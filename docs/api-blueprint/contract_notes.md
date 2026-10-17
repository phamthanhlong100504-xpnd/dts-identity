# Identity — quy ước contract hiện tại

## Chủ thể và đường dẫn

API gốc là /api/v1. Địa chỉ local trực tiếp :8081; qua Gateway :8888 nếu cấu hình route Identity tương ứng. API /users/me, /users/me/license, /users/me/roles, /auth/logout và /auth/change-password yêu cầu Authorization: Bearer <access-token>. Chủ thể lấy từ Authentication principal UUID sau JwtAuthenticationFilter; không nhận userId tùy chọn từ client để thao tác cho người khác.

/api/v1/admin/** yêu cầu ROLE_ADMIN. Các API auth còn lại được permitAll trong SecurityConfig; riêng /auth/validate có thể trả HTTP 200 với data.valid=false khi thiếu/sai token. Không dùng đặc tính của /validate để suy ra /users/me cũng công khai.

## Wrapper và lỗi

Controller trả ApiResponse với success, message, data, timestamp và errorCode/traceId khi có. ApiResponse khai báo JsonInclude.NON_NULL nên field null của chính wrapper có thể bị lược; security handler lại ghi Map có data:null. Client không phụ thuộc việc mọi lỗi đều có đủ data/errorCode/traceId hoặc đều trả null giống nhau.

| Nguồn | HTTP | errorCode / cách đọc |
| --- | --- | --- |
| Bean Validation DTO | 400 | VAL-400, message ghép field và lỗi |
| JSON sai kiểu/cú pháp | 400 | REQ-400 |
| BusinessException factory | Theo status | BIZ-400, BIZ-409, BIZ-401... |
| BusinessException(message,status) | Theo status | Code vẫn BIZ-400, kể cả một số 404 |
| Chưa xác thực tại SecurityConfig | 401 | Map có message/data/timestamp, không bắt buộc errorCode |
| Security từ chối quyền | 403 | Security handler hoặc AUTH-403 tùy nơi ném lỗi |
| RateLimitFilter | 429 | RATE-429 |
| Lỗi ngoài các mapping trên | 500 | SYS-500 từ GlobalExceptionHandler |

Không coi mọi trùng username/email/phone đều trả 409. Email đổi qua updateMyProfile có kiểm tra không phân biệt hoa/thường trả BIZ-409; updateUser dùng đường cũ có lỗi 400. Kiểm tra trùng phone của cập nhật profile chưa có nhánh service riêng; DB unique index vẫn có thể từ chối. Tài liệu không bổ sung mapping exception mới vào source.

## DTO người dùng và GPLX

UserResponse trả selectedLicenseClass; AuthResponse.UserInfo cũng có selectedLicenseClass. Riêng UserLicenseResponse trả licenseClass. Hai tên này cùng mô tả hạng GPLX nhưng không cùng vị trí JSON. Giá trị nullable khi chưa chọn; frontend không tự coi null là A1.

Entity User không được trả trực tiếp; không có password/hash/failedLoginAttempts trong UserResponse. selectedLicenseClass chưa được đưa vào access JWT, TokenValidationResponse hay payload user-events. Client đọc server response và chỉ dùng các field đúng contract.

## Token và quyền

Access JWT chứa sub, username, roles, permissions; roles có tên ROLE_ADMIN/ROLE_STUDENT, authority permission phía Identity có prefix PERM_. Token cấp trước thay đổi role/permission giữ claim cũ tới khi cấp lại. /refresh quay vòng refresh token; không tái dùng refresh token cũ sau thành công.

Logout/đổi/reset password thu hồi refresh session, không có access blacklist tức thì. Profile update không cấp token mới; nếu đổi username, claim username trong token cũ chưa tự được viết lại. userId là định danh ổn định cho liên kết dữ liệu.

## Phạm vi thay đổi

Đợt chuẩn bị Git không đổi Java, YAML, pom.xml, SQL, Redis, Kafka hay tài khoản. Source 16 file chức năng local được commit đúng nội dung đang có; .gitattributes chỉ giữ byte hai SQL mới chưa được track. Các ví dụ không phải dữ liệu seed hoặc lệnh tạo fixture.

Nguồn: [UserController](../../src/main/java/com/dts/identity/controller/UserController.java), [UserService](../../src/main/java/com/dts/identity/service/UserService.java), [SecurityConfig](../../src/main/java/com/dts/identity/config/SecurityConfig.java), [ApiResponse](../../src/main/java/com/dts/identity/dto/response/ApiResponse.java).
