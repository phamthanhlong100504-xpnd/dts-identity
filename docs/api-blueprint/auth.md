# API Blueprint: Authentication

## Endpoint: Đăng nhập

```
POST /api/v1/auth/login
```

### Prerequisites

- Tài khoản đã được đăng ký và verify email (status = `ACTIVE`)
- Tài khoản không bị khóa (`LOCKED`) hoặc cấm (`BANNED`)

### Request Body (JSON)

```json
{
  "username": "student01",
  "password": "SecurePass@123",
  "deviceInfo": "Windows"
}
```

| Field | Type | Required | Constraint |
|---|---|---|---|
| `username` | string | ✅ | Username hoặc email |
| `password` | string | ✅ | |
| `deviceInfo` | string | ❌ | Tên thiết bị (VD: `"Windows"`, `"PostmanRuntime"`) |

### Logic (server side)

1. Tìm user theo `username` hoặc `email` trong bảng `users`
2. Nếu không tìm thấy → giả lập hash mật khẩu dummy (chống timing enumeration), trả về 401
3. Kiểm tra trạng thái `LOCKED` → nếu đang bị khóa, trả về 429 kèm thời gian mở khóa
4. Kiểm tra trạng thái `BANNED` → trả về 401 (cùng thông báo với sai mật khẩu)
5. So sánh mật khẩu bằng `BCryptPasswordEncoder.matches()` (constant-time)
6. Nếu sai → tăng `failed_login_attempts`, nếu đạt ngưỡng 5 → khóa 30 phút, trả về 401
7. Nếu đúng → reset `failed_login_attempts`, `locked_until`, cập nhật `last_login_at`
8. Build `AuthResponse`: generate access token (HS384, 15 phút) + refresh token (HS256, 7 ngày)
9. Lưu refresh token hash (SHA-256) vào bảng `refresh_tokens`
10. Trả về 200 kèm token và thông tin user (id, username, email, fullName, roles, permissions)

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "a6808bb0-f090-4f84-965b-26f8d78ef01c",
      "username": "student01",
      "email": "student01@example.com",
      "fullName": "Nguyen Van A",
      "roles": ["ROLE_STUDENT"],
      "permissions": ["questions:read", "exams:read"]
    }
  },
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

### Errors

| Code | Message | Nguyên nhân |
|---|---|---|
| `400` | Validation errors | Thiếu `username` hoặc `password` |
| `401` | Invalid username or password | Sai thông tin đăng nhập hoặc tài khoản bị BANNED |
| `429` | Account locked until ... | Vượt quá 5 lần đăng nhập sai, thử lại sau 30 phút |

---

## Endpoint: Đăng ký

```
POST /api/v1/auth/register
```

### Prerequisites

- Không có tài khoản nào trùng `username`, `email`, hoặc `phoneNumber`

### Request Body (JSON)

```json
{
  "username": "student01",
  "email": "student01@example.com",
  "password": "SecurePass@123",
  "fullName": "Nguyen Van A",
  "birthOfDate": "2000-01-01",
  "phoneNumber": "+84901234567"
}
```

| Field | Type | Required | Constraint |
|---|---|---|---|
| `username` | string | ✅ | 3–50 ký tự, unique |
| `email` | string | ✅ | Email hợp lệ, unique |
| `password` | string | ✅ | 8–100 ký tự |
| `fullName` | string | ✅ | Max 100 ký tự |
| `birthOfDate` | string (date) | ✅ | `YYYY-MM-DD`, phải trong quá khứ |
| `phoneNumber` | string | ✅ | Max 20 ký tự, unique |

### Logic (server side)

1. Kiểm tra trùng lặp `username`, `email`, `phoneNumber` (chỉ trên record chưa soft-delete)
2. Tìm role mặc định `ROLE_STUDENT` trong bảng `roles` → nếu không tồn tại, trả về 500
3. Tạo `User` với `status = PENDING`, hash mật khẩu bằng BCrypt (strength 12)
4. Lưu user vào bảng `users`
5. Gán role `ROLE_STUDENT` qua bảng `user_roles`
6. Sinh OTP 6 chữ số ngẫu nhiên (cryptographically secure), hash SHA-256 → lưu vào `verification_codes`
7. Ghi outbox event (`verification-code-generated`) vào bảng `outbox_events` — chứa OTP raw trong payload
8. `OutboxScheduler` (5s) quét và publish lên Kafka topic `auth-events`
9. Notification Service consumer nhận event → gửi email chứa OTP
10. Build `AuthResponse` + trả về 201 (user cần verify OTP để chuyển sang ACTIVE)

### Response (JSON, 201 Created)

