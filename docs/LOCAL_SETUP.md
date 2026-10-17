# Chạy DTS Identity trên máy mới

## 1. Công cụ và đường dẫn

- JDK 21 và Maven cài trên máy, Git, Docker Desktop ở chế độ Linux containers.
- Repo hiện tại không có Maven Wrapper; dùng lệnh mvn, không dùng mvnw lấy từ repo khác.
- Ví dụ Windows dùng D:\DTS\dts-identity. Khi pull công việc đang bàn giao, dùng nhánh feature/content-builder-chapter-permissions cho tới khi nhóm merge vào main.

```powershell
Set-Location -LiteralPath 'D:\DTS\dts-identity'
java -version
mvn -version
```

## 2. Các thành phần cần chạy

| Thành phần | Địa chỉ khi Java chạy trên Windows | Vai trò |
| --- | --- | --- |
| Identity | localhost:8081 | Auth, profile, admin APIs |
| PostgreSQL | localhost:5432 / dts_identity | User, role, token hash, OTP hash, outbox |
| Redis | localhost:6379 | Cache profile users, TTL 30 phút |
| Kafka | localhost:9092 | user-events và auth-events |
| SMTP | Theo cấu hình của nhóm | Gửi OTP đăng ký/khôi phục email |
| Gateway | localhost:8888 | Điểm gọi API của web, cấu hình ở repo Gateway |

Identity không tự chạy các repo Practice, Progress, Result, Content Builder, Media hoặc Examination. Để dùng đủ web, chạy các service cần cho màn hình đó theo hướng dẫn từng repo. JWT access secret/issuer phải khớp cấu hình xác thực của các service tiêu thụ token. Chạy Java trực tiếp thì URL service ở Gateway phải trỏ localhost, không dùng hostname chỉ tồn tại trong Docker network.

## 3. Cấu hình máy mới

Chỉ trên máy mới chưa có .env:

```powershell
Copy-Item -LiteralPath '.env.example' -Destination '.env'
notepad .env
```

Nếu đã có cấu hình đang chạy thì giữ file hiện có. Điền DB_PASSWORD theo database thật, hai JWT secret theo môi trường nhóm và thông tin SMTP. REDIS_PASSWORD để trống khi Redis không dùng mật khẩu. Mẫu không chứa account/password/JWT thật và không tự tạo user admin.

Spring Boot/Maven không tự nạp .env. Có thể cấu hình environment trong IDE hoặc nạp các dòng KEY=value vào terminal hiện tại bằng đoạn sau. Giá trị ghi trực tiếp sau dấu =, không thêm nháy bao ngoài; đoạn nạp không thực thi nội dung như PowerShell code.

```powershell
& {
    $envPath = Join-Path (Get-Location).Path '.env'
    foreach ($line in [IO.File]::ReadAllLines($envPath)) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) {
            continue
        }
        $pair = $line -split '=', 2
        if ($pair.Count -ne 2 -or $pair[0] -notmatch '^[A-Z][A-Z0-9_]*$') {
            throw 'Dòng cấu hình .env không đúng KEY=value.'
        }
        [Environment]::SetEnvironmentVariable($pair[0], $pair[1], 'Process')
    }
}
```

Mẫu dùng EUREKA_ENABLED=false khi không chạy Eureka. APP_MAIL_ENABLED=true cùng SMTP hợp lệ cần thiết để nhận OTP qua email. Tắt mail không làm API trả OTP và không thay thế được nghiệp vụ xác thực email.

## 4. Hạ tầng cho Java chạy trực tiếp

Trên môi trường mới chưa có các container DTS trùng tên/cổng, repo cung cấp file infrastructure:

```powershell
docker compose -f docker-compose.infra.yml up -d postgres redis zookeeper kafka
```

Nếu đã có dts-identity-postgres, dts-identity-redis, dts-kafka, dts-zookeeper của hệ thống DTS thì dùng các container hiện có; không tạo stack thứ hai. Không dùng down -v để giải quyết lỗi cấu hình vì lệnh đó xóa volume. Mật khẩu PostgreSQL của volume đã tạo không thay đổi chỉ bằng sửa .env; phải dùng credential thật của volume.

