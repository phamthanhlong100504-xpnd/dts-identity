# Identity — quyền tích hợp với Content Builder và Examination

## Migration và quyền

| Version | Permission | Cấp cho | Ý nghĩa ở service tiêu thụ |
| --- | --- | --- | --- |
| V8 | chapters:read | ROLE_ADMIN | Đọc chapter Content Builder |
| V8 | chapters:create | ROLE_ADMIN | Tạo chapter Content Builder |
| V8 | chapters:update | ROLE_ADMIN | Sửa chapter Content Builder |
| V10 | exams:update | ROLE_ADMIN chưa soft-delete | Cấu hình/công bố kỳ thi và các thao tác Examination yêu cầu quyền update |

V8 đã có trong nhánh trước đợt commit local này; V10 là file local chưa commit lúc thu thập snapshot. Cả hai ghi danh mục permissions và liên kết role_permissions, không thêm route Identity mới. Endpoint cụ thể, quyền đọc/sở hữu và thời điểm công bố kết quả do repo Content Builder/Examination quy định; exams:update không tự thay thế mọi quy tắc quyền của service đó.

## Quy trình triển khai

1. Đưa file migration đúng phiên bản vào source release Identity.
2. Vận hành chạy Flyway trên đúng database theo LOCAL_SETUP; không nhập permission thủ công thay migration và không sửa V8/V10 đã ghi checksum.
3. Đăng nhập lại hoặc refresh tài khoản admin để nhận JWT mới. Token cũ không tự cập nhật permissions.
4. Gateway và service tiêu thụ dùng cấu hình xác thực tương ứng. JWT giữ roles=[ROLE_ADMIN,...] và permissions=[chapters:read,...,exams:update,...].

Nếu permission đã tồn tại, INSERT dùng ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING. Mapping dùng ON CONFLICT (role_id,permission_id) DO NOTHING. Các câu INSERT tránh trùng, nhưng Flyway vẫn là cơ chế theo dõi version/checksum, không phải lý do chạy SQL thủ công lặp lại.

## API đọc ngữ cảnh

- GET /api/v1/users/me/roles: role và danh sách permission của người hiện tại.
- GET /api/v1/admin/roles và /permissions: danh mục cho ADMIN.
- POST /api/v1/auth/login hoặc /refresh: phát token với context hiện tại.

Nhận HTTP200 từ API danh mục không chứng minh token cũ có quyền mới; client sử dụng token được cấp lại. Đợt chuẩn bị Git không gọi những API này, không gán role cho account thật và không thay đổi dữ liệu người dùng.

Nguồn: [V8](../../src/main/resources/db/migration/V8__add_chapter_permissions.sql), [V10](../../src/main/resources/db/migration/V10__add_official_exam_update_permission.sql), [AuthService](../../src/main/java/com/dts/identity/service/AuthService.java), [AdminController](../../src/main/java/com/dts/identity/controller/AdminController.java).
