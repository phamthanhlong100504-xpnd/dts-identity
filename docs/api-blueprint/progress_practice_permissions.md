# Cấp quyền Progress và Practice

## Part 0 — Phạm vi và nguồn

Identity cung cấp danh mục permission, liên kết vai trò và claims JWT cho hai bản sửa backend đã kiểm thử: Progress fix/progress-api-authorization (base f775eaa) và Practice fix/practice-api-authorization (base 6373cc8). Source Identity dựa trên feature/content-builder-chapter-permissions, commit e49bba0a66b693d5ee1698457fed27f03019f04a.

Bản sửa này không thêm route/request/response Identity; dùng các API login, refresh và ngữ cảnh role hiện có. Tài liệu theo Part 0–4; không có DOCX.

## Part 1 — Contract quyền

| Permission trong JWT | Resource | Vai trò được cấp tự động nếu còn hoạt động |
| --- | --- | --- |
| progress:read | progress | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN |
| progress:update | progress | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN |
| practice:read | practice | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN |
| practice:learn | practice | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN |
| practice:exam | practice | ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN |
| practice:import | practice | ROLE_ADMIN |

Các permission độc lập và có tên thuần resource:action. Không dùng exams:create thay practice:exam vì tạo lượt thi thử cá nhân khác quản trị đề/kỳ thi. ROLE_ADMIN cũng cần permission cụ thể ở consumer. Import yêu cầu cả ROLE_ADMIN và practice:import.

Progress dùng read cho 6 GET nghiệp vụ và update cho 3 thao tác ghi người dùng. Hai ingress nội bộ của Progress vẫn dùng shared key riêng. Practice dùng read cho 27 GET, learn cho 15 thao tác học/ôn, exam cho 9 thao tác phiên EXAM/PRACTICE, import cho 3 API quản trị; home giữ public. Ownership và validation thuộc source service tiêu thụ, không đổi bởi migration này.

## Part 2 — Luồng xử lý

1. Flyway xác minh các migration cũ và áp dụng V11 vào đúng database Identity.
2. Migration tạo tối đa 6 permission đang hoạt động; reuse ID/metadata nếu permission cùng tên đã hoạt động.
3. Gán 5 quyền học cá nhân cho 3 role hệ thống đang hoạt động, practice:import cho ADMIN đang hoạt động. Không tự gán GUEST/custom role.
4. Login/refresh đi qua AuthService.buildAuthResponse, đọc user_roles/role_permissions và phát JWT bằng JwtProvider. Giữ username, sub UUID, issuer/chữ ký, roles có ROLE_, permission tên thuần và response user như trước.
5. Client dùng access JWT mới; GET users/me/roles có thể đối chiếu context database. Hạng GPLX vẫn lấy selectedLicenseClass từ API user/auth response, không thêm claim hạng.
6. Consumer kiểm PERM_progress:* hoặc PERM_practice:* và tiếp tục kiểm đúng chủ dữ liệu. Không tin X-User-* do client gửi để chọn tài khoản khác.

## Part 3 — Tương tác dữ liệu

File: [V11__add_progress_practice_permissions.sql](../../src/main/resources/db/migration/V11__add_progress_practice_permissions.sql).

- Chỉ INSERT vào permissions và role_permissions, với ON CONFLICT đúng partial unique index và khóa ghép hiện có. Không UPDATE/DELETE dữ liệu đang có.
- Tạo mới permissions khi chỉ có bản soft-delete cùng tên; không khôi phục hoặc thay đổi bản lịch sử đó. Không gán mới vào role soft-delete.
- Giữ liên kết và metadata đã có, kể cả quyền được quản trị cấp thủ công trước migration. Không suy ra migration có chức năng thu hồi mọi grant ngoài ma trận.
- Trên database chuẩn chỉ có V1–V10, thêm 6 permission và 16 mapping. Với dữ liệu từng được bổ sung trước, số row mới có thể ít hơn; kiểm nội dung/liên kết thay vì ép số insert.
- users, user_roles, mật khẩu, hồ sơ/hạng, refresh_tokens, verification_codes và outbox_events không được migration sửa.
- V1–V10 giữ nguyên. Quy tắc LF mới trong .gitattributes chỉ áp dụng V11; không renormalize migration cũ.

## Part 4 — Triển khai và nghiệm thu

Candidate gồm test nâng cấp V10→V11, database trống, migration lặp, permission đã có/soft-delete, role inactive/guest/custom, giữ dữ liệu/lịch sử và test AuthService/JWT thật qua PostgreSQL với repository JPA. Kèm hồi quy 5 lớp unit đang có. Không dùng H2 thay cho kiểm chứng migration PostgreSQL.

File phải áp dụng vào source trước khi chạy migration máy local. Vận hành chỉ chạy Flyway trên database đã xác định và giữ Hibernate ddl-auto=validate khi khởi động app, vì cấu hình nguồn mặc định còn update. Công cụ migration tách riêng không khởi chạy Spring/worker, không dùng repair/clean/baseline.

Sau migration: login/refresh lấy token mới, kiểm claims và roles API, kiểm 401/403/đủ quyền qua Progress/Practice/Gateway. UI chỉ được ghi nhận đạt khi thực sự chạy. Kết quả candidate/apply/verify không tự chứng minh database nghiệp vụ hoặc runtime đã được nâng cấp.

Xem [permission integration](permission_integration.md), [migration catalog](../db/MIGRATION_CATALOG.md), [local setup](../LOCAL_SETUP.md).
