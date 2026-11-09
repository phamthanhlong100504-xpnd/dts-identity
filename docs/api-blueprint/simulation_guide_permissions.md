# Card 04 — Quyền quản trị hướng dẫn mô phỏng

## Part 0 — Classification & Identity

Tiếp tục nhánh `feat/simulation-permissions` trên nền V11/V12 đã có, bổ sung V13. Chỉ thêm catalog permission và grant cho ROLE_ADMIN đang hoạt động. Không sửa đăng nhập, refresh, service RBAC, user/profile hoặc migration V1–V12.

## Part 1 — Permission Contract

| Permission | Mục đích tại Content Builder | Grant tự động |
| --- | --- | --- |
| simulation-guides:read | Đọc nháp, xem trước và lịch sử | ROLE_ADMIN |
| simulation-guides:update | Tạo/cập nhật nháp theo expectedRevision | ROLE_ADMIN |
| simulation-guides:publish | Công bố nháp đã kiểm tra | ROLE_ADMIN |
| simulations:read (đã có) | Học viên đọc hướng dẫn đã công bố | Giữ grant V12 |

Content Builder phải kiểm **ROLE_ADMIN và permission tương ứng** cho API quản trị. Một quyền được gán thủ công cho custom role không tự biến tài khoản đó thành Admin. Quyền hướng dẫn không cho sửa bank/scoring timeline, media, progress hoặc kết quả Practice.

## Part 2 — Data Interaction

V13 thêm ba active permission nếu chưa có, giữ ID/metadata/grant thủ công đã tồn tại và lịch sử soft-delete. Chỉ ROLE_ADMIN chưa soft-delete nhận grant mới. Student/Teacher/Guest/custom role không được cấp tự động. Flyway chạy một lần; chạy validate/migrate lại không khôi phục grant đã được quản trị viên thu hồi.

JWT và `/users/me/roles` tiếp tục đọc quyền qua service hiện có. Claim dùng tên permission gốc, không thêm PERM_. Token đã cấp giữ snapshot cũ đến khi hết hạn; đăng nhập hoặc refresh mới để nhận quyền hiện tại. Truy cập Admin còn phụ thuộc role và kiểm tra backend Content Builder.

## Part 3 — Migration & Local Setup

Migration: `V13__add_simulation_guide_permissions.sql`. Apply kit chỉ cài source, không chạy SQL. Sau Check/Apply/Verify, khởi động lại Identity bằng cấu hình đã dùng cho V12, xác nhận Flyway áp dụng V13; không sửa history/checksum, baseline/repair/clean hoặc chạy lại migration cũ. Nếu cấu hình đang pin target/disable Flyway, gửi trạng thái trước khi thực hiện upgrade riêng.

Tool `IdentityPermissionMigrationTool` cũ vẫn là tool lịch sử V10→V11; không dùng nó để nâng V13 hoặc sửa target của nó. Các bài regression V11/V12 tiếp tục pin phiên bản lịch sử riêng.

## Part 4 — Acceptance & Handoff

Check chạy hai lớp PostgreSQL mới cùng toàn bộ mười lớp hồi quy Identity đã có. Kiểm tra upgrade V12→V13, cài mới, chạy lại, metadata/grant/soft-delete, role Admin không hoạt động, thu hồi quyền và JWT/login/refresh thật trên DB riêng. Không gửi email hoặc sự kiện ngoài.

Runtime sau restart dùng hai tài khoản khác nhau: Admin phải có đủ ba quyền mới, học viên phải giữ simulations:read và không có quyền quản trị hướng dẫn từ grant mặc định. Kiểm JWT khớp context Identity; mật khẩu ẩn và token không ghi log. API nháp/xem trước/công bố và nghiệm thu SIM-06/Admin được thực hiện ở bước Content Builder/frontend tiếp theo, không suy ra đã hoàn thành Card 04 chỉ từ quyền Identity.
