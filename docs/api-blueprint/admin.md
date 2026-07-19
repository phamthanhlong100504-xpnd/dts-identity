# API Blueprint: Admin Management

> **Base:** `/api/v1/admin` | **Auth:** `Authorization: Bearer <access_token>` + `ROLE_ADMIN`

---

## Endpoint: Danh sách người dùng

```
GET /api/v1/admin/users?search=&status=&role=&page=0&size=20
```

### Request Parameters

| Parameter | In | Type | Required | Description |
|---|---|---|---|---|
| `search` | query | string | ❌ | Tìm trong username, email, fullName, phoneNumber |
| `status` | query | string | ❌ | `PENDING`, `ACTIVE`, `LOCKED`, `BANNED` |
| `role` | query | string | ❌ | Lọc theo tên role (VD: `ROLE_STUDENT`) |
| `page` | query | int | ❌ | Default: 0 |
| `size` | query | int | ❌ | Default: 20 |

### Logic (server side)

1. Build JPA `Specification` với các điều kiện:
   - `deletedAt IS NULL` (luôn luôn)
   - Nếu có `search` → `LIKE` trên `username`, `email`, `fullName`, `phoneNumber`
   - Nếu có `status` → `EQUAL` trên `status`
   - Nếu có `role` → JOIN `user_roles` → JOIN `roles` → `EQUAL` trên `roles.name`
2. Query phân trang qua `userRepository.findAll(spec, pageable)`
3. Map sang `UserResponse` DTO
4. Trả về 200

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "content": [
      {
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
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20,
      "sort": { "sorted": false, "empty": true, "unsorted": true },
      "offset": 0,
      "paged": true,
      "unpaged": false
    },
    "totalPages": 1,
    "totalElements": 1,
    "first": true,
    "last": true,
    "size": 20,
    "number": 0,
    "sort": { "sorted": false, "empty": true, "unsorted": true },
    "numberOfElements": 1,
    "empty": false
  },
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

### Errors

| Code | Message | Nguyên nhân |
|---|---|---|
| `400` | Invalid status filter | Status không hợp lệ |
| `403` | Access denied | Không có role `ROLE_ADMIN` |

---

## Endpoint: Xem chi tiết người dùng

```
GET /api/v1/admin/users/{id}
```

### Request Parameters

| Parameter | In | Type | Required |
|---|---|---|---|
| `id` | path | UUID | ✅ |

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

| Code | Message |
|---|---|
| `404` | User not found |

---

## Endpoint: Tạo người dùng (Admin)

```
POST /api/v1/admin/users
```

### Request Body (JSON)

