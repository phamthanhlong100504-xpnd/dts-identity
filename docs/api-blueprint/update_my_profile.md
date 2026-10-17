# API Blueprint: Cập nhật hồ sơ của tôi

## Endpoint và điều kiện

PUT /api/v1/users/me. Header Authorization: Bearer <access-token>, Content-Type: application/json. Không có userId trong path/body. DTO là UpdateMyProfileRequest, khác DTO admin UpdateUserRequest.

## Request

```json
{
  "fullName": "Nguyễn Văn An",
  "email": "student.new@example.com"
}
```

| Field | Loại | Validation hiện có |
| --- | --- | --- |
| username | string, optional | Size 3–20, phải có ký tự không trắng nếu gửi |
| email | string, optional | Email, tối đa 254, phải có ký tự không trắng nếu gửi |
| fullName | string, optional | Tối đa 100, phải có ký tự không trắng nếu gửi |
| birthOfDate | date, optional | YYYY-MM-DD, không có @Past tại DTO này |
| phoneNumber | string, optional | Tối đa 20, phải có ký tự không trắng nếu gửi |

Bean Validation kiểm tra chuỗi request trước khi service trim. null/không gửi giữ giá trị cũ; không phải thao tác xóa field. Không dùng status, password, roleIds, selectedLicenseClass hoặc id trong request này. Chọn GPLX dùng endpoint riêng; đổi mật khẩu dùng /auth/change-password.

## Logic

1. Lấy UUID từ Authentication; tìm user chưa soft-delete, không có →404.
2. Trim email mới. Nếu khác email hiện tại theo equalsIgnoreCase, kiểm tra email đang dùng ở user chưa xóa theo so sánh không phân biệt hoa/thường. Trùng →409 BIZ-409.
3. Khi email đổi theo điều kiện trên, đặt emailVerifiedAt=null. Không tự gửi OTP hay đổi status; client muốn xác minh dùng luồng CHANGE_EMAIL có sẵn.
4. Tạo UpdateUserRequest nội bộ từ 5 field được phép; status nội bộ luôn null. Trim username/fullName/phoneNumber, giữ birthOfDate.
5. Tái dùng updateUser để lưu, ghi USER_UPDATED vào outbox cùng transaction; payload gồm userId/username/email/fullName và metadata event. Xóa cache users của UUID.

selectedLicenseClass được giữ nguyên bởi thao tác này. Không có cặp token mới; username trong token cũ không tự đổi. Không hứa request lặp không sinh thêm USER_UPDATED, vì source không có idempotency key cho cập nhật profile.

## Response

200 ApiResponse<UserResponse>, data đầy đủ giống [GET profile](users.md), gồm selectedLicenseClass hiện tại và emailVerifiedAt mới. message là Success. Client cập nhật hồ sơ/state từ response server, giữ UUID để liên kết các dữ liệu học tập.

## Lỗi

- 400 VAL-400: vi phạm annotation, ví dụ fullName toàn dấu cách.
- 400 REQ-400: JSON/date sai định dạng.
- 400 BIZ-400: username trùng theo nhánh updateUser; các business validation cũ.
- 409 BIZ-409: email mới trùng theo kiểm tra updateMyProfile.
- 401: chưa xác thực; 404: không tìm thấy user; 429: rate limit.
- Kiểm tra unique phone không được thêm riêng trong service này; DB unique index vẫn áp dụng, exception ngoài mapping có thể500 SYS-500. Không mô tả mọi trùng dữ liệu là409.

Nguồn: [UpdateMyProfileRequest](../../src/main/java/com/dts/identity/dto/request/UpdateMyProfileRequest.java), [UserController](../../src/main/java/com/dts/identity/controller/UserController.java), [UserService](../../src/main/java/com/dts/identity/service/UserService.java).
