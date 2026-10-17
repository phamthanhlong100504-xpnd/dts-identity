# Chức năng và nghiệp vụ DTS Identity

## 1. Trách nhiệm và cấu trúc

Identity sở hữu tài khoản, hồ sơ, role/permission, refresh session, mã xác thực và sự kiện tài khoản. Practice sở hữu việc học/luyện thi; Examination sở hữu thi chính thức/công bố kết quả; Result sở hữu lịch sử, bảng xếp hạng và thống kê. Identity cung cấp danh tính và quyền, không tính điểm hoặc EXP.

| Thư mục | Nội dung |
| --- | --- |
| controller | AuthController, UserController, AdminController và HomeController |
| dto/request, dto/response | Contract vào/ra, Bean Validation; User entity không trả trực tiếp |
| service | AuthService, UserService, EmailService, OutboxPublisher |
| security, config | JWT, SecurityFilterChain, Redis, Kafka, scheduler, rate limit |
| entity, repository | Mô hình JPA và truy vấn dữ liệu Identity |
| resources/db/migration | SQL Flyway V1–V10 |

## 2. Đăng ký và xác minh email

POST /api/v1/auth/register nhận thông tin tài khoản, kiểm tra trùng username/email/phone, BCrypt mật khẩu, tạo user PENDING và gán ROLE_STUDENT. Ghi USER_CREATED vào user-events qua outbox; sinh OTP REGISTER và gửi email bất đồng bộ. Response 201 đã có cặp token và user context, vì vậy không mô tả PENDING như một bảo đảm rằng mọi API đều bị chặn.

OTP được lưu dạng SHA-256 hash, có hạn dùng và số lần thử. /auth/verify nhận identifier, code, type; REGISTER thành công ghi emailVerifiedAt và trạng thái ACTIVE. /auth/resend-verification sinh mã mới, đánh dấu mã cũ đã dùng. Mã OTP không nằm trong payload Kafka; EmailService gửi trực tiếp qua SMTP.

## 3. Đăng nhập và vòng đời token

/auth/login dùng field username nhưng cho phép tra username hoặc email qua repository. Xác minh mật khẩu bằng BCrypt; kiểm tra khóa tạm và BANNED. Service có nhánh xử lý số lần sai và mốc khóa theo security.brute-force; giới hạn HTTP ở RateLimitFilter độc lập với trạng thái tài khoản.

Access JWT có sub=userId, username, roles, permissions, issuer, iat, exp và jti. Refresh JWT riêng có type=refresh. Database lưu refresh token hash cùng hạn dùng/deviceInfo. /auth/refresh kiểm tra chữ ký và bản ghi còn hiệu lực, thu hồi refresh token cũ, cấp cặp mới và đọc lại user context từ dữ liệu hiện tại.

/auth/logout thu hồi mọi refresh session của chủ thể JWT. Đổi mật khẩu và admin reset cũng thu hồi refresh session. Source không triển khai access-token blacklist trong các luồng này; không hứa access JWT đã cấp mất hiệu lực tức thì trước exp.

## 4. Đổi hoặc quên mật khẩu

- /auth/change-password yêu cầu Bearer, oldPassword và newPassword; kiểm tra mật khẩu cũ rồi lưu BCrypt mới, thu hồi refresh session.
- /auth/forgot-password nhận identifier; trả cùng thông báo kể cả không tìm thấy tài khoản để tránh suy ra account tồn tại. Nếu có user, sinh OTP RESET_PASSWORD, gửi mail và ghi auth-events.
- /auth/reset-password nhận identifier, code, newPassword; dùng trực tiếp OTP RESET_PASSWORD còn hiệu lực, đánh dấu mã đã dùng, cập nhật BCrypt, xóa failedLoginAttempts/lockedUntil và thu hồi refresh session.

Không gọi /verify với type=RESET_PASSWORD trước /reset-password: verify đánh dấu mã đã dùng. Đổi profile/email không tự gửi OTP CHANGE_EMAIL; nếu cần xác minh địa chỉ mới, dùng luồng resend-verification/verify có sẵn. Việc gửi mail là bất đồng bộ và không biến một lỗi SMTP thành lỗi HTTP của request ban đầu.

## 5. Hồ sơ cá nhân

GET /users/me lấy userId từ Authentication, đọc user chưa soft-delete và cache users theo UUID 30 phút. PUT /users/me dùng UpdateMyProfileRequest riêng gồm username, email, fullName, birthOfDate, phoneNumber. Field null hoặc không gửi giữ giá trị hiện tại; chuỗi được trim trong service.

