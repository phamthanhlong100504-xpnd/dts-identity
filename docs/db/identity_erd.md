# Database: Identity Service ERD

## Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           DTS_IDENTITY DATABASE                             │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────┐       ┌──────────────────────────┐
│         users            │       │         roles            │
├──────────────────────────┤       ├──────────────────────────┤
│ PK  id           UUID    │       │ PK  id           UUID    │
│     username     VARCHAR │       │     name         VARCHAR │
│     email        TEXT    │       │     metadata     JSONB   │
│     password     TEXT    │       │     created_at   TIMESTMP│
│     full_name    VARCHAR │       │     updated_at   TIMESTMP│
│     birth_of_date DATE   │       │     deleted_at   TIMESTMP│
│     phone_number VARCHAR │       │     created_by   UUID FK │
│     status       VARCHAR │       │     updated_by   UUID    │
│     email_verified_at    │       └───────────┬──────────────┘
│     phone_verified_at    │                   │
│     failed_login_attempts│       ┌───────────▼──────────────┐
│     locked_until         │       │    role_permissions      │
│     last_login_at        │       ├──────────────────────────┤
│     created_at           │       │ PK  role_id      UUID FK │
│     updated_at           │       │ PK  permission_id UUID FK │
│     deleted_at           │       │     assigned_at  TIMESTMP │
│     created_by   UUID FK │       └───────────┬──────────────┘
│     updated_by   UUID    │                   │
│     version      BIGINT  │       ┌───────────▼──────────────┐
│     metadata     JSONB   │       │      permissions         │
└──────────┬───────────────┘       ├──────────────────────────┤
           │                       │ PK  id           UUID    │
           │                       │     name         VARCHAR │
┌──────────▼───────────────┐       │     display_name VARCHAR │
│       user_roles         │       │     resource     VARCHAR │
├──────────────────────────┤       │     metadata     JSONB   │
│ PK  user_id      UUID FK │       │     created_at   TIMESTMP│
│ PK  role_id      UUID FK │       │     updated_at   TIMESTMP│
│     assigned_at  TIMESTMP │       │     deleted_at   TIMESTMP│
└──────────────────────────┘       │     created_by   UUID    │
                                   │     updated_by   UUID    │
                                   └──────────────────────────┘

┌──────────────────────────┐       ┌──────────────────────────┐
│     refresh_tokens       │       │    verification_codes    │
├──────────────────────────┤       ├──────────────────────────┤
│ PK  id           UUID    │       │ PK  id           UUID    │
│ FK  user_id      UUID    │       │ FK  user_id      UUID    │
│     token_hash   TEXT    │       │     code_hash    TEXT    │
│     device_info  TEXT    │       │     type         VARCHAR │
│     expires_at   TIMESTMP│       │     attempts     INT     │
│     revoked_at   TIMESTMP│       │     expires_at   TIMESTMP│
│     created_at   TIMESTMP│       │     used_at      TIMESTMP│
└──────────────────────────┘       │     created_at   TIMESTMP│
                                   └──────────────────────────┘

