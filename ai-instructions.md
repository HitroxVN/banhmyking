# BANHMYKING BACKEND — SYSTEM RULES

## 1. Công nghệ & Kiến trúc
- Stack: Spring Boot 4.1.1 (Java 17/21), MySQL 8.0, Flyway.
- Mô hình 3 tầng: Controller -> Service -> Repository.
- Luồng dữ liệu: Controller (DTO) <-> Service (Mapper, Business Logic) <-> Repository (Entity).

## 2. Quy tắc lập trình bắt buộc
- Controller: Không chứa business logic; không gọi trực tiếp Repository; validate input qua `@Valid`; chỉ nhận/trả DTO bọc trong `ResponseEntity<ApiResponse<T>>`.
- Service: Bắt buộc dùng Interface + Impl (`XxxService` / `XxxServiceImpl`); gắn `@Transactional` cho tác vụ ghi; Entity tuyệt đối KHÔNG lọt ra Controller.
- Repository: Tất cả quan hệ `@ManyToOne`, `@OneToOne` phải đặt `fetch = FetchType.LAZY`; chống lỗi N+1 bằng `@EntityGraph` hoặc `JOIN FETCH`.

## 3. Quy chuẩn dữ liệu & Database
- Khóa chính & Tiền tệ: PK dùng `BIGINT AUTO_INCREMENT`; tiền dùng `BigDecimal` / `DECIMAL(12,2)` (Scale 2, `HALF_UP`).
- Enum & Audit: Enum lưu dạng `VARCHAR` (`EnumType.STRING`); kế thừa `BaseEntity` (`created_at`, `updated_at`).
- Xóa mềm (Soft Delete): Dùng cột `is_deleted` cho `products`, `categories`, `users`.
- Snapshot đơn hàng: Bắt buộc snapshot giá (`unit_price`), tên món, option và địa chỉ giao tại thời điểm tạo đơn vào `orders` và `order_items`.

## 4. Bảo mật & Phân quyền
- Auth: Stateless JWT + Refresh Token (lưu hash trong `refresh_tokens`).
- Role: 1 user có 1 role trong bảng `users` (`CUSTOMER`, `STAFF`, `SHIPPER`, `ADMIN`).
- Phân quyền: Endpoint cho STAFF dùng `hasAnyRole('STAFF', 'ADMIN')`; dùng `@PreAuthorize` ở tầng Service.
- Ownership: Luôn lấy `userId` từ `SecurityContextHolder`, không dùng `userId` truyền từ Request Body.

## 5. Chuẩn Response
- Success Envelope: `{ "success": true, "message": "...", "data": {...}, "timestamp": "..." }`
- Error Envelope: `{ "success": false, "message": "...", "errorCode": "...", "errors": {...}, "timestamp": "..." }`
- Pagination: Dùng `PageResponse<T>` gồm `content`, `page`, `size`, `totalElements`, `totalPages`, `last`.