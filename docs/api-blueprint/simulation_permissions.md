# Quyền luyện mô phỏng và tích hợp Media/Content Builder

## Part 0 — Phạm vi và nguồn

Phần Identity của GAP Card 01, trên nhánh feat/simulation-permissions, kế thừa cf0e620 (bao gồm quyền Progress/Practice V11). V12 bổ sung danh mục và grant để các service triển khai phân quyền; không thêm API Identity hoặc thay đổi cấu trúc JWT.

Practice quản lý lượt luyện và dữ liệu cá nhân; Content Builder quản lý nội dung/ngân hàng và cấu hình chấm; Media quản lý tệp và quyền truy cập tệp. Migration này chưa chứng minh các API đó đã triển khai hoặc GAP đã nghiệm thu trên web.

## Part 1 — Contract quyền

| Permission trong JWT | Resource | Vai trò được cấp tự động nếu active |
| --- | --- | --- |
| simulations:read | simulations | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN |
| simulations:learn | simulations | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN |
| media:read | media | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN |
| media:upload | media | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN |
| traffic-signs:read | traffic-signs | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN |
| memory-tips:read | memory-tips | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN |
| media:update | media | ROLE_ADMIN |
| media:delete | media | ROLE_ADMIN |
| media:manage | media | ROLE_ADMIN |
| simulation-scenarios:read | simulation-scenarios | ROLE_ADMIN |
| simulation-scenarios:create | simulation-scenarios | ROLE_ADMIN |
| simulation-scenarios:publish | simulation-scenarios | ROLE_ADMIN |
| traffic-signs:create | traffic-signs | ROLE_ADMIN |
| memory-tips:create | memory-tips | ROLE_ADMIN |
| learning-programs:read | learning-programs | ROLE_ADMIN |
| learning-programs:create | learning-programs | ROLE_ADMIN |
| learning-programs:update | learning-programs | ROLE_ADMIN |
| learning-programs:delete | learning-programs | ROLE_ADMIN |
| chapters:delete | chapters | ROLE_ADMIN |

Tên trong bảng là tên thực tế, không có tiền tố PERM_. Quyền Progress/Practice và chapters/exams đã có vẫn giữ nguyên. GUEST/custom role không được tự cấp quyền mới. Grant thủ công đã có được bảo toàn, kể cả grant ngoài ma trận mặc định.

## Part 2 — Luồng xử lý và trách nhiệm consumer

1. Flyway áp dụng V12 sau khi file đã nằm trong source; login/refresh đọc quyền hiện tại qua AuthService và phát JWT ký như trước. Token cũ giữ snapshot cũ.
2. Consumer xác minh JWT và chuyển tên permission thành authority PERM_<name>. Chủ lượt luyện lấy từ sub; không dùng userId từ request để truy cập lượt luyện của người khác.
3. simulations:read dùng cho nội dung đã công bố và trạng thái cá nhân; simulations:learn dùng cho thao tác lượt luyện/bookmark/cursor của chính người dùng. Không thay bằng practice:learn để vô tình mở toàn bộ module.
4. simulation-scenarios:* dành cho quản trị nội dung gốc, import/công bố và cấu hình chấm. API quản trị cần cả ROLE_ADMIN và permission tương ứng. Học viên nhận kết quả/cấu hình giải thích theo trạng thái lượt luyện hợp lệ, không nhận cấu hình chấm trước lượt luyện qua API quản trị.
5. media:read không cho phép đọc mọi UUID PRIVATE. Media phải kiểm chủ sở hữu hoặc liên kết với nội dung đã công bố/phạm vi phiên được phép. media:upload của học viên chỉ dùng cho target cá nhân được phép; target mô phỏng/nội dung dùng chung cần quyền quản trị. Kiểm cả bước presign, complete và phát URL; không tin query userId hoặc payload JWT chưa xác minh.
6. traffic-signs/memory-tips:read chỉ mở nội dung đã công bố; create và learning-programs:*/chapters:delete thuộc quản trị. Từng API phải ghi contract cụ thể trong repo tiêu thụ khi triển khai.

## Part 3 — Tương tác dữ liệu

[V12](../../src/main/resources/db/migration/V12__add_simulation_and_media_permissions.sql) chỉ INSERT permissions và role_permissions, bỏ qua xung đột trên permission active và khóa ghép. Giữ ID/metadata active, bản soft-delete, role inactive, dữ liệu tài khoản, token, OTP và outbox. Không sửa V1–V11.

Trên seed chuẩn V11: thêm 19 permission, 31 mapping (6 quyền cho 3 role và 13 quyền riêng ADMIN). Nếu đã có quyền/grant tương ứng, số insert thấp hơn. Chạy Flyway lại sau V12 không chạy lại SQL hoặc khôi phục grant đã thu hồi. Không chạy SQL seed trực tiếp để đồng bộ lại quyền sau vận hành.

## Part 4 — Triển khai và kiểm thử

Candidate chạy nâng V11→V12, database trống, idempotency, bảo toàn dữ liệu/metadata, soft-delete, role inactive/guest/custom, thu hồi grant, cùng AuthService/JPA/JWT thật trên PostgreSQL riêng. Test V11 vẫn kiểm lịch sử V11; không sửa kỳ vọng cũ thành kỳ vọng V12 để bỏ qua hồi quy.

Sau candidate đạt: apply/verify source, chạy Flyway V12 bằng cấu hình Identity hiện có với Hibernate validate, rồi login/refresh. Công cụ migration V11 trong gói cũ chỉ hỗ trợ V11; không dùng công cụ đó để chạy V12. Đối chiếu history và quyền qua API ngữ cảnh. API 401/403, ownership và giao diện chỉ nghiệm thu khi các repo tiêu thụ đã áp dụng và chạy thật.

Xem [quyền tích hợp](permission_integration.md), [migration catalog](../db/MIGRATION_CATALOG.md), [local setup](../LOCAL_SETUP.md).
