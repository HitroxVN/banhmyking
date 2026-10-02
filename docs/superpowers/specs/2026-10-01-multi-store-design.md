# Thiết kế: Chuỗi nhiều cơ sở (dự án con A)

- **Ngày:** 2026-10-01
- **Trạng thái:** Đã triển khai
- **Nhánh:** `feature/multi-store` (tách từ `fix/project-review-2026-10-01`, rebase lên `master` sau khi PR fix được merge)
- **Tham chiếu:** mô hình vận hành của bamiking.vn (chuỗi 15 cơ sở, mở 6h30–22h, giao trong 5 km, đơn từ 50k, freeship 0–3 km). Chỉ học cấu trúc/tính năng — **không** dùng logo, ảnh, nội dung hay nhãn hiệu BAMI KING®.

---

## 1. Bối cảnh và mục tiêu

`plan.md` chốt mô hình **một cửa hàng**. Quyết định mới: chuyển sang **chuỗi nhiều cơ sở**. Đây là dự án con **A** trong 4 dự án con:

| | Dự án con | Phụ thuộc |
|---|---|---|
| **A** | Nền tảng chuỗi cơ sở (tài liệu này) | — |
| B | Giá khuyến mãi gạch giá + combo | A |
| C | Hoá đơn đỏ (VAT) | A |
| D | Tin tức, tuyển dụng, form phản hồi | A (tuyển dụng chọn cơ sở) |

**Mục tiêu A:** khách đặt online được phục vụ bởi cơ sở phù hợp; mỗi cơ sở vận hành độc lập (đơn, bếp, shipper, hết món, tồn kho, báo cáo); chủ chuỗi quản lý và xem tổng thể.

### Các quyết định đã chốt

| # | Quyết định |
|---|---|
| Q1 | Đơn **tự gán cơ sở gần nhất đủ điều kiện**, khách **đổi được** sang cơ sở khác đủ điều kiện |
| Q2 | **Thực đơn và giá chung** toàn chuỗi; **hết món và tồn kho riêng** từng cơ sở |
| Q3 | STAFF và SHIPPER thuộc **đúng 1 cơ sở**; thêm vai trò **MANAGER** (quản lý cơ sở); ADMIN = chủ chuỗi |
| Q4 | Kiến trúc: bảng `stores` + cột `store_id` (không multi-tenant, không tách DB) |

### Ngoài phạm vi A
Hẹn giờ giao, khách tự đến lấy (PICKUP), giờ mở cửa khác nhau theo thứ, giá riêng theo cơ sở, thông báo real-time (SSE), chuyển shipper giữa cơ sở.

---

## 2. Mô hình dữ liệu (migration V11)

### 2.1 Bảng mới `stores`

| Cột | Kiểu | Ràng buộc / mặc định | Ghi chú |
|---|---|---|---|
| `id` | BIGINT | PK, auto | |
| `code` | VARCHAR(20) | NOT NULL, UNIQUE | vd `CS01` |
| `name` | VARCHAR(100) | NOT NULL | vd "Cơ sở Trung Hoà" |
| `address` | VARCHAR(500) | NOT NULL | |
| `phone` | VARCHAR(20) | NULL | |
| `latitude`, `longitude` | DECIMAL(9,6) | NULL | NULL = chưa ghim (xem quy tắc 3.4) |
| `open_time`, `close_time` | TIME | NOT NULL, mặc định 06:30 / 22:00 | Cùng giờ mọi ngày; giờ Việt Nam |
| `accepting_orders` | BOOLEAN | NOT NULL, TRUE | Nút "Tạm ngưng nhận đơn" |
| `delivery_radius_km` | DECIMAL(5,2) | NOT NULL, 5 | Theo quãng đường ước tính |
| `free_ship_radius_km` | DECIMAL(5,2) | NOT NULL, 3 | 0 = không freeship theo km |
| `min_order_amount` | DECIMAL(12,2) | NOT NULL, 0 | Tính trên tạm tính món |
| `is_active` | BOOLEAN | NOT NULL, TRUE | Cơ sở đóng cửa hẳn = FALSE |
| `is_deleted` | BOOLEAN | NOT NULL, FALSE | Xoá mềm |
| `created_at`, `updated_at` | DATETIME(6) | | |

CHECK: `delivery_radius_km > 0`, `free_ship_radius_km >= 0`, `min_order_amount >= 0`.

