# API Blueprint: Chọn hạng giấy phép lái xe

## Endpoint và điều kiện

PUT /api/v1/users/me/license. Header Authorization: Bearer <access-token>, Content-Type: application/json. Database đã áp dụng V9__add_selected_license_class.sql. Chủ thể là UUID từ JWT, không nhận userId để sửa người khác.

## Request

```json
{"licenseClass":"A1"}
```

licenseClass là chuỗi bắt buộc @NotBlank. Giá trị enum đúng hoa/thường:

A1, A, B1, B, C1, C, D1, D2, D, BE, C1E, CE, D1E, D2E, DE.

Service dùng LicenseClass.valueOf trực tiếp, không trim hoặc uppercase. "a1", " A1 " và hạng ngoài danh sách đều không hợp lệ. null/trắng không phải lệnh bỏ lựa chọn; contract này không có API reset về null.

## Logic

1. Kiểm tra body/hạng, đối chiếu enum.
2. Tìm user hiện tại chưa soft-delete.
3. Lưu tên enum vào users.selected_license_class rồi save trong transaction.
4. Evict cache users của userId; trả UserLicenseResponse.

V9 thêm cột VARCHAR(10), nullable và CHECK danh sách enum. Không backfill A1 cho user cũ. Update không đổi username/email/status, không sửa bài thi/học cũ, không cấp token mới và không gọi OutboxPublisher trong nhánh chọn hạng này. Gửi lại cùng hạng đặt cùng giá trị nghiệp vụ nhưng không hứa updated_at/version giữ nguyên.

## Response — 200

```json
{
  "success": true,
  "message": "License class updated",
  "data": {"licenseClass":"A1"},
  "timestamp": "2026-10-18T00:00:00Z"
}
```

Sau đó GET /users/me trả data.selectedLicenseClass="A1". Login/register/refresh trả data.user.selectedLicenseClass. Không đổi tên response của endpoint này thành selectedLicenseClass và không đọc hạng từ JWT.

## Lỗi và phía client

- 400 VAL-400: null/trắng ở DTO.
- 400 BIZ-400: chuỗi không thuộc enum trong service.
- 400 REQ-400: JSON không parse được.
- 401: Bearer thiếu/sai; 404: user không tìm thấy; 429: quá hạn mức HTTP.

Client coi null ở profile/auth là chưa chọn, lưu state theo dữ liệu server và làm mới dữ liệu phụ thuộc hạng. Số câu, thời gian, điểm đỗ không do Identity cấp; lấy từ API của Practice/Examination tương ứng.

Nguồn: [UpdateLicenseRequest](../../src/main/java/com/dts/identity/dto/request/UpdateLicenseRequest.java), [LicenseClass](../../src/main/java/com/dts/identity/entity/LicenseClass.java), [UserLicenseResponse](../../src/main/java/com/dts/identity/dto/response/UserLicenseResponse.java), [V9](../../src/main/resources/db/migration/V9__add_selected_license_class.sql).
