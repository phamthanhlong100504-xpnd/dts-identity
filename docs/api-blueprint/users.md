# API Blueprint: User Profile

## GET /api/v1/users/me

Header Authorization: Bearer <access-token>. Không có request body hoặc tham số chọn userId. Controller lấy UUID chủ thể, UserService.getUser tìm user chưa soft-delete và cache theo UUID trong cache users, TTL 30 phút.

HTTP 200, ví dụ:

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "11111111-1111-4111-8111-111111111111",
    "username": "student_demo",
    "email": "student@example.com",
    "fullName": "Nguyễn Văn An",
    "birthOfDate": "2000-01-01",
    "phoneNumber": "0900000000",
    "status": "ACTIVE",
    "emailVerifiedAt": "2026-10-01T00:00:00Z",
    "phoneVerifiedAt": null,
    "lastLoginAt": "2026-10-18T00:00:00Z",
    "createdAt": "2026-10-01T00:00:00Z",
    "updatedAt": "2026-10-18T00:00:00Z",
    "selectedLicenseClass": "A1"
  },
  "timestamp": "2026-10-18T00:00:00Z"
}
```

selectedLicenseClass có thể null khi chưa chọn. Các mốc thời gian xác minh/login có thể null; birthOfDate là date, timestamp là Instant. Không dùng status hoặc fullName như định danh thay UUID.

Lỗi: thiếu/sai/hết hạn access token →401 tại SecurityConfig; user không tồn tại hoặc bị soft-delete và không được tìm thấy →404, BusinessException(message,status) có thể mang BIZ-400. Profile có cache nên không coi GET này là truy vấn DB trực tiếp ở mọi lần gọi. Có thể429 bởi rate limiter. Xem [contract notes](contract_notes.md).

## PUT /api/v1/users/me

Dùng DTO UpdateMyProfileRequest riêng. Không dùng UpdateUserRequest của admin cho API tự sửa hồ sơ. Contract chi tiết ở [update_my_profile.md](update_my_profile.md), gồm username 3–20 ký tự, email, fullName, birthOfDate, phoneNumber và quy tắc trim/emailVerifiedAt. Không gửi status để tự đổi trạng thái tài khoản.

## PUT /api/v1/users/me/license

Dùng body {"licenseClass":"A1"}, response data.licenseClass. Xem [select_license_class.md](select_license_class.md); GET profile ở trên lại trả data.selectedLicenseClass.

## GET /api/v1/users/me/roles

Header Bearer; lấy chủ thể hiện tại. Service đọc user_roles, bỏ role null/đã soft-delete, trả danh sách RoleResponse với permission name đang đọc từ role_permissions.

HTTP 200:

```json
{
  "success": true,
  "message": "Success",
  "data": [
    {
      "id": "22222222-2222-4222-8222-222222222222",
      "name": "ROLE_STUDENT",
      "permissions": ["questions:read", "exams:read"]
    }
  ],
  "timestamp": "2026-10-18T00:00:00Z"
}
```

Danh sách permission trong ví dụ không phải cam kết mọi môi trường có đúng hai quyền. Việc sửa role/permission thuộc API admin; đọc danh sách này không tự viết lại JWT đã cấp.

Nguồn: [UserController](../../src/main/java/com/dts/identity/controller/UserController.java), [UserService](../../src/main/java/com/dts/identity/service/UserService.java), [UserResponse](../../src/main/java/com/dts/identity/dto/response/UserResponse.java), [RedisConfig](../../src/main/java/com/dts/identity/config/RedisConfig.java).
