# Identity API blueprints

Tài liệu bổ sung theo source local ngày 18/10/2026. Nhánh feature/content-builder-chapter-permissions dựa trên main cddd096 và đã có commit V8 150adc0. Phần local mới bổ sung profile DTO riêng, selectedLicenseClass và V9/V10.

| Nhóm | Endpoint / nội dung | Tài liệu |
| --- | --- | --- |
| Profile | GET /api/v1/users/me, GET /api/v1/users/me/roles | [users.md](users.md) |
| Profile | PUT /api/v1/users/me | [update_my_profile.md](update_my_profile.md) |
| GPLX | PUT /api/v1/users/me/license | [select_license_class.md](select_license_class.md) |
| Auth user context | data.user trong login/register/refresh | [auth_user_context.md](auth_user_context.md) |
| Permission dùng chung | Chapter V8, exams:update V10 | [permission_integration.md](permission_integration.md) |
| Quy ước | Wrapper, lỗi, JWT, dữ liệu cá nhân | [contract_notes.md](contract_notes.md) |
| Luồng auth gốc | Register/login/OTP/password/token | [auth.md](auth.md) |
| Quản trị gốc | User/role/permission | [admin.md](admin.md) |

Các trang mới và users.md mô tả phần thay đổi hiện tại. auth.md/admin.md được giữ làm tài liệu lịch sử các API đã có; xem contract_notes.md và source khi ví dụ cũ khác lỗi/field hiện tại. Có thể tra schema động tại /api-docs của service đúng phiên bản. Các ví dụ dưới đây dùng dữ liệu minh họa, không có token, credential hoặc ID người dùng thật.

[Cách chạy local](../LOCAL_SETUP.md), [nghiệp vụ](../IDENTITY_FEATURES.md), [migration](../db/MIGRATION_CATALOG.md).
