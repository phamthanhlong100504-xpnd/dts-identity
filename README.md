# DTS Identity

Identity Service của DTS quản lý tài khoản, xác thực JWT, hồ sơ học viên và danh mục role/permission. Service chạy Java 21, Maven, Spring Boot 3.3.2; dữ liệu nằm trong PostgreSQL, cache profile dùng Redis, sự kiện người dùng gửi qua Kafka bằng outbox.

| Nội dung | Tài liệu |
| --- | --- |
| Chạy local, biến môi trường, migration | [Local setup](docs/LOCAL_SETUP.md) |
| Chức năng và quy trình nghiệp vụ | [Identity features](docs/IDENTITY_FEATURES.md) |
| API và phần bổ sung so với main | [API blueprints](docs/api-blueprint/README.md) |
| Migration V1–V10 | [Migration catalog](docs/db/MIGRATION_CATALOG.md) |
| Mẫu cấu hình không chứa credential thật | [.env.example](.env.example) |

Phần mới của đợt bàn giao: cập nhật hồ sơ cá nhân bằng DTO riêng; chọn hạng GPLX và đọc selectedLicenseClass trong profile/auth response; quyền exams:update cho ROLE_ADMIN. Quyền chapter đã nằm trong commit V8 của nhánh hiện tại. Các tài liệu mô tả source hiện có; quá trình chuẩn bị Git không thay đổi logic đăng nhập, role, OTP, token hoặc nội dung migration.

Identity local dùng http://localhost:8081; ứng dụng web thường đi qua Gateway http://localhost:8888. Không lấy userId từ query/header của client để chọn chủ thể cho API /users/me; chủ thể lấy từ JWT.

Pull repo mang theo code, schema migration và tài liệu. Database người dùng, session, OTP, SMTP credential, secret JWT và dữ liệu của repo khác thuộc từng môi trường. Thành viên dùng cấu hình được nhóm bàn giao và các repo DTS tương ứng để chạy đủ luồng web; không dùng bản snapshot đã che thông tin làm source triển khai.