### 2.2 Bảng mới `store_products`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `store_id`, `product_id` | BIGINT | PK ghép; FK tới `stores`, `products` |
| `is_available` | BOOLEAN NOT NULL DEFAULT TRUE | Hết món tại cơ sở |
| `stock_quantity` | INT NULL | NULL = không quản tồn; CHECK `IS NULL OR >= 0` |
| `low_stock_threshold` | INT NOT NULL DEFAULT 5 | |
| `created_at`, `updated_at` | DATETIME(6) | |

**Không có dòng** = món đang bán, không quản tồn tại cơ sở đó. Thêm món mới không cần tạo dữ liệu cho từng cơ sở. Một món bán được tại cơ sở S khi: `products.is_available AND NOT products.is_deleted AND COALESCE(sp.is_available, TRUE)` và tồn (nếu quản) đủ.

### 2.3 Thay đổi bảng có sẵn

| Bảng | Thay đổi |
|---|---|
| `users` | `+ store_id BIGINT NULL` FK. Bắt buộc với STAFF/SHIPPER/MANAGER, luôn NULL với CUSTOMER/ADMIN (kiểm tra ở service) |
| `orders` | `+ store_id BIGINT NOT NULL` FK; index `(store_id, status, created_at)` |
| `inventory_movements` | `+ store_id BIGINT NOT NULL` FK; index `(store_id, product_id, created_at)` |
| `products` | **Bỏ** `stock_quantity`, `low_stock_threshold` và CHECK `chk_products_stock` (chuyển sang `store_products`). `is_available` giữ nghĩa "còn trong thực đơn chuỗi" |
| `site_settings` | Xoá 3 khoá `storeLatitude`, `storeLongitude`, `deliveryMaxRadiusKm` (chuyển thành dữ liệu Cơ sở 1) |

`RoleName` thêm `MANAGER` (cột `users.role` VARCHAR(20) đủ chỗ, không cần đổi schema).

Giữ nguyên: mã đơn `BMK-yyyyMMdd-XXXXX` chung toàn chuỗi; danh mục, món, giá, khuyến mãi chung; ngưỡng freeship theo giá trị đơn (`delivery.freeship-threshold`, 200k) vẫn áp dụng song song với freeship theo km.

---

## 3. Luồng đặt hàng

### 3.1 Báo giá giao hàng — `GET /api/v1/delivery/quote`
Thay cho `GET/POST /delivery/fee`. **Yêu cầu đăng nhập.** Tham số: `latitude`, `longitude` (tuỳ chọn), `shippingAddress`. Giỏ hàng (món, số lượng, tạm tính) **server tự đọc** từ giỏ của người gọi — client không gửi danh sách món/tạm tính nên không thể khai sai.

Trả về:
```json
{
  "recommendedStoreId": 3,
  "options": [
    { "storeId": 3, "storeName": "Cơ sở Láng Hạ", "distanceKm": 2.1,
      "shippingFee": 0, "freeship": true, "eligible": true, "reasons": [] },
    { "storeId": 1, "storeName": "Cơ sở Trung Hoà", "distanceKm": 6.4,
      "eligible": false, "reasons": ["OUT_OF_RADIUS"] }
  ]
}
```

Mã lý do (frontend dịch ra tiếng Việt): `CLOSED` (kèm giờ mở), `NOT_ACCEPTING`, `OUT_OF_RADIUS`, `BELOW_MIN_ORDER` (kèm mức tối thiểu), `ITEM_UNAVAILABLE` (kèm tên món), `INACTIVE` (không trả về cho khách).

**Đề xuất** = cơ sở `eligible` có khoảng cách nhỏ nhất. Không có toạ độ điểm giao → không đề xuất, khách tự chọn trong danh sách cơ sở đang mở.

### 3.2 Tạo đơn — `POST /api/v1/orders`
`CreateOrderRequest` thêm `storeId` (tuỳ chọn; trống → server tự chọn đề xuất). Server **kiểm tra lại toàn bộ** điều kiện ở 3.1 tại thời điểm tạo đơn, không tin client; không đủ → 400 kèm lý do. Đơn lưu `store_id`, `distance_km`, toạ độ (V10).

### 3.3 Phí ship
1. Có khoảng cách → công thức hiện có (`DeliveryFeeCalculator`: 15k tới 2 km, +5k/km).
2. Miễn phí nếu `distance_km <= store.free_ship_radius_km` **hoặc** tạm tính ≥ ngưỡng freeship theo giá trị đơn.
3. Không có khoảng cách (quy tắc 3.4) → tính theo khu vực như hiện nay.