┌──────────────────────────┐
│      outbox_events       │
├──────────────────────────┤
│ PK  id           UUID    │
│     aggregate_id VARCHAR │
│     aggregate_type VARCHAR│
│     event_type   VARCHAR │
│     topic        VARCHAR │
│     payload      JSONB   │
│     status       VARCHAR │  (PENDING → PROCESSED / FAILED)
│     retry_count  INT     │
│     max_retries  INT     │
│     last_error   TEXT    │
│     created_at   TIMESTMP│
│     processed_at TIMESTMP│
└──────────────────────────┘
```

### Relationships

| From | To | Type | Note |
|---|---|---|---|
| `user_roles` | `users` | N:1 | ON DELETE CASCADE |
| `user_roles` | `roles` | N:1 | ON DELETE CASCADE |
| `role_permissions` | `roles` | N:1 | ON DELETE CASCADE |
| `role_permissions` | `permissions` | N:1 | ON DELETE CASCADE |
| `refresh_tokens` | `users` | N:1 | ON DELETE CASCADE |
| `verification_codes` | `users` | N:1 | ON DELETE CASCADE |
| `users.created_by` | `users` | Self-ref | Audit |
| `roles.created_by` | `users` | Self-ref | Audit |

**Cardinality:**
- 1 User có N Role (qua user_roles)
- 1 Role có N Permission (qua role_permissions)
- 1 User có N RefreshToken
- 1 User có N VerificationCode

---

## RBAC Model (Role-Based Access Control)

### Kiến trúc 3 tầng

```
┌─────────────┐     ┌──────────────┐     ┌───────────────┐
│    USER     │────>│     ROLE     │────>│  PERMISSION   │
│             │ N:M │              │ N:M │               │
│ - username  │     │ - ROLE_ADMIN │     │ - users:read  │
│ - email     │     │ - ROLE_TEACH.│     │ - users:create│
│ - status    │     │ - ROLE_STUD. │     │ - exams:read  │
│             │     │ - ROLE_GUEST │     │ - ...         │
└─────────────┘     └──────────────┘     └───────────────┘
```

### Cách hoạt động

1. **User** được gán 1 hoặc nhiều **Role** (bảng `user_roles`)
2. Mỗi **Role** có 1 hoặc nhiều **Permission** (bảng `role_permissions`)
3. Khi user login, JWT access token chứa cả `roles` và `permissions` trong claims
4. `JwtAuthenticationFilter` tạo `GrantedAuthority` từ roles + permissions (prefix `PERM_`)
5. Controller dùng `@PreAuthorize("hasRole('ADMIN')")` hoặc `hasAuthority('PERM_users:create')`

### Dynamic Roles

- Role mặc định có thể mở rộng qua API `POST /api/v1/admin/roles`
- Tên role tự động uppercase + prefix `ROLE_`
- Role bị xóa (soft-delete) vẫn giữ trong `user_roles` để bảo toàn lịch sử

### Audit Trail

- Tất cả bảng master data (`users`, `roles`, `permissions`) có `created_by`, `updated_by`
- `user_roles` và `role_permissions` có `assigned_at`
- Soft-delete: `deleted_at` thay vì DELETE vật lý

---

## Outbox Pattern

### Vấn đề

Khi user đăng ký hoặc quên mật khẩu, cần **gửi OTP qua email/SMS** (Notification Service) **VÀ** lưu OTP vào DB trong **cùng một transaction**. Nếu gửi trực tiếp qua Kafka:
- Transaction DB rollback nhưng Kafka message đã gửi → OTP bị mất
- Kafka lỗi nhưng DB không rollback → DB có OTP nhưng user không nhận được

### Giải pháp: Transactional Outbox

```
┌─────────────────────────────────────────────────────────────────┐
│                    SINGLE DB TRANSACTION                        │
│                                                                 │
│  1. INSERT INTO verification_codes (OTP hash)                  │
│  2. INSERT INTO outbox_events (payload chứa OTP, topic, type)  │
│     status = 'PENDING'                                          │
│                                                                 │
│  → COMMIT (cả 2 cùng thành công hoặc cùng rollback)             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              OutboxScheduler (@Scheduled 5s)                    │
│                                                                 │
│  1. SELECT * FROM outbox_events WHERE status = 'PENDING'        │
│     ORDER BY created_at LIMIT 50                                │
│                                                                 │
│  2. FOR EACH event:                                              │
│     KafkaTemplate.send(topic, payload)                          │
│     → Success: UPDATE status = 'PROCESSED', processed_at = now  │
│     → Failure: retry_count++, status = 'FAILED' nếu max retries │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      KAFKA BROKER                               │
│                                                                 │
│  Topic: auth-events                                             │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ { "otp": "146213", "email": "...", "userId": "...",       │  │
│  │   "eventType": "verification-code-generated", ... }       │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                   │
│                              ▼                                   │
│              NOTIFICATION SERVICE (consumer)                     │
│              → Gửi email/SMS chứa OTP                           │
└─────────────────────────────────────────────────────────────────┘
```

### Các event types

| Event Type | Topic | Trigger |
|---|---|---|
| `verification-code-generated` | `auth-events` | Register, Resend OTP, Forgot Password |
| `password-reset-requested` | `auth-events` | Forgot Password |
| `password-reset-completed` | `auth-events` | Reset Password |
| `verification-code-resent` | `auth-events` | Resend Verification |

### Retention

- `OutboxScheduler` cleanup: xóa event `PROCESSED` sau 7 ngày
- `SchedulerConfig`: xóa `refresh_tokens` hết hạn/revoked hàng giờ

---

## Index Strategy

| Bảng | Index | Mục đích |
|---|---|---|
| `users` | `uq_users_username` (partial) | Unique lookup, ignore soft-deleted |
| `users` | `uq_users_email` (partial) | Unique lookup, ignore soft-deleted |
| `users` | `uq_users_phone` (partial) | Unique lookup, ignore soft-deleted |
| `users` | `idx_users_status` (partial) | Filter by status |
| `roles` | `uq_roles_name` | Unique role name |
| `permissions` | `uq_permissions_name` (partial) | Unique permission name, ignore soft-deleted |
| `refresh_tokens` | `idx_refresh_tokens_user_id` | Find tokens by user |
| `refresh_tokens` | `idx_refresh_tokens_hash` | Token lookup by hash |
| `verification_codes` | `idx_verification_codes_user_id` | Find codes by user |
| `outbox_events` | `idx_outbox_status_created` (partial) | Poll PENDING events |
| `outbox_events` | `idx_outbox_aggregate` | Lookup by aggregate |
