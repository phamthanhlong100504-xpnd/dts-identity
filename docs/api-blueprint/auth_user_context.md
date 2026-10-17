# API Blueprint: User context của auth response

## API liên quan

| Method / endpoint | Input chính | Thành công |
| --- | --- | --- |
| POST /api/v1/auth/login | username, password; deviceInfo optional | 200 |
| POST /api/v1/auth/register | username, email, password, fullName, birthOfDate, phoneNumber | 201 |
| POST /api/v1/auth/refresh | refreshToken | 200 |

Đây là bổ sung field của response hiện có, không tạo endpoint auth mới. Các API dùng body JSON; trường username của login có thể là email theo truy vấn findByIdentifier. Đăng ký vẫn dùng username 3–20, password 8–100, email hợp lệ, fullName không trắng tối đa100, ngày sinh quá khứ và phone không trắng tối đa20 theo RegisterRequest.

## Response shape

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "<access-token>",
    "refreshToken": "<refresh-token>",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "11111111-1111-4111-8111-111111111111",
      "username": "student_demo",
      "email": "student@example.com",
      "fullName": "Nguyễn Văn An",
      "roles": ["ROLE_STUDENT"],
      "permissions": ["questions:read", "exams:read"],
      "selectedLicenseClass": "A1"
    }
  },
  "timestamp": "2026-10-18T00:00:00Z"
}
```

Ví dụ minh họa response login. Register message là Registration successful. Please verify your email.; refresh là Token refreshed. expiresIn là giây từ cấu hình expiration-ms/1000; 900 là mặc định 15 phút, không hard-code cho mọi môi trường.

## Logic và dữ liệu mới

AuthService.buildAuthResponse đọc role/permission và user hiện tại, tạo cặp token rồi lưu refresh token hash. UserInfo bổ sung selectedLicenseClass từ User; không thêm trường này vào claims của JWT. User mới đăng ký chưa chọn hạng có giá trị null.

Refresh thành công thu hồi refresh token cũ, cấp cặp mới và user context lấy từ dữ liệu user lúc đó. Nếu người dùng đổi hạng trên web, cập nhật state từ /users/me/license và/hoặc /users/me; không cần đổi cơ chế xác thực để chứa hạng trong token.

## Ranh giới client

- Dùng Authorization: Bearer với accessToken; refreshToken chỉ dùng cho luồng refresh.
- Không nhầm data.user.id với username hoặc selectedLicenseClass.
- Không giả định auth user context chứa birthOfDate/phoneNumber/status như UserResponse; muốn hồ sơ đầy đủ dùng /users/me.
- Role/permission mới được cấp cần token mới. Không dùng claim username của token cũ để ghi đè hồ sơ vừa cập nhật.
- Không lưu token/password/OTP thật trong tài liệu hoặc file Postman versioned.

Lỗi HTTP theo [contract_notes.md](contract_notes.md). Login sai thông tin →401; register trùng dữ liệu theo nhánh kiểm tra →409; refresh sai/hết hạn/thu hồi →401; auth rate limit →429. Các API công khai vẫn chịu Bean Validation.

Nguồn: [AuthResponse](../../src/main/java/com/dts/identity/dto/response/AuthResponse.java), [AuthService](../../src/main/java/com/dts/identity/service/AuthService.java), [User](../../src/main/java/com/dts/identity/entity/User.java).