### 3.4 Quy tắc toạ độ
- **Điểm giao không có toạ độ** (địa chỉ cũ): không kiểm tra bán kính, phí theo khu vực, khách tự chọn cơ sở. Giao diện gợi ý ghim vị trí.
- **Cơ sở chưa có toạ độ** (vd Cơ sở 1 sau migration khi quán chưa ghim): coi như phục vụ mọi địa chỉ, phí theo khu vực — giữ nguyên hành vi hiện tại cho tới khi admin ghim. Cơ sở này đứng sau mọi cơ sở có khoảng cách khi xếp đề xuất.

### 3.5 Giờ mở cửa
Mở khi `open_time <= giờ hiện tại (Asia/Ho_Chi_Minh) < close_time`. Không hỗ trợ ca qua nửa đêm trong đợt này (validate `open_time < close_time`).

### 3.6 Kho theo cơ sở
- `ProductRepository.decrementStockAtomic/incrementStockAtomic` chuyển sang `StoreProductRepository` với khoá `(store_id, product_id)`; giữ cơ chế UPDATE nguyên tử và "đủ hết hoặc không trừ" (`decreaseAllOrNothing`), chống hoàn hai lần qua `inventory_movements` (lọc thêm theo `store_id`).
- Trừ khi đơn chuyển CONFIRMED (staff xác nhận, webhook/đối soát tay); hoàn khi CANCELLED/FAILED — luôn trên `order.store`.
- Giỏ hàng **không gắn cơ sở**: `CartServiceImpl` chỉ kiểm tra món còn trong thực đơn chuỗi; kiểm tra hết món/tồn theo cơ sở diễn ra ở báo giá và tạo đơn.

### 3.7 Sau khi đặt
- Hàng đợi bếp, danh sách shipper rảnh, gán shipper: lọc theo `order.store`; shipper được gán phải cùng cơ sở.
- **Chuyển cơ sở** — `PUT /api/v1/admin/orders/{code}/store` `{storeId, reason}`: chỉ đơn `PENDING`; ADMIN, hoặc MANAGER của cơ sở hiện tại; cơ sở đích phải đủ **mọi** điều kiện như 3.2, kể cả đang mở cửa và đang nhận đơn. Tính lại phí ship và tổng tiền theo cơ sở đích; nếu đơn đã thanh toán và tổng tiền thay đổi → từ chối (tránh lệch tiền). Ghi `order_status_history` (from = to = PENDING, note "Chuyển từ CSx sang CSy: lý do").
- `OrderResponse` thêm `storeId`, `storeName`, `storePhone`.

---

## 4. Phân quyền

| Việc | ADMIN | MANAGER | STAFF | SHIPPER |
|---|---|---|---|---|
| Xem/xử lý đơn | Mọi cơ sở | Cơ sở mình | Cơ sở mình | Đơn được gán |
| Gán shipper | Mọi cơ sở | Cơ sở mình | Cơ sở mình | – |
| Chuyển đơn sang cơ sở khác | ✓ | Đơn cơ sở mình | – | – |
| Hết món / nhập, điều chỉnh tồn | Mọi cơ sở | Cơ sở mình | Cơ sở mình | – |
| Sửa thực đơn chung (món, giá, danh mục, ảnh) | ✓ | – | – | – |
| Tạm ngưng / mở lại nhận đơn | Mọi cơ sở | Cơ sở mình | – | – |
| Sửa thông tin cơ sở | ✓ | – | – | – |
| Tạo / xoá cơ sở | ✓ | – | – | – |
| Tài khoản | Tất cả | Xem nhân viên cơ sở mình | – | – |
| Báo cáo, dashboard | Toàn chuỗi + lọc cơ sở | Cơ sở mình | – | – |
| Khuyến mãi, cài đặt website | ✓ | – | – | – |

**Thay đổi hành vi:** STAFF hiện sửa được thực đơn chung — sau A chỉ ADMIN sửa; STAFF chỉ bật/tắt hết món và tồn kho tại cơ sở mình.

### Cài đặt
- `StoreAccessGuard` (service dùng chung):
  - `Set<Long> allowedStoreIds(User actor)` — ADMIN: tất cả (biểu diễn bằng "không giới hạn"); MANAGER/STAFF: `{actor.storeId}`; SHIPPER: `{actor.storeId}` (chỉ dùng cho danh sách, đơn vẫn lọc `shipper = actor`).
  - `Order requireOrderAccess(User actor, Order order)` — ngoài phạm vi → `ResourceNotFoundException` (404, không lộ đơn tồn tại).
  - `void requireStoreAccess(User actor, Long storeId)`.
