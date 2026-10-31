# Identity — quyền tích hợp với các service nghiệp vụ

## Migration và quyền

| Version | Permission | Cấp cho | Ý nghĩa ở service tiêu thụ |
| --- | --- | --- | --- |
| V8 | chapters:read/create/update | ROLE_ADMIN | Quyền chapter Content Builder đã có |
| V10 | exams:update | ROLE_ADMIN chưa soft-delete | Cấu hình/công bố kỳ thi theo contract Examination |
| V11 | progress:read/update | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN chưa soft-delete | Đọc/cập nhật dữ liệu Progress đúng chủ thể |
| V11 | practice:read/learn/exam | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN chưa soft-delete | Đọc, học/ôn và phiên thi thử cá nhân |
| V11 | practice:import | ROLE_ADMIN chưa soft-delete | Import ngân hàng; Practice vẫn bắt buộc cả role ADMIN |

Các tên nhóm có dấu / trong bảng là cách viết gọn; tên permission thực tế đầy đủ trong [contract V11](progress_practice_permissions.md). V8/V10 giữ nguyên. V11 chỉ thêm danh mục và role_permissions; không thêm endpoint Identity, không đổi role của tài khoản và không cập nhật token đang tồn tại.

## Quy trình triển khai

1. Đưa V11 vào source trên nhánh fix/identity-progress-practice-permissions từ base e49bba0; giữ nguyên V1–V10.
2. Candidate chạy toàn bộ migration và JWT test trên PostgreSQL riêng trước khi áp dụng file vào repo local.
3. Sau apply/verify, đối chiếu database mục tiêu và Flyway history rồi chạy migration bằng công cụ Flyway, giữ validate-on-migrate, không sửa checksum/repair/baseline để lách lỗi. Xem [LOCAL_SETUP](../LOCAL_SETUP.md).
4. Đăng nhập lại hoặc refresh để nhận access JWT mới. AuthService lấy permissions từ database khi phát token; token cũ giữ snapshot cũ tới khi hết hạn/được thay thế.
5. Dùng JWT mới kiểm tra trực tiếp Progress/Practice và qua Gateway theo cấu hình môi trường. Không tự cấp bypass khi thiếu quyền.

JWT giữ roles=[ROLE_STUDENT,...] và permissions=[progress:read,...] với tên thuần. Consumer chuyển permission sang authority PERM_<name>. Không thêm PERM_ vào dữ liệu permissions của Identity. Quyền mới không thay thế chapters:* hoặc exams:* cũ.

V11 không tự cấp quyền cho ROLE_GUEST/vai trò tự tạo, không cấp practice:import cho STUDENT/TEACHER. Các liên kết đã tồn tại do quản trị cấp trước được giữ nguyên; migration không phải tác vụ thu hồi quyền hoặc khôi phục role/permission đã soft-delete.

## API đọc ngữ cảnh

- GET /api/v1/users/me/roles: role và danh sách permission hiện tại theo database.
- GET /api/v1/admin/roles và /permissions: danh mục quản trị theo contract hiện có.
- POST /api/v1/auth/login hoặc /refresh: cấp token với context hiện tại.

HTTP200 từ API danh mục không chứng minh token cũ đã nhận quyền. Với dữ liệu học cá nhân, userId vẫn lấy từ JWT sub; quyền admin cũng không mở quyền chọn phiên của người khác. Progress chuyển tiếp Bearer sang Practice cho learning-catalog/exam progress nên token liên thông cần practice:read.

Nguồn: [V11](../../src/main/resources/db/migration/V11__add_progress_practice_permissions.sql), [AuthService](../../src/main/java/com/dts/identity/service/AuthService.java), [JwtProvider](../../src/main/java/com/dts/identity/security/JwtProvider.java), [UserService](../../src/main/java/com/dts/identity/service/UserService.java).