File infra advertise Kafka localhost:9092, phù hợp Java trên host. docker-compose.yml có service identity-service chạy trong network và advertise kafka:9092; dùng bộ compose tương ứng, không ghép hai cách advertise tùy ý. docker-compose.prod.yml dành cho môi trường triển khai, không thay thế lệnh local này.

## 5. Migration rồi chạy ứng dụng

Migration có trong src/main/resources/db/migration, Flyway dùng flyway_schema_history, chạy theo version số V1 đến V10. Với cấu hình mẫu:

- SPRING_FLYWAY_ENABLED=true: các SQL chưa chạy được thực thi khi khởi động service.
- SPRING_FLYWAY_VALIDATE_ON_MIGRATE=true: đối chiếu checksum migration đã ghi.
- SPRING_JPA_HIBERNATE_DDL_AUTO=validate: Hibernate kiểm tra schema sau Flyway, không tự thêm cột.

application.yml đang có ddl-auto: update và được giữ nguyên trong đợt chuẩn bị Git. Mẫu environment override thành validate để thành viên áp dụng thay đổi cơ sở dữ liệu qua migration. Cấu hình mẫu chỉ có hiệu lực sau khi được nạp vào tiến trình Java.

```powershell
Set-Location -LiteralPath 'D:\DTS\dts-identity'
mvn spring-boot:run
```

Đây là bước vận hành có thể ghi database qua migration và các worker, không phải bước kiểm tra chỉ đọc. Chọn đúng database, giữ backup theo quy trình nhóm trước khi nâng cấp database đã có dữ liệu. Không chạy từng V9/V10 bằng psql rồi để Flyway không có lịch sử; không sửa SQL đã áp dụng, xóa schema history hay dùng repair để bỏ qua checksum không khớp.

Database trống chạy đủ V1–V10. Database đã có bảng nhưng chưa có lịch sử Flyway phải được rà riêng vì baseline-on-migrate=true, baseline-version=1 trong source; không coi baseline tự động là chứng minh schema đúng.

## 6. Điểm truy cập và dữ liệu

- Identity: http://localhost:8081
- Swagger: http://localhost:8081/swagger-ui.html
- OpenAPI động: http://localhost:8081/api-docs
- Health: http://localhost:8081/actuator/health
- Gateway của web: http://localhost:8888, cấu hình trong repo dts-gateway.

V1 seed danh mục role/permission, không seed đầy đủ tài khoản của máy người phát triển. V7 chỉ gán role nếu các username chỉ định đã tồn tại; không tạo account hay password. Máy mới đăng ký qua API và quản trị quyền theo quy trình nhóm, hoặc dùng backup được bàn giao ngoài Git. Không chép token/OTP/refresh session từ máy khác vào source.

Sau V8/V10, token cấp trước migration chưa tự có permission mới. Đăng nhập lại hoặc refresh để nhận JWT chứa danh sách quyền hiện tại. Trường selectedLicenseClass đọc từ /users/me hoặc data.user của auth response; không giải mã JWT để tìm hạng GPLX vì claim này không được thêm vào token.

## 7. Cách đọc lỗi cấu hình thường gặp

| Hiện tượng | Nơi cần đối chiếu |
| --- | --- |
| Không kết nối PostgreSQL | Container, DB_HOST/PORT/NAME và credential của volume |
| Cache profile không truy cập được | Redis thực tế, host/port/password |
| Login được nhưng OTP không tới | SMTP và APP_MAIL_ENABLED; thành công HTTP không bảo đảm mail đã gửi vì gửi bất đồng bộ |
| Token bị service khác từ chối | Access secret, issuer, token hết hạn và cấu hình từng repo |
| Admin chưa có quyền chapter/exam mới | Flyway đã chạy V8/V10 và token được cấp lại |
| Kafka gửi lỗi | Broker đang chạy, advertised listeners truy cập được từ tiến trình Java |

Đợt chuẩn bị Git không đổi cấu hình đang chạy, khởi động hạ tầng, đăng nhập tài khoản hay thực thi migration.