Khi email mới khác email cũ theo so sánh không phân biệt hoa/thường, kiểm tra trùng không phân biệt hoa/thường và bỏ emailVerifiedAt của email cũ. Các field ngoài contract như status, role hoặc selectedLicenseClass không được dùng để tự quản trị tài khoản. Service tái dùng updateUser, ghi USER_UPDATED vào outbox và evict cache profile.

Chi tiết validation, response và giới hạn kiểm tra trùng xem [update_my_profile.md](api-blueprint/update_my_profile.md). Không suy từ unique index thành một thông báo HTTP 409 đã có cho mọi field.

## 6. Chọn hạng GPLX

PUT /users/me/license nhận licenseClass, đối chiếu enum LicenseClass và lưu users.selected_license_class. Người cũ/mới chưa chọn có giá trị null; V9 không tự gán A1 hay đổi dữ liệu hồ sơ cũ. Danh sách: A1, A, B1, B, C1, C, D1, D2, D, BE, C1E, CE, D1E, D2E, DE.

Response của endpoint chọn hạng là data.licenseClass. Profile và auth user context dùng selectedLicenseClass. Cập nhật chọn hạng evict cache, không cấp cặp token mới, không phát USER_UPDATED trong nhánh này và không đổi lịch sử bài học/bài thi. Client cập nhật state hạng đang chọn từ server; cấu hình số câu/thời gian thi do repo sở hữu bài thi cung cấp.

## 7. Quản trị user, role và permission

Toàn bộ /api/v1/admin/** yêu cầu ROLE_ADMIN. Admin có thể tìm user/phân trang/lọc trạng thái/role, đọc/cập nhật/tạo user, soft-delete, đổi status, reset password, xem/gán/thu hồi role và quản lý permission.

Tạo user bằng admin đặt ACTIVE và emailVerifiedAt ngay, có thể gán roleIds; không chạy luồng đăng ký xác minh email. Soft-delete user giữ bản ghi và thu hồi refresh session. Chuyển BANNED/LOCKED thu hồi refresh session; chuyển ACTIVE xóa trạng thái khóa tạm. Reset password admin xóa số lần sai và lockedUntil theo source, không tự khẳng định đổi mọi status về ACTIVE.

Tên role tạo mới chuẩn hóa uppercase và prefix ROLE_. Xóa role là soft-delete và giữ liên kết lịch sử. Xóa permission là soft-delete, đồng thời bỏ các liên kết role_permissions của permission đó. Permission name như chapters:read hoặc exams:update nằm trong claim permissions; JwtAuthenticationFilter tạo authority PERM_<permission>, role giữ prefix ROLE_.

V8 thêm chapters:read/create/update cho admin; V10 bổ sung exams:update để các API Examination cần cập nhật/công bố/phát hành kết quả có permission tương ứng. Quy tắc kiểm tra permission của từng service nằm ở repo đó; Identity không sở hữu endpoint công bố kết quả.

## 8. Sự kiện và dữ liệu

OutboxPublisher dùng Propagation.MANDATORY, ghi outbox_events trong transaction nghiệp vụ hiện tại. Payload thêm eventType và timestamp milliseconds. Scheduler đọc PENDING theo createdAt, gửi Kafka với key=eventType, chờ kết quả gửi rồi mark PROCESSED. Lỗi tăng retryCount và chuyển FAILED khi đạt maxRetries; có dọn event PROCESSED cũ hơn 7 ngày mỗi giờ. Không coi outbox là bảo đảm exactly-once ở mọi consumer; repo tiêu thụ quản lý idempotency của mình.

user-events mang USER_CREATED, USER_UPDATED, USER_DELETED; auth-events mang các thông báo vòng đời xác minh/khôi phục. Payload có thể có userId, username, email, fullName tùy sự kiện. Không thêm selectedLicenseClass vào payload/JWT trong đợt này và không đưa OTP/password vào event.

## 9. Giới hạn và dữ liệu môi trường

Profile cache dùng Redis; rate limiter dùng bộ nhớ tiến trình, không dùng Redis. Có bucket riêng auth và api theo địa chỉ IP, mức cấu hình trong code lần lượt 10 và 30 request/phút. Khi qua Gateway, nhiều request có thể chia sẻ cách nhận diện IP tùy forwarded header; client xử lý 429 thay vì tự gọi liên tục.

Code trong Git không bao gồm tài khoản, volume, token hoặc credential thật của máy người phát triển. Mọi thay đổi schema tiếp theo tạo migration mới theo thứ tự, giữ nguyên SQL đã ghi checksum. Tài liệu này mô tả code đang bàn giao; không tuyên bố đã thực hiện lại thao tác tài khoản hay kiểm thử runtime trong quá trình dọn Git.
