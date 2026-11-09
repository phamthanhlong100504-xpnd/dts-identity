# Quyền thi thử mô phỏng — Card 05

## Quyền và vai trò

V14 bổ sung permission thuần `simulations:exam`, resource `simulations`. ROLE_ADMIN, ROLE_TEACHER và ROLE_STUDENT đang hoạt động nhận quyền thực hiện và xem kết quả bài thi thử mô phỏng của chính mình. ROLE_GUEST, role tự tạo và role đã soft-delete không được tự cấp.

| Quyền | Phạm vi |
| --- | --- |
| `simulations:read` | Đọc nội dung/khả năng mô phỏng được phép |
| `simulations:learn` | Lượt luyện cá nhân, bookmark và tiến độ Card 01–03 |
| `simulations:exam` | Phiên thi thử mô phỏng và kết quả/review của chính học viên |
| `practice:exam` | Thi thử lý thuyết; không thay quyền thi mô phỏng |
| `media:read` | Đọc media trong phạm vi do consumer kiểm tra |

Backend Practice/Media của Card 05 phải kiểm quyền tương ứng, JWT subject, ownership và trạng thái phiên. `simulations:exam` không cho chọn phiên của người khác, đọc scoring khi đang thi, bỏ qua thứ tự video hoặc tự ghi điểm. Identity chỉ cấp context; thêm permission chưa tạo API thi thử.

JWT dùng `permissions: ["simulations:exam", ...]`. Không thêm tiền tố `PERM_` vào database hoặc claim; tiền tố thuộc authority tại consumer. Không tự suy quyền thi từ quyền luyện, quyền thi lý thuyết hoặc tên role.

## Migration và triển khai

[V14](../../src/main/resources/db/migration/V14__add_simulation_exam_permission.sql) chỉ INSERT danh mục/grant. Permission active có sẵn được reuse, giữ ID/metadata và grant thủ công; permission soft-delete được giữ lịch sử và tạo row active mới. SQL V1–V13, người dùng, refresh token và lịch sử cũ được bảo toàn. Seed V13 chuẩn thêm một permission và ba mapping; dữ liệu tùy biến có thể thêm ít hơn.

Chạy candidate Check trước Apply/Verify. Sau khi source được xác nhận, khởi động lại Identity bằng cấu hình Flyway đang dùng với V13 để chạy V14. Nếu môi trường disable Flyway hoặc pin target thì cần kiểm tra riêng; không dùng runner lịch sử V10→V11 và không sửa migration/checksum đã chạy. Đăng nhập lại hoặc refresh để JWT mới nhận context mới; token access đã cấp vẫn giữ snapshot đến khi hết hạn.

## Kiểm thử

`ExamPermissionMigrationPostgresTest` kiểm chuỗi mới/nâng V13→V14, grant đúng role, SQL lặp, dữ liệu/grant hiện hữu, soft-delete và thu hồi quyền. `ExamPermissionJwtPostgresTest` gọi AuthService/JwtProvider/UserService thật trên PostgreSQL tạm để kiểm login, nhiều role, guest/custom, refresh/rotation và tính độc lập với quyền luyện/lý thuyết. Kiểm thêm toàn bộ regression Identity trước đó.

Runtime nhập Admin và learner khác nhau, mật khẩu ẩn. JWT, login context, `/users/me`, `/users/me/roles` và `/auth/validate` phải khớp. Không chỉnh role, không logout các phiên khác, không ghi token/password ra audit. Kết quả này chỉ nghiệm thu prerequisite Identity, không phải Card 05 hoàn chỉnh.