- Thay các kiểm tra `role == STAFF || ADMIN` rải rác trong `OrderServiceImpl`, `PaymentServiceImpl`, `OrderStatusValidator`, `InventoryServiceImpl`, `ReviewController` bằng guard; MANAGER được coi như STAFF ở mọi chỗ vận hành.
- `OrderSpecifications.withFilters`, các truy vấn báo cáo/dashboard, `UserRepository.findByRoleAndDeletedFalse` (shipper rảnh), sổ kho nhận thêm tham số `storeIds` (null = không giới hạn).
- `SecurityConfig`: `/admin/orders/**`, `/shipper/**` thêm MANAGER; thêm `/api/v1/manager/**` → MANAGER, ADMIN; `/api/v1/stores` GET public; `/api/v1/admin/stores/**` → ADMIN. `/catalog/**` ghi → **chỉ ADMIN** (trừ endpoint hết món/tồn kho chuyển sang `/api/v1/store-inventory/**` → STAFF, MANAGER, ADMIN).
- Tạo/sửa tài khoản STAFF/SHIPPER/MANAGER bắt buộc `storeId`; đổi cơ sở hoặc vai trò → thu hồi refresh token. Đổi về CUSTOMER/ADMIN → `store_id = NULL`.
- Không xoá được cơ sở còn nhân viên hoặc còn đơn chưa kết thúc.

---

## 5. Giao diện

### Khách hàng
- **`/stores` — Hệ thống cửa hàng** (public): bản đồ ghim mọi cơ sở (dùng lại Leaflet), danh sách: tên, địa chỉ, ☎, giờ, nhãn *Đang mở / Đã đóng / Tạm ngưng nhận đơn*, nút "Chỉ đường"; nút "Tìm cơ sở gần tôi" (GPS) sắp xếp theo khoảng cách. Link ở menu đầu trang và footer.
- **Thanh toán:** thẻ "Giao từ cơ sở" — cơ sở đề xuất, khoảng cách, phí, nút **Đổi** (danh sách kèm lý do không chọn được); khoá nút Đặt hàng khi không cơ sở nào phục vụ.
- **Theo dõi đơn:** "Cơ sở phục vụ: … · ☎ …".
- **Footer:** hotline + "N cơ sở → Xem hệ thống cửa hàng".

### ADMIN
- **Trang "Cơ sở"** (`/admin/stores`): bảng (mã, tên, địa chỉ, giờ, trạng thái, số nhân viên); form thêm/sửa dùng `AddressMapPicker` + giờ, bán kính, freeship, đơn tối thiểu, ☎, hoạt động.
- Bộ lọc **Cơ sở** ở Đơn hàng, Báo cáo, Dashboard; báo cáo thêm **doanh thu theo cơ sở**.
- Tài khoản: cột + bộ lọc Cơ sở; ô chọn cơ sở khi vai trò STAFF/SHIPPER/MANAGER.
- Cài đặt website: bỏ mục "Vị trí cửa hàng & giao hàng".

### Khu `/staff` (STAFF, MANAGER, ADMIN — menu theo vai trò)
- Đầu trang: tên cơ sở đang làm việc; ADMIN có ô chọn cơ sở.
- STAFF: Hàng đợi đơn; **Tình trạng món** (bật/tắt hết món, nhập tồn) thay trang sửa thực đơn.
- MANAGER: thêm **Báo cáo cơ sở**, **Nhân viên** (chỉ xem), nút **Tạm ngưng nhận đơn**.

### Shipper
Đầu trang hiện tên cơ sở; còn lại giữ nguyên.

---

## 6. Migration V11–V13 và dữ liệu cũ

Chia 3 migration để ứng dụng chạy được sau mỗi bước triển khai (xem kế hoạch): **V11** = bước 1–6 (cột `store_id` mới còn NULL được), **V12** = bỏ tồn kho ở `products` + siết `inventory_movements.store_id` NOT NULL, **V13** = siết `orders.store_id` NOT NULL + xoá 3 khoá vị trí quán. Thứ tự tổng thể:
1. Tạo `stores`, `store_products`.
2. Tạo **Cơ sở 1** (`CS01`, "Cơ sở 1"): `address` = `site_settings.contactAddress` (rỗng → "Chưa cập nhật địa chỉ"), toạ độ = `storeLatitude/storeLongitude` (rỗng → NULL), `delivery_radius_km` = `deliveryMaxRadiusKm` (rỗng/không hợp lệ → 5), giờ 06:30–22:00, freeship 3 km, **đơn tối thiểu 0**.
3. `orders.store_id`: thêm NULL → gán Cơ sở 1 → NOT NULL + FK + index.
4. `users.store_id`: gán Cơ sở 1 cho mọi STAFF và SHIPPER.
5. `store_products`: chép `(Cơ sở 1, product_id, TRUE, stock_quantity, low_stock_threshold)` cho món có `stock_quantity IS NOT NULL`.
6. `inventory_movements.store_id`: gán Cơ sở 1 → NOT NULL.
7. Bỏ CHECK `chk_products_stock`, bỏ cột `products.stock_quantity`, `products.low_stock_threshold`.
8. Xoá 3 khoá vị trí quán khỏi `site_settings`.

