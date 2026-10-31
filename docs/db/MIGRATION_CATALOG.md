# DTS Identity — danh mục migration

Thư mục: src/main/resources/db/migration. Flyway dùng flyway_schema_history; mặc định enabled=true, baseline-on-migrate=true, baseline-version=1, out-of-order=false. Thứ tự là số version, V10 sau V9, không theo thứ tự chuỗi tên file.

| Version | File | Nghiệp vụ |
| --- | --- | --- |
| 1 | V1__init_identity_schema.sql | users, roles, user_roles, permissions, role_permissions, refresh_tokens, verification_codes; index/trigger và seed role/permission |
| 2 | V2__allow_dynamic_roles.sql | Bỏ giới hạn CHECK tên role cố định, mở rộng độ dài để tạo role động |
| 3 | V3__outbox_events.sql | Bảng/index outbox_events cho gửi sự kiện Kafka |
| 4 | V4__add_audit_fields_to_master_data.sql | Audit/metadata/soft-delete danh mục role/permission và assigned_at liên kết |
| 5 | V5__extend_username_length.sql | Mở rộng cột users.username thành VARCHAR(50); DTO tự sửa/đăng ký hiện vẫn giới hạn20 |
| 6 | V6__change_device_info_to_text.sql | Chuyển refresh_tokens.device_info thành TEXT |
| 7 | V7__sync_test_user_roles.sql | Gán role cho các username chỉ định nếu đã có; không tạo tài khoản/mật khẩu |
| 8 | V8__add_chapter_permissions.sql | Thêm chapters:read/create/update và mapping cho ROLE_ADMIN |
| 9 | V9__add_selected_license_class.sql | Thêm users.selected_license_class nullable VARCHAR(10), CHECK 15 hạng GPLX |
| 10 | V10__add_official_exam_update_permission.sql | Thêm exams:update, gán cho ROLE_ADMIN chưa soft-delete |
| 11 | V11__add_progress_practice_permissions.sql | Thêm 6 quyền Progress/Practice; 5 quyền học cá nhân cho 3 role hệ thống active, import chỉ ADMIN active |

## V9: hồ sơ và lựa chọn hạng

User đã có giữ các field cũ, selected_license_class=null cho tới khi chọn. Constraint cho phép null hoặc A1, A, B1, B, C1, C, D1, D2, D, BE, C1E, CE, D1E, D2E, DE. Schema không tự chọn A1 và không cập nhật bài thi/lịch sử cũ.

## V10: quyền thi chính thức

Ghi permission exams:update với resource exams, tránh trùng permission đang hoạt động và mapping. Đăng nhập lại/refresh sau migration để token nhận quyền. Identity không tự công bố kết quả hay chạy migration Examination.

## V11: quyền học và tiến độ

INSERT danh mục và liên kết, không sửa dữ liệu người dùng/phiên hoặc các grant đang có. Permission active cùng tên được reuse; record soft-delete được giữ lịch sử. Không tự gán GUEST/custom role hoặc gán vào role đã soft-delete. Trên seed chuẩn V10 thêm 6 permission và 16 mapping; có dữ liệu bổ sung trước thì số insert có thể ít hơn. [Ma trận đầy đủ](../api-blueprint/progress_practice_permissions.md).

## Quy tắc triển khai

- Dữ liệu mới: chạy migration đủ V1–V11. Dữ liệu có lịch sử: Flyway xác minh/checksum và chạy các version còn thiếu.
- SQL đã chạy được giữ nguyên nội dung. Thay đổi tiếp theo tạo version mới; không xóa lịch sử hoặc sửa checksum để lách lỗi.
- Database có bảng nhưng chưa có lịch sử cần rà riêng trước baseline; không đảm bảo baseline tạo đủ schema.
- .gitattributes giữ nguyên quy tắc -text của V9/V10 và chỉ thêm text eol=lf cho V11; không renormalize migration cũ.
- Đợt chuẩn bị Git không chạy SQL, kiểm tra database hoặc thay dữ liệu tài khoản; danh mục là tài liệu source, không phải báo cáo migration thực tế.

[Cách chạy](../LOCAL_SETUP.md) nêu override Hibernate validate để schema đổi qua Flyway SQL. Tài khoản, session và credential môi trường không nằm trong repo.