```json
{
  "username": "teacher01",
  "email": "teacher01@example.com",
  "password": "SecurePass@123",
  "fullName": "Tran Van B",
  "birthOfDate": "1990-05-15",
  "phoneNumber": "+84909876543",
  "roleIds": ["b1234567-0000-0000-0000-000000000002"]
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
| `roleIds` | UUID[] | ❌ | Danh sách role cần gán |

### Logic (server side)

1. Kiểm tra trùng lặp `username`, `email`, `phoneNumber`
2. Tạo user với `status = ACTIVE`, `emailVerifiedAt = now` (không cần verify)
3. Hash mật khẩu BCrypt
4. Nếu có `roleIds` → validate từng role tồn tại → insert `user_roles`
5. Trả về 201

### Response (JSON, 201 Created)

```json
{
  "success": true,
  "message": "User created",
  "data": {
    "id": "b8765432-0000-0000-0000-000000000001",
    "username": "teacher01",
    "email": "teacher01@example.com",
    "fullName": "Tran Van B",
    "birthOfDate": "1990-05-15",
    "phoneNumber": "+84909876543",
    "status": "ACTIVE",
    "emailVerifiedAt": "2026-07-20T00:00:00Z",
    "phoneVerifiedAt": null,
    "lastLoginAt": null,
    "createdAt": "2026-07-20T00:00:00Z",
    "updatedAt": "2026-07-20T00:00:00Z"
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
| `404` | Role not found | `roleIds` chứa UUID không tồn tại |

---

## Endpoint: Cập nhật người dùng

```
PUT /api/v1/admin/users/{id}
```

### Request Parameters

| Parameter | In | Type | Required |
|---|---|---|---|
| `id` | path | UUID | ✅ |

### Request Body (JSON) — Partial Update

```json
{
  "fullName": "Tran Van C",
  "status": "LOCKED"
}
```

> Cùng schema với `PUT /api/v1/users/me`. Field `status` hỗ trợ `PENDING`, `ACTIVE`, `LOCKED`, `BANNED`.

---

## Endpoint: Cập nhật trạng thái người dùng

```
PATCH /api/v1/admin/users/{id}/status
```

### Request Body (JSON)

```json
{
  "status": "ACTIVE"
}
```

| Field | Type | Required | Valid Values |
|---|---|---|---|
| `status` | string | ✅ | `PENDING`, `ACTIVE`, `LOCKED`, `BANNED` |

### Logic (server side)

1. Tìm user, parse + validate status mới
2. Cập nhật `status`
3. Side effects:
   - `ACTIVE` → clear `failedLoginAttempts = 0`, `lockedUntil = null`
   - `BANNED` / `LOCKED` → revoke tất cả refresh token
4. Lưu user, trả về 200

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "a6808bb0-f090-4f84-965b-26f8d78ef01c",
    "username": "student01",
    "status": "ACTIVE",
    "...": "..."
  },
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

## Endpoint: Xóa người dùng (soft-delete)

```
DELETE /api/v1/admin/users/{id}
```

| Parameter | In | Type | Required |
|---|---|---|---|
| `id` | path | UUID | ✅ |

### Logic (server side)

1. Tìm user
2. Set `deleted_at = now`
3. Revoke tất cả refresh token của user
4. Trả về 200

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "User deleted",
  "data": null,
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

## Endpoint: Gán role cho người dùng

```
POST /api/v1/admin/roles/assign
```

### Request Body (JSON)

```json
{
  "userId": "a6808bb0-f090-4f84-965b-26f8d78ef01c",
  "roleId": "b1234567-0000-0000-0000-000000000002"
}
```

| Field | Type | Required |
|---|---|---|
| `userId` | UUID | ✅ |
| `roleId` | UUID | ✅ |

### Logic (server side)

1. Validate `userId` và `roleId` tồn tại
2. Kiểm tra chưa được gán → insert `user_roles`
3. Trả về 201 (hoặc 200 nếu đã tồn tại)

### Response (JSON, 201 Created)

```json
{
  "success": true,
  "message": "Role assigned",
  "data": null,
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

## Endpoint: Thu hồi role

```
DELETE /api/v1/admin/users/{userId}/roles/{roleId}
```

| Parameter | In | Type | Required |
|---|---|---|---|
| `userId` | path | UUID | ✅ |
| `roleId` | path | UUID | ✅ |

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Role revoked",
  "data": null,
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

## Endpoint: Xem roles của người dùng

```
GET /api/v1/admin/users/{id}/roles
```

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

## Endpoint: Danh sách roles

```
GET /api/v1/admin/roles
```

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Success",
  "data": [
    {
      "id": "b1234567-0000-0000-0000-000000000000",
      "name": "ROLE_ADMIN",
      "permissions": [
        "users:read", "users:create", "users:update", "users:delete",
        "roles:read", "roles:assign", "roles:revoke",
        "permissions:read", "permissions:manage",
        "questions:read", "questions:create", "questions:update", "questions:delete",
        "exams:read", "exams:create", "exams:delete",
        "analytics:read"
      ]
    }
  ],
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

## Endpoint: Tạo role

```
POST /api/v1/admin/roles
```

### Request Body (JSON)

```json
{
  "name": "moderator"
}
```

| Field | Type | Required | Constraint |
|---|---|---|---|
| `name` | string | ✅ | 3–50 ký tự, unique |

### Logic (server side)

1. Normalize name: uppercase + thay thế ký tự đặc biệt bằng `_`
2. Prefix `ROLE_` nếu chưa có (VD: `moderator` → `ROLE_MODERATOR`)
3. Kiểm tra unique, insert role với audit trail (`created_by`)
4. Trả về 201

### Response (JSON, 201 Created)

```json
{
  "success": true,
  "message": "Role created",
  "data": {
    "id": "c1234567-0000-0000-0000-000000000001",
    "name": "ROLE_MODERATOR",
    "permissions": []
  },
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

## Endpoint: Xóa role (soft-delete)

```
DELETE /api/v1/admin/roles/{id}
```

### Logic (server side)

1. Tìm role (chỉ lấy record chưa soft-delete)
2. Set `deleted_at = now`, `updated_by = actorId`
3. **Giữ nguyên** `user_roles` và `role_permissions` (bảo toàn lịch sử)

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Role deleted",
  "data": null,
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

## Endpoint: Danh sách permissions

```
GET /api/v1/admin/permissions
```

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Success",
  "data": [
    {
      "id": "d0000001-0000-0000-0000-000000000001",
      "name": "users:read",
      "displayName": "Xem danh sách người dùng",
      "resource": "users"
    },
    {
      "id": "d0000002-0000-0000-0000-000000000001",
      "name": "questions:read",
      "displayName": "Xem câu hỏi",
      "resource": "questions"
    }
  ],
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

### Default Permissions

| Resource | `name` | `displayName` |
|---|---|---|
| `users` | `users:read` | Xem danh sách người dùng |
| `users` | `users:create` | Tạo người dùng |
| `users` | `users:update` | Cập nhật người dùng |
| `users` | `users:delete` | Xóa người dùng |
| `roles` | `roles:read` | Xem danh sách vai trò |
| `roles` | `roles:assign` | Gán vai trò |
| `roles` | `roles:revoke` | Thu hồi vai trò |
| `permissions` | `permissions:read` | Xem danh sách quyền |
| `permissions` | `permissions:manage` | Quản lý phân quyền |
| `questions` | `questions:read` | Xem câu hỏi |
| `questions` | `questions:create` | Tạo câu hỏi |
| `questions` | `questions:update` | Cập nhật câu hỏi |
| `questions` | `questions:delete` | Xóa câu hỏi |
| `exams` | `exams:read` | Xem bài thi |
| `exams` | `exams:create` | Tạo bài thi |
| `exams` | `exams:delete` | Xóa bài thi |
| `analytics` | `analytics:read` | Xem báo cáo thống kê |

---

## Endpoint: Tạo permission

```
POST /api/v1/admin/permissions
```

### Request Body (JSON)

```json
{
  "name": "reports:export",
  "displayName": "Xuất báo cáo",
  "resource": "reports"
}
```

| Field | Type | Required | Constraint |
|---|---|---|---|
| `name` | string | ✅ | Max 100 ký tự, unique |
| `displayName` | string | ✅ | Max 100 ký tự |
| `resource` | string | ✅ | Max 50 ký tự |

### Response (JSON, 201 Created)

```json
{
  "success": true,
  "message": "Permission created",
  "data": {
    "id": "e0000001-0000-0000-0000-000000000001",
    "name": "reports:export",
    "displayName": "Xuất báo cáo",
    "resource": "reports"
  },
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

## Endpoint: Cập nhật permission

```
PUT /api/v1/admin/permissions/{id}
```

### Request Body (JSON) — Partial Update

```json
{
  "displayName": "Xuất báo cáo Excel"
}
```

| Field | Type | Required |
|---|---|---|
| `name` | string | ❌ |
| `displayName` | string | ❌ |
| `resource` | string | ❌ |

---

## Endpoint: Xóa permission (soft-delete)

```
DELETE /api/v1/admin/permissions/{id}
```

### Logic (server side)

1. Tìm permission (chưa soft-delete)
2. Set `deleted_at = now`, `updated_by = actorId`
3. Xóa tất cả `role_permissions` liên quan (cleanup associations)
4. Trả về 200

---

## Endpoint: Gán permission cho role

```
POST /api/v1/admin/roles/{roleId}/permissions/{permissionId}
```

| Parameter | In | Type | Required |
|---|---|---|---|
| `roleId` | path | UUID | ✅ |
| `permissionId` | path | UUID | ✅ |

### Response (JSON, 201 Created)

```json
{
  "success": true,
  "message": "Permission assigned to role",
  "data": null,
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

## Endpoint: Thu hồi permission khỏi role

```
DELETE /api/v1/admin/roles/{roleId}/permissions/{permissionId}
```

### Response (JSON, 200 OK)

```json
{
  "success": true,
  "message": "Permission revoked from role",
  "data": null,
  "timestamp": "2026-07-20T00:00:00.000Z"
}
```

---

> Generated according to the implementation plan. See `auth.md` for authentication and `identity_erd.md` for database schema.