**Rủi ro:** V12 xoá cột → hướng dẫn team **sao lưu DB trước khi pull**:
```
mysqldump -h 127.0.0.1 -P 3307 -u root -p banhmyking > backup_truoc_V11.sql
```

**Dữ liệu demo (chỉ profile dev, `DataInitializer`):** 3 cơ sở mẫu tại Hà Nội (toạ độ thật của các phường, tên chung chung "Cơ sở Cầu Giấy/Đống Đa/Hà Đông"), `manager@gmail.com` (MANAGER, cơ sở 1), gán staff/shipper demo vào cơ sở.

---

## 7. Kiểm thử

**Unit (Mockito):**
- `StoreSelectionService`: gần nhất được đề xuất; đóng cửa; tạm ngưng; ngoài bán kính; dưới đơn tối thiểu; hết món / thiếu tồn; điểm giao không toạ độ; cơ sở không toạ độ; không cơ sở nào đủ điều kiện.
- Phí ship: freeship theo km (biên đúng bằng bán kính), theo giá trị đơn, cả hai.
- `StoreAccessGuard`: từng vai trò × đơn cùng / khác cơ sở → được / 404.
- Kho: trừ/hoàn đúng `(store, product)`; không đủ hàng ở cơ sở này nhưng cơ sở khác đủ.
- Tạo đơn: `storeId` không đủ điều kiện → 400; trống → tự chọn.
- Chuyển cơ sở: chỉ PENDING; MANAGER khác cơ sở → 404; đơn đã thanh toán mà đổi tổng tiền → từ chối.
- Tài khoản: STAFF thiếu `storeId` → 400; đổi cơ sở → thu hồi token.

**Tích hợp (DB thật):** chạy V11 trên **bản sao** DB dev có sẵn đơn/tồn/nhân viên; kiểm tra gán Cơ sở 1, Hibernate `validate` khớp; các test `@SpringBootTest` hiện có vẫn pass.

**HTTP (smoke) với 2 cơ sở:** staff A không thấy đơn B (404); đặt đơn khi cơ sở đóng/hết món bị chặn kèm lý do; manager tạm ngưng → báo giá đề xuất cơ sở khác; báo cáo lọc theo cơ sở đúng số.

**Frontend:** `tsc -b`, `npm run build`, lint; danh sách bấm thử giao diện cho người duyệt.

---

## 8. Hướng dẫn team khi pull

1. Sao lưu DB (lệnh ở mục 6).
2. `git pull`, chạy backend → Flyway áp V11–V13 (log `now at version v13`).
3. `cd frontend && npm install && npm run dev`.
4. ADMIN vào **Cơ sở**: kiểm tra Cơ sở 1, ghim vị trí, đặt đơn tối thiểu; thêm các cơ sở khác.
5. ADMIN vào **Tài khoản**: gán cơ sở cho từng STAFF/SHIPPER, tạo MANAGER.

---

## 9. Rủi ro và câu hỏi còn mở

| Rủi ro | Giảm thiểu |
|---|---|
| V12 xoá cột tồn kho cũ | Sao lưu bắt buộc; chạy thử trên bản sao trước |
| Bỏ quyền sửa thực đơn của STAFF làm gãy thói quen team | Ghi rõ trong PR; trang "Tình trạng món" thay thế |
| ~15 chỗ kiểm tra quyền rải rác — sót một chỗ là lộ đơn cơ sở khác | Gom về `StoreAccessGuard`; test 404 cho từng endpoint đơn hàng |
| Nominatim/OSM giới hạn tốc độ khi trang `/stores` có nhiều người xem | Trang `/stores` chỉ tải tile bản đồ, không gọi tra địa chỉ |
| Polling 5s × nhiều cơ sở tăng tải | Truy vấn hàng đợi có index `(store_id, status, created_at)`; SSE để đợt sau |
