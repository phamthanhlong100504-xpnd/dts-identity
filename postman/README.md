# DTS Identity Service - Postman Auto Test

## Tổng quan

Bộ test tự động cho DTS Identity Service. Tester **không cần nhập token hay ID thủ công** - mọi thứ được tự động hóa qua Postman pre-request scripts và test scripts.

## Server VPS đang hoạt động ✅

| Service | URL | Status |
|---------|-----|--------|
| **Identity Service** | `http://103.75.182.249:8081` | ✅ Online |
| **Practice Service** | `http://103.75.182.249:8083` | ✅ Online |
| **Swagger UI (Practice)** | `http://103.75.182.249:8083/swagger-ui/index.html` | ✅ Online |

### Credentials đã xác nhận trên VPS

| Username | Password | Role |
|----------|----------|------|
| `testuser2` | `Test@1234` | `ROLE_ADMIN`, `ROLE_STUDENT` |

## Files

| File | Mô tả |
|------|-------|
| `DTS_Identity_Collection.json` | Postman Collection với toàn bộ API và automation scripts |
| `DTS_Identity_Environment.json` | Postman Environment với các biến cần thiết |
| `README.md` | File này |

## Cách import vào Postman

1. Mở Postman
2. Click **Import** (góc trên trái)
3. Kéo thả hoặc chọn cả 2 file JSON vào
4. Vào **Environments** → chọn **DTS Identity - Local**

## Cấu hình ban đầu (chỉ cần làm 1 lần)

Mở Environment **DTS Identity - Local** và điền 2 giá trị bắt buộc:

| Biến | Giá trị mặc định | Mô tả |
|------|-----------------|-------|
| `baseUrl` | `http://127.0.0.1:8081` | URL của service |
| `adminUsername` | `admin` | Username admin |
| `adminPassword` | `Admin@12345` | **Thay bằng password thật** |

> **Lưu ý:** Các biến có tag `[TU DONG]` được tự động cập nhật bởi scripts, không cần chỉnh sửa.

## Chạy toàn bộ collection (Collection Runner)

1. Click chuột phải vào collection **DTS Identity Service - Auto Test**
2. Chọn **Run collection**
3. Chạy theo thứ tự khuyến nghị:

```
1.  Authentication / Login Admin          → Lấy accessToken, refreshToken
2.  Admin - Permissions / Create Permission → Lấy createdPermissionId  
3.  Admin - Permissions / List Permissions
4.  Admin - Permissions / Update Permission
5.  Admin - Roles / Create Role            → Lấy createdRoleId
6.  Admin - Roles / List Roles
7.  Admin - Roles / Assign Permission to Role
8.  Admin - Users / Create User (Admin)   → Lấy createdUserId
9.  Admin - Users / List Users
10. Admin - Users / Get User by ID
11. Admin - Users / Update User
12. Admin - Users / Get User Roles
13. Admin - Roles / Assign Role to User
14. Admin - Users / Update User Status LOCKED
15. Admin - Users / Update User Status ACTIVE
16. Admin - Roles / Revoke Permission from Role
17. Admin - Roles / Revoke Role from User
18. User Management / Get My Profile
19. User Management / Update My Profile
20. User Management / Get My Roles
21. Authentication / Validate Token
22. Authentication / Refresh Token
23. Authentication / Register
24. Authentication / Logout
25. Admin - Roles / Delete Role
26. Admin - Permissions / Delete Permission
27. Admin - Users / Delete User
```

## Cơ chế tự động hóa

### 1. Tự động lấy & lưu Token
Sau khi **Login Admin** thành công:
```javascript
// Test script tự động chạy:
pm.environment.set('accessToken', json.data.accessToken);
pm.environment.set('refreshToken', json.data.refreshToken);
pm.environment.set('adminUserId', json.data.user.id);
```
→ Tất cả request tiếp theo tự động dùng `{{accessToken}}` trong header Authorization.

### 2. Tự động tạo dữ liệu ngẫu nhiên
Để tránh conflict khi chạy nhiều lần, các request Create tự động tạo data:
```javascript
// Pre-request script:
var ts = Date.now();
pm.environment.set('newUsername', 'user_' + ts.toString().slice(-8));
pm.environment.set('newEmail', 'user_' + ts + '@test.com');
```

### 3. Tự động lưu ID sau khi tạo
Sau mỗi Create request:
```javascript
// Test script:
pm.environment.set('createdUserId', json.data.id);    // dùng cho Get/Update/Delete
pm.environment.set('createdRoleId', json.data.id);
pm.environment.set('createdPermissionId', json.data.id);
```

### 4. Tự động xóa biến sau khi Delete
```javascript
pm.environment.unset('createdUserId');
```

## Các biến cần nhập thủ công (hạn chế tối đa)

| Biến | Khi nào cần | Mô tả |
|------|-------------|-------|
| `otpCode` | Chỉ khi test Verify/Reset Password | OTP từ email |
| `testEmail` | Khi test Forgot Password | Email có tài khoản thật |
| `testUsername` | Khi test Login User | Username user thường |
| `testPassword` | Khi test Login User | Password user thường |

> **Ghi chú:** OTP flows (forgot-password, verify, reset-password) yêu cầu OTP thật từ email → không thể tự động hoàn toàn.

## Test Assertions tự động

Mỗi request đều có kiểm tra tự động:
- ✅ HTTP Status code là 200
- ✅ `response.success === true`
- ✅ Response time < 3000ms
- ✅ Dữ liệu trả về đúng schema (có `id`, `data`, v.v.)
- ✅ Status field khớp với giá trị mong đợi (ACTIVE, LOCKED...)

## Môi trường khác

Tạo thêm environment bằng cách duplicate **DTS Identity - Local** và đổi `baseUrl`:

| Môi trường | baseUrl |
|-----------|---------|
| Local | `http://127.0.0.1:8081` |
| Docker | `http://localhost:8081` |
| Staging | `https://staging-api.dts.vn` |
| Production | `https://api.dts.vn` |
