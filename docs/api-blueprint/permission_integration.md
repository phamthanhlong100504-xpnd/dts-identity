# Identity — quyền tích hợp với các service nghiệp vụ

## Migration và quyền

| Version | Permission | Cấp cho | Ý nghĩa ở service tiêu thụ |
| --- | --- | --- | --- |
| V8 | chapters:read/create/update | ROLE_ADMIN | Quyền chapter Content Builder đã có |
| V10 | exams:update | ROLE_ADMIN chưa soft-delete | Cấu hình/công bố kỳ thi theo contract Examination |
| V11 | progress:read/update | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN chưa soft-delete | Đọc/cập nhật dữ liệu Progress đúng chủ thể |
| V11 | practice:read/learn/exam | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN chưa soft-delete | Đọc, học/ôn và phiên thi thử cá nhân |
| V11 | practice:import | ROLE_ADMIN chưa soft-delete | Import ngân hàng; Practice vẫn bắt buộc cả role ADMIN |
| V12 | simulations:read/learn; media:read/upload; traffic-signs:read; memory-tips:read | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN chưa soft-delete | Luyện mô phỏng cá nhân và tài nguyên trong phạm vi consumer cho phép |
| V12 | 13 quyền quản trị Media/Content Builder | ROLE_ADMIN chưa soft-delete | Ma trận tên đầy đủ và giới hạn trong [contract V12](simulation_permissions.md) |

Các tên nhóm có dấu / trong bảng là cách viết gọn; tên permission thực tế đầy đủ trong [contract V11](progress_practice_permissions.md). V8/V10 giữ nguyên. V11 chỉ thêm danh mục và role_permissions; không thêm endpoint Identity, không đổi role của tài khoản và không cập nhật token đang tồn tại.

V12 kế thừa toàn bộ quyền trước đó. Có permission Media không đồng nghĩa được đọc mọi tệp PRIVATE hoặc tải lên mọi target; service tiêu thụ phải kiểm ownership, publication và target theo [contract mô phỏng](simulation_permissions.md).

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


### Card 04 — Hướng dẫn mô phỏng

`simulation-guides:read`, `simulation-guides:update`, `simulation-guides:publish` được V13 cấp cho ROLE_ADMIN đang hoạt động. Content Builder kiểm cả role và quyền tương ứng. Học viên đọc bản công bố bằng `simulations:read` của V12. [Chi tiết](simulation_guide_permissions.md).

### Card 05 — Thi thử mô phỏng

`simulations:exam` được V14 cấp cho ROLE_ADMIN/ROLE_TEACHER/ROLE_STUDENT đang hoạt động. Practice/Media kiểm thêm `simulations:read`, `media:read` khi cần media, ownership và phiên thi; không dùng `practice:exam` hoặc `simulations:learn` thay quyền này. [Chi tiết](simulation_exam_permissions.md).
