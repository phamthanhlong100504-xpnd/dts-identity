# Tài liệu DTS Identity

Đọc [LOCAL_SETUP.md](LOCAL_SETUP.md) để chạy máy mới, rồi [IDENTITY_FEATURES.md](IDENTITY_FEATURES.md) để hiểu nghiệp vụ và ranh giới với Gateway/Practice/Result/Examination.

Tài liệu API theo source hiện tại nằm ở [api-blueprint/README.md](api-blueprint/README.md). Các trang mới giải thích profile, chọn GPLX, trường user trong auth response và quyền cho các service. [db/MIGRATION_CATALOG.md](db/MIGRATION_CATALOG.md) mô tả thứ tự SQL V1–V10.

Các trang auth.md/admin.md, API-DOCS.md, openapi.json, ERD và Postman cũ được giữ để tham khảo; nếu ví dụ cũ khác phần profile/hạng GPLX hiện tại, đọc contract_notes.md và các blueprint mới. Swagger /api-docs của đúng phiên bản service đang chạy là nguồn OpenAPI động. Không sửa Java hoặc chạy dữ liệu thử chỉ để khớp ví dụ tài liệu cũ.
