# DTS Identity Service

**Version:** 1.0.0
**Description:** Authentication & Authorization Service

---

## GET `/`

> **Tag:** home-controller | Globe - Public

**/**

### Responses

**200** — OK

---

## GET `/api/v1/admin/permissions`

> **Tag:** Admin | Globe - Public

**List all permissions**

### Responses

**200** — OK
> Schema: `ApiResponseListPermissionResponse`

---

## POST `/api/v1/admin/permissions`

> **Tag:** Admin | Globe - Public

**Create a new permission**

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | `string` | Yes | - [max: 100] |
| `displayName` | `string` | Yes | - [max: 100] |
| `resource` | `string` | Yes | - [max: 50] |

### Responses

**200** — OK
> Schema: `ApiResponsePermissionResponse`

---

## PUT `/api/v1/admin/permissions/{id}`

> **Tag:** Admin | Globe - Public

**Update a permission**

### Parameters

| Name | In | Required | Type | Description |
|------|-----|----------|------|-------------|
| `id` | path | Yes | `string` | - |

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | `string` | No | - [max: 100] |
| `displayName` | `string` | No | - [max: 100] |
| `resource` | `string` | No | - [max: 50] |

### Responses

**200** — OK
> Schema: `ApiResponsePermissionResponse`

---

## DELETE `/api/v1/admin/permissions/{id}`

> **Tag:** Admin | Globe - Public

**Soft-delete a permission**

### Parameters

| Name | In | Required | Type | Description |
|------|-----|----------|------|-------------|
| `id` | path | Yes | `string` | - |

### Responses

**200** — OK
> Schema: `ApiResponseVoid`

---

## GET `/api/v1/admin/roles`

> **Tag:** Admin | Globe - Public

**List all roles with their permissions**

### Responses

**200** — OK
> Schema: `ApiResponseListRoleResponse`

---

## POST `/api/v1/admin/roles`

> **Tag:** Admin | Globe - Public

**Create a new role**

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | `string` | Yes | - [max: 50, min: 3] |

### Responses

**200** — OK
> Schema: `ApiResponseRoleResponse`

---

## POST `/api/v1/admin/roles/assign`

> **Tag:** Admin | Globe - Public

**Assign a role to a user**

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userId` | `string (uuid)` | Yes | - |
| `roleId` | `string (uuid)` | Yes | - |

### Responses

**200** — OK
> Schema: `ApiResponseVoid`

---

## DELETE `/api/v1/admin/roles/{id}`

> **Tag:** Admin | Globe - Public

**Soft-delete a role (preserves history)**

### Parameters

| Name | In | Required | Type | Description |
|------|-----|----------|------|-------------|
| `id` | path | Yes | `string` | - |

### Responses

**200** — OK
> Schema: `ApiResponseVoid`

---

## POST `/api/v1/admin/roles/{roleId}/permissions/{permissionId}`

> **Tag:** Admin | Globe - Public

**Assign a permission to a role**

### Parameters

| Name | In | Required | Type | Description |
|------|-----|----------|------|-------------|
| `roleId` | path | Yes | `string` | - |
| `permissionId` | path | Yes | `string` | - |

### Responses

**200** — OK
> Schema: `ApiResponseVoid`

---

## DELETE `/api/v1/admin/roles/{roleId}/permissions/{permissionId}`

> **Tag:** Admin | Globe - Public

**Revoke a permission from a role**

### Parameters

| Name | In | Required | Type | Description |
|------|-----|----------|------|-------------|
| `roleId` | path | Yes | `string` | - |
| `permissionId` | path | Yes | `string` | - |

### Responses

**200** — OK
> Schema: `ApiResponseVoid`

---

## GET `/api/v1/admin/users`

> **Tag:** Admin | Globe - Public

**List users with optional search and filter (paginated)**

### Parameters

| Name | In | Required | Type | Description |
|------|-----|----------|------|-------------|
| `pageable` | query | Yes | `string` | - |
| `search` | query | No | `string` | - |
| `status` | query | No | `string` | - |
| `role` | query | No | `string` | - |

### Responses

**200** — OK
> Schema: `ApiResponsePageUserResponse`

---

## POST `/api/v1/admin/users`

> **Tag:** Admin | Globe - Public

**Create a new user (admin only, no verification needed)**

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | Yes | - [max: 20, min: 3] |
| `email` | `string` | Yes | - |
| `password` | `string` | Yes | - [max: 100, min: 8] |
| `fullName` | `string` | Yes | - [max: 100] |
| `birthOfDate` | `string (date)` | Yes | - |
| `phoneNumber` | `string` | Yes | - [max: 20] |
| `roleIds` | `array` | No | - |

### Responses

**200** — OK
> Schema: `ApiResponseUserResponse`

---

## GET `/api/v1/admin/users/{id}`

> **Tag:** Admin | Globe - Public

**Get user by ID**

### Parameters

| Name | In | Required | Type | Description |
|------|-----|----------|------|-------------|
| `id` | path | Yes | `string` | - |

### Responses

**200** — OK
> Schema: `ApiResponseUserResponse`

---

## PUT `/api/v1/admin/users/{id}`

> **Tag:** Admin | Globe - Public

**Update user**

### Parameters

| Name | In | Required | Type | Description |
|------|-----|----------|------|-------------|
| `id` | path | Yes | `string` | - |

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | No | - [max: 20, min: 3] |
| `email` | `string` | No | - |
| `fullName` | `string` | No | - [max: 100] |
| `birthOfDate` | `string (date)` | No | - |
| `phoneNumber` | `string` | No | - [max: 20] |
| `status` | `string` | No | - |

### Responses

**200** — OK
> Schema: `ApiResponseUserResponse`

---

## DELETE `/api/v1/admin/users/{id}`

> **Tag:** Admin | Globe - Public

**Soft-delete user**

### Parameters

| Name | In | Required | Type | Description |
|------|-----|----------|------|-------------|
| `id` | path | Yes | `string` | - |

### Responses

**200** — OK
> Schema: `ApiResponseVoid`

---

## GET `/api/v1/admin/users/{id}/roles`

> **Tag:** Admin | Globe - Public

**Get roles for a specific user**

### Parameters

| Name | In | Required | Type | Description |
|------|-----|----------|------|-------------|
| `id` | path | Yes | `string` | - |

### Responses

**200** — OK
> Schema: `ApiResponseListRoleResponse`

---

## PATCH `/api/v1/admin/users/{id}/status`

> **Tag:** Admin | Globe - Public

**Update user status (ACTIVE, LOCKED, BANNED)**

### Parameters

| Name | In | Required | Type | Description |
|------|-----|----------|------|-------------|
| `id` | path | Yes | `string` | - |

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | `string` | Yes | - |

### Responses

**200** — OK
> Schema: `ApiResponseUserResponse`

---

## DELETE `/api/v1/admin/users/{userId}/roles/{roleId}`

> **Tag:** Admin | Globe - Public

**Revoke a role from a user**

### Parameters

| Name | In | Required | Type | Description |
|------|-----|----------|------|-------------|
| `userId` | path | Yes | `string` | - |
| `roleId` | path | Yes | `string` | - |

### Responses

**200** — OK
> Schema: `ApiResponseVoid`

---

## POST `/api/v1/auth/change-password`

> **Tag:** Authentication | Locked - Auth required

**Change password for authenticated user**

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `oldPassword` | `string` | Yes | - |
| `newPassword` | `string` | Yes | - [max: 100, min: 8] |

### Responses

**200** — OK
> Schema: `ApiResponseVoid`

---

## POST `/api/v1/auth/forgot-password`

> **Tag:** Authentication | Globe - Public

**Request password reset OTP via email**

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `identifier` | `string` | Yes | - |

### Responses

**200** — OK
> Schema: `ApiResponseVoid`

---

## POST `/api/v1/auth/login`

> **Tag:** Authentication | Globe - Public

**Login with username/email and password**

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | Yes | - |
| `password` | `string` | Yes | - |
| `deviceInfo` | `string` | No | - |

### Responses

**200** — OK
> Schema: `ApiResponseAuthResponse`

---

## POST `/api/v1/auth/logout`

> **Tag:** Authentication | Locked - Auth required

**Logout - revoke all refresh tokens for the authenticated user**

### Responses

**200** — OK
> Schema: `ApiResponseVoid`

---

## POST `/api/v1/auth/refresh`

> **Tag:** Authentication | Globe - Public

**Refresh access token using refresh token**

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `refreshToken` | `string` | Yes | - |

### Responses

**200** — OK
> Schema: `ApiResponseAuthResponse`

---

## POST `/api/v1/auth/register`

> **Tag:** Authentication | Globe - Public

**Register a new student account**

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | Yes | - [max: 20, min: 3] |
| `email` | `string` | Yes | - |
| `password` | `string` | Yes | - [max: 100, min: 8] |
| `fullName` | `string` | Yes | - [max: 100] |
| `birthOfDate` | `string (date)` | Yes | - |
| `phoneNumber` | `string` | Yes | - [max: 20] |

### Responses

**200** — OK
> Schema: `ApiResponseAuthResponse`

---

## POST `/api/v1/auth/resend-verification`

> **Tag:** Authentication | Globe - Public

**Resend verification code**

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `identifier` | `string` | Yes | - |
| `type` | `string` | Yes | - |

### Responses

**200** — OK
> Schema: `ApiResponseVoid`

---

## POST `/api/v1/auth/reset-password`

> **Tag:** Authentication | Globe - Public

**Reset password using verification code**

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `identifier` | `string` | Yes | - |
| `code` | `string` | Yes | - |
| `newPassword` | `string` | Yes | - [max: 100, min: 8] |

### Responses

**200** — OK
> Schema: `ApiResponseVoid`

---

## GET `/api/v1/auth/validate`

> **Tag:** Authentication | Locked - Auth required

**Validate access token (for API Gateway)**

### Responses

**200** — OK
> Schema: `ApiResponseTokenValidationResponse`

---

## POST `/api/v1/auth/verify`

> **Tag:** Authentication | Globe - Public

**Verify email/phone with OTP code**

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `identifier` | `string` | Yes | - |
| `code` | `string` | Yes | - |
| `type` | `string` | Yes | - |

### Responses

**200** — OK
> Schema: `ApiResponseVoid`

---

## GET `/api/v1/users/me`

> **Tag:** User Management | Globe - Public

**Get current authenticated user's profile**

### Responses

**200** — OK
> Schema: `ApiResponseUserResponse`

---

## PUT `/api/v1/users/me`

> **Tag:** User Management | Globe - Public

**Update current authenticated user's profile**

### Request Body

`Content-Type: application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | No | - [max: 20, min: 3] |
| `email` | `string` | No | - |
| `fullName` | `string` | No | - [max: 100] |
| `birthOfDate` | `string (date)` | No | - |
| `phoneNumber` | `string` | No | - [max: 20] |
| `status` | `string` | No | - |

### Responses

**200** — OK
> Schema: `ApiResponseUserResponse`

---

## GET `/api/v1/users/me/roles`

> **Tag:** User Management | Globe - Public

**Get current user's roles and permissions**

### Responses

**200** — OK
> Schema: `ApiResponseListRoleResponse`

---

# Schemas

## `ApiResponseAuthResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `success` | `boolean` | No | - |
| `message` | `string` | No | - |
| `data` | `AuthResponse` | No | - |
| `timestamp` | `string (date-time)` | No | - |

## `ApiResponseListPermissionResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `success` | `boolean` | No | - |
| `message` | `string` | No | - |
| `data` | `array` | No | - |
| `timestamp` | `string (date-time)` | No | - |

## `ApiResponseListRoleResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `success` | `boolean` | No | - |
| `message` | `string` | No | - |
| `data` | `array` | No | - |
| `timestamp` | `string (date-time)` | No | - |

## `ApiResponsePageUserResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `success` | `boolean` | No | - |
| `message` | `string` | No | - |
| `data` | `PageUserResponse` | No | - |
| `timestamp` | `string (date-time)` | No | - |

## `ApiResponsePermissionResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `success` | `boolean` | No | - |
| `message` | `string` | No | - |
| `data` | `PermissionResponse` | No | - |
| `timestamp` | `string (date-time)` | No | - |

## `ApiResponseRoleResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `success` | `boolean` | No | - |
| `message` | `string` | No | - |
| `data` | `RoleResponse` | No | - |
| `timestamp` | `string (date-time)` | No | - |

## `ApiResponseTokenValidationResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `success` | `boolean` | No | - |
| `message` | `string` | No | - |
| `data` | `TokenValidationResponse` | No | - |
| `timestamp` | `string (date-time)` | No | - |

## `ApiResponseUserResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `success` | `boolean` | No | - |
| `message` | `string` | No | - |
| `data` | `UserResponse` | No | - |
| `timestamp` | `string (date-time)` | No | - |

## `ApiResponseVoid`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `success` | `boolean` | No | - |
| `message` | `string` | No | - |
| `data` | `object` | No | - |
| `timestamp` | `string (date-time)` | No | - |

## `AssignRoleRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userId` | `string (uuid)` | Yes | - |
| `roleId` | `string (uuid)` | Yes | - |

## `AuthResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `accessToken` | `string` | No | - |
| `refreshToken` | `string` | No | - |
| `tokenType` | `string` | No | - |
| `expiresIn` | `integer (int64)` | No | - |
| `user` | `UserInfo` | No | - |

## `ChangePasswordRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `oldPassword` | `string` | Yes | - |
| `newPassword` | `string` | Yes | - |

## `CreatePermissionRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | `string` | Yes | - |
| `displayName` | `string` | Yes | - |
| `resource` | `string` | Yes | - |

## `CreateRoleRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | `string` | Yes | - |

## `CreateUserRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | Yes | - |
| `email` | `string` | Yes | - |
| `password` | `string` | Yes | - |
| `fullName` | `string` | Yes | - |
| `birthOfDate` | `string (date)` | Yes | - |
| `phoneNumber` | `string` | Yes | - |
| `roleIds` | `array` | No | - |

## `ForgotPasswordRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `identifier` | `string` | Yes | - |

## `LoginRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | Yes | - |
| `password` | `string` | Yes | - |
| `deviceInfo` | `string` | No | - |

## `PageUserResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `totalElements` | `integer (int64)` | No | - |
| `totalPages` | `integer (int32)` | No | - |
| `pageable` | `PageableObject` | No | - |
| `first` | `boolean` | No | - |
| `last` | `boolean` | No | - |
| `numberOfElements` | `integer (int32)` | No | - |
| `size` | `integer (int32)` | No | - |
| `content` | `array` | No | - |
| `number` | `integer (int32)` | No | - |
| `sort` | `array` | No | - |
| `empty` | `boolean` | No | - |

## `Pageable`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `page` | `integer (int32)` | No | - |
| `size` | `integer (int32)` | No | - |
| `sort` | `array` | No | - |

## `PageableObject`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `unpaged` | `boolean` | No | - |
| `pageNumber` | `integer (int32)` | No | - |
| `paged` | `boolean` | No | - |
| `pageSize` | `integer (int32)` | No | - |
| `offset` | `integer (int64)` | No | - |
| `sort` | `array` | No | - |

## `PermissionResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | `string (uuid)` | No | - |
| `name` | `string` | No | - |
| `displayName` | `string` | No | - |
| `resource` | `string` | No | - |

## `RefreshTokenRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `refreshToken` | `string` | Yes | - |

## `RegisterRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | Yes | - |
| `email` | `string` | Yes | - |
| `password` | `string` | Yes | - |
| `fullName` | `string` | Yes | - |
| `birthOfDate` | `string (date)` | Yes | - |
| `phoneNumber` | `string` | Yes | - |

## `ResendVerificationRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `identifier` | `string` | Yes | - |
| `type` | `string` | Yes | - |

## `ResetPasswordRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `identifier` | `string` | Yes | - |
| `code` | `string` | Yes | - |
| `newPassword` | `string` | Yes | - |

## `RoleResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | `string (uuid)` | No | - |
| `name` | `string` | No | - |
| `permissions` | `array` | No | - |

## `SortObject`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `direction` | `string` | No | - |
| `nullHandling` | `string` | No | - |
| `ascending` | `boolean` | No | - |
| `property` | `string` | No | - |
| `ignoreCase` | `boolean` | No | - |

## `TokenValidationResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `valid` | `boolean` | No | - |
| `userId` | `string (uuid)` | No | - |
| `username` | `string` | No | - |
| `roles` | `array` | No | - |
| `permissions` | `array` | No | - |

## `UpdatePermissionRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | `string` | No | - |
| `displayName` | `string` | No | - |
| `resource` | `string` | No | - |

## `UpdateUserRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `username` | `string` | No | - |
| `email` | `string` | No | - |
| `fullName` | `string` | No | - |
| `birthOfDate` | `string (date)` | No | - |
| `phoneNumber` | `string` | No | - |
| `status` | `string` | No | - |

## `UpdateUserStatusRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | `string` | Yes | - |

## `UserInfo`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | `string (uuid)` | No | - |
| `username` | `string` | No | - |
| `email` | `string` | No | - |
| `fullName` | `string` | No | - |
| `roles` | `array` | No | - |
| `permissions` | `array` | No | - |

## `UserResponse`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | `string (uuid)` | No | - |
| `username` | `string` | No | - |
| `email` | `string` | No | - |
| `fullName` | `string` | No | - |
| `birthOfDate` | `string (date)` | No | - |
| `phoneNumber` | `string` | No | - |
| `status` | `string` | No | - |
| `emailVerifiedAt` | `string (date-time)` | No | - |
| `phoneVerifiedAt` | `string (date-time)` | No | - |
| `lastLoginAt` | `string (date-time)` | No | - |
| `createdAt` | `string (date-time)` | No | - |
| `updatedAt` | `string (date-time)` | No | - |

## `VerifyCodeRequest`

Type: `object`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `identifier` | `string` | Yes | - |
| `code` | `string` | Yes | - |
| `type` | `string` | Yes | - |
