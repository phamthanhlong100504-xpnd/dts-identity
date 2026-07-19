# API Blueprint: User Profile

## Endpoint: Xem profile

```
GET /api/v1/users/me
Authorization: Bearer <access_token>
```

### Prerequisites

- Access token hợp lệ trong header `Authorization`

### Logic (server side)

1. Spring Security xác thực JWT → `Authentication` object
2. Lấy `userId` từ `authentication.getPrincipal()`
3. Tìm user trong bảng `users` (có Redis cache, TTL 30 phút)
4. Map sang `UserResponse` DTO
5. Trả về 200

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "a6808bb0-f090-4f84-965b-26f8d78ef01c",
    "username": "student01",
    "email": "student01@example.com",
    "fullName": "Nguyen Van A",
    "birthOfDate": "2000-01-01",
    "phoneNumber": "+84901234567",
    "status": "ACTIVE",
    "emailVerifiedAt": "2026-07-19T09:00:00Z",
    "phoneVerifiedAt": null,
    "lastLoginAt": "2026-07-20T00:00:00Z",
    "createdAt": "2026-07-19T08:00:00Z",
    "updatedAt": "2026-07-20T00:00:00Z"
  },
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

### Errors

| Code | Message | Nguyên nhân |
|---|---|---|
| `403` | Access denied | Không có token hoặc token hết hạn |
| `404` | User not found | User đã bị soft-delete |

---

## Endpoint: Cập nhật profile

```
PUT /api/v1/users/me
Authorization: Bearer <access_token>
```

### Request Body (JSON) — Partial Update

```json
{
  "fullName": "Nguyen Van B",
  "phoneNumber": "+84907654321"
}
```

| Field | Type | Required | Constraint |
|---|---|---|---|
| `username` | string | ❌ | 3–50 ký tự, unique |
| `email` | string | ❌ | Email hợp lệ, unique |
| `fullName` | string | ❌ | Max 100 ký tự |
| `birthOfDate` | string (date) | ❌ | `YYYY-MM-DD` |
| `phoneNumber` | string | ❌ | Max 20 ký tự, unique |
| `status` | string | ❌ | `PENDING`, `ACTIVE`, `LOCKED`, `BANNED` |

> Chỉ gửi field cần thay đổi. Field `null` hoặc không gửi sẽ được bỏ qua.

### Logic (server side)

1. Lấy `userId` từ `Authentication`
2. Tìm user, kiểm tra từng field được gửi lên:
   - Nếu thay đổi `username`/`email`/`phoneNumber` → kiểm tra unique
   - Các field khác → cập nhật trực tiếp
3. `userRepository.save(user)` → evict Redis cache
4. Trả về 200

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "a6808bb0-f090-4f84-965b-26f8d78ef01c",
    "username": "student01",
    "email": "student01@example.com",
    "fullName": "Nguyen Van B",
    "birthOfDate": "2000-01-01",
    "phoneNumber": "+84907654321",
    "status": "ACTIVE",
    "emailVerifiedAt": "2026-07-19T09:00:00Z",
    "phoneVerifiedAt": null,
    "lastLoginAt": "2026-07-20T00:00:00Z",
    "createdAt": "2026-07-19T08:00:00Z",
    "updatedAt": "2026-07-20T00:05:00Z"
  },
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

### Errors

| Code | Message | Nguyên nhân |
|---|---|---|
| `400` | Username already taken | Trùng username với user khác |
| `400` | Email already registered | Trùng email |
| `400` | Invalid status | Status không hợp lệ |
| `403` | Access denied | Không có token |

---

## Endpoint: Xem roles & permissions

```
GET /api/v1/users/me/roles
Authorization: Bearer <access_token>
```

### Logic (server side)

1. Lấy `userId` từ `Authentication`
2. Query `user_roles` JOIN `roles` WHERE `userId = ?` AND `roles.deleted_at IS NULL`
3. Với mỗi role, query `role_permissions` JOIN `permissions` để lấy danh sách permission name
4. Trả về 200

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Success",
  "data": [
    {
      "id": "b1234567-0000-0000-0000-000000000001",
      "name": "ROLE_STUDENT",
      "permissions": ["questions:read", "exams:read"]
    }
  ],
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

## User Status Lifecycle

```
┌─────────┐  verify OTP   ┌────────┐
│ PENDING │──────────────>│ ACTIVE │
└─────────┘               └───┬────┘
                              │
                   5 lần sai  │  Admin
                   mật khẩu   │  unlock
                              │
                         ┌────▼───────┐    30 phút    ┌────────┐
                         │   LOCKED   │──────────────>│ ACTIVE │
                         └─────┬──────┘               └────────┘
                               │ Admin ban
                               ▼
                         ┌────────┐
                         │ BANNED │ (không thể đăng nhập)
                         └────────┘
```

---

> Generated according to the implementation plan. See `auth.md` for authentication endpoints and `admin.md` for admin management.
