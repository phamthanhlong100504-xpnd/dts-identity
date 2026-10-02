
## 1. Mục đích

Tài liệu này mô tả phần nghiệp vụ hiện tại của `dts-identity` liên quan đến xác thực, phân quyền và migration bổ sung quyền quản lý chương cho Content Builder.

## 2. Vai trò của Identity Service

`dts-identity` chịu trách nhiệm cho các nghiệp vụ xác thực và phân quyền dùng chung giữa các service trong hệ thống DTS.

Trong luồng hiện tại:

```text
User
  ↓
Identity Service
  ↓
JWT
  ↓
Gateway / Backend Services
  ↓
Roles + Permissions
```

Các service như Content Builder và Practice sử dụng thông tin xác thực/quyền từ Identity để quyết định người dùng có được phép thực hiện API tương ứng hay không.


## 3. Quyền quản lý chapter của Content Builder

Migration mới:

```text
src/main/resources/db/migration/V8__add_chapter_permissions.sql
```

bổ sung ba permission:

```text
chapters:read
chapters:create
chapters:update
```

Resource:

```text
chapters
```

Ý nghĩa:

- `chapters:read`: quyền xem/đọc chapter.
- `chapters:create`: quyền tạo chapter.
- `chapters:update`: quyền cập nhật chapter.

Các permission này phục vụ API quản lý chapter của Content Builder.

## 4. Gán quyền cho ROLE_ADMIN

Migration đồng thời gán ba quyền mới cho:

```text
ROLE_ADMIN
```

Luồng dữ liệu:

```text
ROLE_ADMIN
    ↓
role_permissions
    ↓
chapters:read
chapters:create
chapters:update
```

Nhờ đó tài khoản admin hiện có có thể sử dụng các API chapter sau khi migration được áp dụng.

## 5. Tính idempotent của migration

Phần tạo permission sử dụng:

```sql
ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING;
```

Mục tiêu:

- không tạo trùng permission đang hoạt động;
- có thể triển khai an toàn trên database đã có dữ liệu.

Phần gán role-permission sử dụng:

```sql
ON CONFLICT (role_id, permission_id) DO NOTHING;
```

Mục tiêu:

- không tạo trùng mapping role-permission;
- giữ migration an toàn nếu quyền đã được gán từ trước.


## 6. Migration hiện tại

Nội dung nghiệp vụ của `V8__add_chapter_permissions.sql`:

```sql
INSERT INTO permissions (id, name, display_name, resource)
VALUES
    (gen_random_uuid(), 'chapters:read',   'Xem chương học',  'chapters'),
    (gen_random_uuid(), 'chapters:create', 'Tạo chương học', 'chapters'),
    (gen_random_uuid(), 'chapters:update', 'Sửa chương học', 'chapters')
ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN'
  AND p.name IN (
      'chapters:read',
      'chapters:create',
      'chapters:update'
  )
  AND p.deleted_at IS NULL
ON CONFLICT (role_id, permission_id) DO NOTHING;
```

## 7. Quan hệ với Content Builder

Sau khi migration được áp dụng:

```text
Admin login
   ↓
Identity cấp JWT
   ↓
JWT chứa role / permission theo cơ chế hiện tại
   ↓
Content Builder nhận request
   ↓
Security kiểm tra permission chapter
```

Mục tiêu là đảm bảo backend Content Builder có thể dùng permission riêng cho nghiệp vụ chapter thay vì phụ thuộc một quyền chung không đúng phạm vi.

## 8. Yêu cầu deployment


Cần bảo đảm Identity production được cấu hình đúng cho môi trường VPS, gồm:

```text
PostgreSQL production
Redis production
JWT access secret
JWT refresh secret
JWT issuer
các biến môi trường/service configuration hiện có
```

## 9. Áp dụng migration production

Khi deploy version mới của `dts-identity`, migration:

```text
V8__add_chapter_permissions.sql
```

phải được áp dụng vào database Identity production theo cơ chế migration hiện tại của service.

Nếu môi trường production chạy Flyway tự động khi service khởi động, migration sẽ được thực thi theo version sau các migration trước đó.

Nếu production quản lý Flyway/migration thủ công, người quản lý VPS cần chạy migration theo quy trình triển khai hiện có trước khi test API chapter.

Không chạy lại bằng cách sửa version migration cũ đã được áp dụng.

## 10. Kiểm tra database sau migration

Có thể kiểm tra ba permission:

```sql
SELECT
    name,
    display_name,
    resource
FROM permissions
WHERE name IN (
    'chapters:read',
    'chapters:create',
    'chapters:update'
)
  AND deleted_at IS NULL;
```

Kỳ vọng:

```text
chapters:read
chapters:create
chapters:update
```

Kiểm tra quyền của `ROLE_ADMIN`:

```sql
SELECT
    r.name AS role_name,
    p.name AS permission_name
FROM role_permissions rp
JOIN roles r
    ON r.id = rp.role_id
JOIN permissions p
    ON p.id = rp.permission_id
WHERE r.name = 'ROLE_ADMIN'
  AND p.name IN (
      'chapters:read',
      'chapters:create',
      'chapters:update'
  )
  AND p.deleted_at IS NULL
ORDER BY p.name;
```

Kỳ vọng `ROLE_ADMIN` có đủ ba permission.

---

## 11. Smoke test xuyên service

Sau khi Identity production được deploy và migration đã chạy:

1. Đăng nhập bằng tài khoản admin.
2. Lấy JWT theo luồng đăng nhập production.
3. Gọi API đọc chapter của Content Builder.
4. Gọi API tạo chapter.
5. Gọi API cập nhật chapter.
6. Xác nhận admin không bị từ chối vì thiếu permission.
7. Kiểm tra các role không được cấp quyền chapter vẫn bị giới hạn theo security rule hiện tại.

## 12. Quan hệ với bộ câu hỏi 2026

Trong quy trình bootstrap Content Builder 2026:

```text
Identity
    ↓
JWT / ROLE_ADMIN / chapter permissions
    ↓
Content Builder
    ↓
publish questions
    ↓
publish chapters
```

Các tool portable của Content Builder dùng token hợp lệ từ Identity.

Vì vậy trước khi chạy bước publish chapter trên production, Identity production phải có các permission chapter cần thiết và tài khoản/token thực hiện import phải có quyền phù hợp.

## 13. Những dữ liệu không nên đưa vào Git

Không lưu trong repository:

```text
JWT thật
password database
Redis password
secret production
access token
refresh token
file runtime
log JVM
database dump có thông tin nhạy cảm
```

Các giá trị này thuộc cấu hình/secret management của môi trường triển khai.


## 14. Kết luận

Thay đổi hiện tại của `dts-identity` là thay đổi schema/phân quyền dùng chung, không phải cấu hình local.

Migration:

```text
V8__add_chapter_permissions.sql
```

cần có trên môi trường production để Content Builder sử dụng đầy đủ nghiệp vụ chapter với `ROLE_ADMIN`.

Luồng mục tiêu:

```text
Identity
    ↓
ROLE_ADMIN + chapter permissions
    ↓
JWT / authorization
    ↓
Content Builder chapter APIs
    ↓
Question Bank 2026 deployment
```