```json
{
  "success": true,
  "message": "Registration successful. Please verify your email.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "a6808bb0-f090-4f84-965b-26f8d78ef01c",
      "username": "student01",
      "email": "student01@example.com",
      "fullName": "Nguyen Van A",
      "roles": [],
      "permissions": ["questions:read", "exams:read"]
    }
  },
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

### Errors

| Code | Message | Nguyên nhân |
|---|---|---|
| `400` | Username already taken | Trùng username |
| `400` | Email already registered | Trùng email |
| `400` | Phone number already registered | Trùng phoneNumber |
| `400` | Validation errors | Thiếu field hoặc sai format |
| `500` | Default role not found | Bảng roles chưa được seed |

---

## Endpoint: Refresh Token

```
POST /api/v1/auth/refresh
```

### Request Body (JSON)

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

| Field | Type | Required |
|---|---|---|
| `refreshToken` | string | ✅ |

### Logic (server side)

1. Validate JWT signature + expiry của refresh token
2. Hash token bằng SHA-256 → tìm trong bảng `refresh_tokens`
3. Kiểm tra token chưa bị revoke và chưa hết hạn
4. **Token rotation:** revoke token cũ (`revoked_at = now`)
5. Lấy user từ database, kiểm tra chưa bị soft-delete
6. Sinh access token + refresh token mới, lưu refresh token hash vào DB
7. Trả về 200

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Token refreshed",
  "data": {
    "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "a6808bb0-f090-4f84-965b-26f8d78ef01c",
      "username": "student01",
      "email": "student01@example.com",
      "fullName": "Nguyen Van A",
      "roles": ["ROLE_STUDENT"],
      "permissions": ["questions:read", "exams:read"]
    }
  },
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

### Errors

| Code | Message | Nguyên nhân |
|---|---|---|
| `401` | Invalid or expired refresh token | Token sai chữ ký hoặc hết hạn |
| `401` | Refresh token not recognized | Token không tồn tại trong DB |
| `401` | Refresh token has been revoked or expired | Token đã bị revoke |
| `401` | User not found / Account deleted | User không tồn tại hoặc đã bị xóa |

---

## Endpoint: Đăng xuất

```
POST /api/v1/auth/logout
Authorization: Bearer <access_token>
```

### Prerequisites

- Access token hợp lệ trong header `Authorization`

### Logic (server side)

1. Spring Security xác thực JWT → `Authentication` object được inject
2. Lấy `userId` từ `authentication.getPrincipal()`
3. Gọi `refreshTokenRepository.revokeAllByUserId(userId, now)` — revoke **tất cả** refresh token
4. Trả về 200

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Logged out",
  "data": null,
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

### Errors

| Code | Message | Nguyên nhân |
|---|---|---|
| `403` | Access denied | Không có token hoặc token hết hạn |

---

## Endpoint: Xác thực OTP

```
POST /api/v1/auth/verify
```

### Request Body (JSON)

```json
{
  "identifier": "student01",
  "code": "146213",
  "type": "REGISTER"
}
```

| Field | Type | Required | Note |
|---|---|---|---|
| `identifier` | string | ✅ | Username hoặc email |
| `code` | string | ✅ | Mã OTP 6 chữ số |
| `type` | string | ✅ | `REGISTER`, `RESET_PASSWORD`, `CHANGE_EMAIL` |

### Logic (server side)

1. Tìm user theo `identifier`
2. Parse `type` → nếu không hợp lệ, trả về 400
3. Tìm OTP mới nhất của user theo type trong `verification_codes`
4. Kiểm tra OTP chưa hết hạn, chưa bị dùng, chưa vượt quá `maxAttempts` (3 lần)
5. So sánh `code` với `codeHash` bằng **constant-time comparison** (SHA-256)
6. Nếu sai → tăng `attempts`, trả về 400
7. Nếu đúng → đánh dấu `used_at = now`
8. Xử lý theo type:
   - `REGISTER`: `status → ACTIVE`, `emailVerifiedAt → now`
   - `CHANGE_EMAIL`: `emailVerifiedAt → now`
   - `RESET_PASSWORD`: chỉ đánh dấu OTP đã dùng (password reset qua endpoint riêng)
9. Trả về 200

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Verification successful",
  "data": null,
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

### Errors

| Code | Message | Nguyên nhân |
|---|---|---|
| `400` | User not found | Không tìm thấy user |
| `400` | Invalid verification type | Type không hợp lệ |
| `400` | No verification code found | Chưa yêu cầu OTP hoặc OTP cũ đã bị vô hiệu |
| `400` | Verification code has expired or already used | OTP hết hạn hoặc đã dùng |
| `400` | Too many failed attempts | Nhập sai quá 3 lần |
| `400` | Invalid verification code | Sai mã OTP |

---

## Endpoint: Đổi mật khẩu

```
POST /api/v1/auth/change-password
Authorization: Bearer <access_token>
```

### Request Body (JSON)

```json
{
  "oldPassword": "SecurePass@123",
  "newPassword": "NewSecurePass@456"
}
```

| Field | Type | Required | Constraint |
|---|---|---|---|
| `oldPassword` | string | ✅ | |
| `newPassword` | string | ✅ | 8–100 ký tự |

### Logic (server side)

1. Lấy `userId` từ `Authentication`
2. Tìm user, kiểm tra `oldPassword` khớp với hash trong DB
3. Hash `newPassword` bằng BCrypt → cập nhật `users.password`
4. Revoke **tất cả** refresh token của user (bảo mật: force re-login trên mọi thiết bị)
5. Trả về 200

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Password changed successfully",
  "data": null,
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

### Errors

| Code | Message | Nguyên nhân |
|---|---|---|
| `400` | Old password is incorrect | Mật khẩu cũ không đúng |
| `403` | Access denied | Không có token |

---

## Endpoint: Quên mật khẩu

```
POST /api/v1/auth/forgot-password
```

### Request Body (JSON)

```json
{
  "identifier": "student01"
}
```

| Field | Type | Required | Note |
|---|---|---|---|
| `identifier` | string | ✅ | Username hoặc email |

### Logic (server side)

1. Tìm user theo `identifier`
2. **Luôn trả về 200** (kể cả khi user không tồn tại — chống user enumeration)
3. Nếu user tồn tại:
   - Vô hiệu hóa tất cả OTP `RESET_PASSWORD` cũ
   - Sinh OTP 6 chữ số mới, hash SHA-256 → lưu `verification_codes`
   - Ghi outbox event (`password-reset-requested` + `verification-code-generated`) → Kafka
   - Notification Service gửi email chứa OTP

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "If the account exists, a reset code has been sent",
  "data": null,
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

## Endpoint: Đặt lại mật khẩu

```
POST /api/v1/auth/reset-password
```

### Request Body (JSON)

```json
{
  "identifier": "student01",
  "code": "146213",
  "newPassword": "NewSecurePass@456"
}
```

| Field | Type | Required | Constraint |
|---|---|---|---|
| `identifier` | string | ✅ | Username hoặc email |
| `code` | string | ✅ | Mã OTP 6 chữ số |
| `newPassword` | string | ✅ | 8–100 ký tự |

### Logic (server side)

1. Tìm user theo `identifier`
2. Verify OTP (giống flow `/verify` với type `RESET_PASSWORD`)
3. Hash `newPassword` bằng BCrypt → cập nhật `users.password`
4. Reset `failedLoginAttempts = 0`, `lockedUntil = null`
5. Revoke tất cả refresh token
6. Ghi outbox event (`password-reset-completed`) → Kafka

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Password reset successful",
  "data": null,
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

### Errors

| Code | Message | Nguyên nhân |
|---|---|---|
| `400` | User not found | |
| `400` | No verification code found | Chưa gọi `/forgot-password` |
| `400` | Verification code has expired or already used | |
| `400` | Invalid verification code | Sai OTP |

---

## Endpoint: Gửi lại mã xác thực

```
POST /api/v1/auth/resend-verification
```

### Request Body (JSON)

```json
{
  "identifier": "student01",
  "type": "REGISTER"
}
```

| Field | Type | Required |
|---|---|---|
| `identifier` | string | ✅ |
| `type` | string | ✅ | `REGISTER`, `RESET_PASSWORD`, `CHANGE_EMAIL` |

### Logic (server side)

1. Tìm user, parse type
2. Vô hiệu hóa tất cả OTP cũ của user theo type
3. Sinh OTP mới → lưu `verification_codes` + outbox event (`verification-code-resent`)
4. Trả về 200

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Verification code resent",
  "data": null,
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

## Endpoint: Validate Token (API Gateway)

```
GET /api/v1/auth/validate
Authorization: Bearer <access_token>
```

### Logic (server side)

1. Trích xuất token từ header `Authorization` (hỗ trợ cả raw token và `Bearer <token>`)
2. Nếu không có token → trả về `valid: false`
3. Parse JWT, verify signature + expiry
4. Trích xuất `userId`, `username`, `roles`, `permissions` từ claims
5. Trả về 200

### Response (JSON, 200 OK) — Token hợp lệ

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "valid": true,
    "userId": "a6808bb0-f090-4f84-965b-26f8d78ef01c",
    "username": "student01",
    "roles": ["ROLE_STUDENT"],
    "permissions": ["questions:read", "exams:read"]
  },
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

### Response (JSON, 200 OK) — Token không hợp lệ

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "valid": false,
    "userId": null,
    "username": null,
    "roles": null,
    "permissions": null
  },
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

> Generated according to the implementation plan. See `docs/db/identity_erd.md` for database schema and Outbox pattern details.
