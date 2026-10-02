# Thiết kế: Giá khuyến mãi gạch giá + Combo (dự án con B)

- **Ngày:** 2026-10-02
- **Trạng thái:** Đã triển khai
- **Nhánh:** `feature/combo-sale` (tạo từ `feature/multi-store` — B phụ thuộc A)
- **Phụ thuộc:** dự án con A — `docs/superpowers/specs/2026-10-01-multi-store-design.md`

## 1. Bối cảnh và mục tiêu

Hiện mỗi món chỉ có một giá (`products.price`) cộng giá topping; ưu đãi duy nhất là mã giảm giá trên cả đơn. Dự án con B thêm hai công cụ bán hàng theo kiểu các chuỗi (bamiking.vn):

1. **Giá khuyến mãi gạch giá** cho món lẻ, có thời gian áp dụng.
2. **Combo cố định**: gói nhiều món lẻ với số lượng cố định, giá rẻ hơn mua lẻ.

### Quyết định đã chốt

| # | Quyết định |
|---|---|
| B1 | Giá KM đặt **trên từng món lẻ**, kèm khoảng thời gian (từ–đến, bỏ trống = không giới hạn phía đó); **chung toàn chuỗi** |
| B2 | Combo **cố định** món + số lượng; không có lựa chọn, không có topping |
| B3 | Mã giảm giá **được cộng dồn** với giá KM và combo (tính trên tạm tính đã theo giá KM/combo) |
| B4 | Kiến trúc: **combo là một `Product` loại `COMBO`** + bảng thành phần — tái dùng danh mục, ảnh, giỏ, đơn, đánh giá, báo cáo, bật/tắt theo cơ sở |

### Ngoài phạm vi B
Combo có lựa chọn ("chọn 1 đồ uống bất kỳ"); giá KM riêng theo cơ sở; chương trình KM theo cả danh mục; khung giờ vàng lặp hằng ngày; topping trong combo; combo lồng combo.

## 2. Mô hình dữ liệu (migration V14)

### 2.1 `products` — thêm cột
| Cột | Kiểu | Ghi chú |
|---|---|---|
| `product_type` | `VARCHAR(20) NOT NULL DEFAULT 'SINGLE'` | `SINGLE` \| `COMBO`; dữ liệu cũ = `SINGLE` |
| `sale_price` | `DECIMAL(12,2) NULL` | chỉ cho `SINGLE`; phải `> 0` và `< price` |
| `sale_starts_at` | `DATETIME NULL` | giờ Việt Nam (`TimeConfig.VIETNAM`); NULL = áp dụng ngay |
| `sale_ends_at` | `DATETIME NULL` | NULL = không hết hạn; nếu cả hai có thì `ends > starts` |

Ràng buộc nghiệp vụ kiểm ở service (không dùng CHECK của DB để giữ thông báo lỗi tiếng Việt).

### 2.2 Bảng mới `combo_items`
| Cột | Kiểu | Ghi chú |
|---|---|---|
| `combo_id` | `BIGINT NOT NULL` FK → `products.id` | sản phẩm loại `COMBO` |
| `component_id` | `BIGINT NOT NULL` FK → `products.id` | sản phẩm loại `SINGLE` |
| `quantity` | `INT NOT NULL` | 1–20 |
| PK | (`combo_id`, `component_id`) | một món xuất hiện tối đa 1 dòng trong combo |

### 2.3 `order_items` — thêm cột
| Cột | Kiểu | Ghi chú |
|---|---|---|
| `original_unit_price` | `DECIMAL(12,2) NULL` | giá gốc 1 đơn vị (chưa gồm topping) lúc đặt. NULL ở đơn cũ = không có ưu đãi |

`unit_price` giữ nghĩa hiện tại: giá bán 1 đơn vị **chưa gồm topping** (nay = giá hiệu lực). `line_total` không đổi nghĩa.

### 2.4 Bảng mới `order_item_components`
| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `BIGINT` PK auto | |
| `order_item_id` | `BIGINT NOT NULL` FK → `order_items.id` (xoá theo) | |
| `product_id` | `BIGINT NULL` FK → `products.id` | NULL nếu món sau này bị xoá cứng |
| `product_name` | `VARCHAR(255) NOT NULL` | tên lúc đặt |
| `quantity` | `INT NOT NULL` | số lượng **trong 1 combo** |

## 3. Quy tắc giá

Tập trung trong một lớp `ProductPricing` (component, inject `Clock`), là **nguồn sự thật duy nhất**; `PriceCalculator`, `CartServiceImpl`, `OrderServiceImpl`, `DeliveryController`, `ProductResponse` đều gọi qua nó.

- `isSaleActive(p, now)` = `p` là `SINGLE` ∧ `salePrice != null` ∧ (`saleStartsAt == null` ∨ `now ≥ saleStartsAt`) ∧ (`saleEndsAt == null` ∨ `now < saleEndsAt`).
- `effectivePrice(p, now)` = `salePrice` nếu `isSaleActive`, ngược lại `price` (combo: `price`).
- `originalPrice(p)`:
  - `SINGLE` = `price`;
  - `COMBO` = Σ(`component.price` × `quantity`) — **giá gốc** của thành phần, không dùng giá KM của thành phần.
- `compareAtPrice(p, now)` = `originalPrice` nếu `originalPrice > effectivePrice`, ngược lại `null` (không gạch giá).
- `discountPercent` = làm tròn xuống của `(compareAt − effective) / compareAt × 100`; chỉ hiển thị.
- **Đơn giá dòng giỏ** = `effectivePrice` + Σ `extraPrice` topping (combo không có topping).
- **Thời điểm chốt giá** = lúc tạo đơn (`now` từ `Clock`), không phải lúc thêm vào giỏ. Giỏ luôn tính theo giá hiện tại.
- **Mã giảm giá** (B3): `calculateSubtotal` dùng đơn giá trên; `minOrderAmount` và `PERCENTAGE` xét trên tạm tính này.
- **Tiết kiệm của dòng** = (`originalPrice` − `effectivePrice`) × `quantity` (≥ 0); **tiết kiệm giỏ** = tổng các dòng.
- Bỏ phần công thức lặp trong `PriceCalculator.calculateSubtotal` — dùng `unitPriceOf`.

### Ràng buộc khi lưu (400, thông báo tiếng Việt)
- `sale_price` phải `> 0` và `< price`; `sale_ends_at > sale_starts_at` khi có cả hai.
- Combo: không nhận `salePrice`/`saleStartsAt`/`saleEndsAt`, không nhận `options`/`optionGroups`.
- Combo: ≥ 1 dòng thành phần và tổng số phần (Σ quantity) ≥ 2; thành phần phải là `SINGLE`, chưa xoá; không trùng món.
- Combo: `price < originalPrice` (tổng giá lẻ) — nếu không thì combo vô nghĩa.
- Không đổi `product_type` của sản phẩm đã tồn tại.

## 4. Tồn kho, hết món theo cơ sở

### 4.1 Combo còn bán tại cơ sở S khi
1. combo `available` ∧ chưa xoá ∧ `COALESCE(store_products.available, TRUE)` của **chính combo** tại S (manager báo hết combo riêng được);
2. **mọi thành phần** bán được tại S (quy tắc món lẻ hiện có của A);
3. tồn kho đủ cho **nhu cầu gộp**.

Combo **không có tồn kho riêng**: dòng `store_products` của combo chỉ dùng cột `available`; API điều chỉnh tồn từ chối combo (400).

### 4.2 Nhu cầu gộp theo món thành phần
Một hàm dùng chung `ComboExpander.expand(Map<Product,Integer> lines)` → `Map<Product,Integer>` theo **món lẻ**: dòng `SINGLE` giữ nguyên; dòng `COMBO` × q → mỗi thành phần × (`quantity` × q); cộng dồn cùng món. Dùng ở:
- `InventoryService.unavailableItems(storeId, …)` (báo giá, tạo đơn, chuyển cơ sở) — kiểm cả combo (điều kiện 1) lẫn thành phần (điều kiện 2–3); tên trả về là tên món/combo thiếu, kèm thành phần gây thiếu (vd "Combo Sáng no nê (hết Cà phê sữa đá)");
- `InventoryServiceImpl.decreaseAllOrNothing` — trừ theo map đã gộp, all-or-nothing như cũ, ghi `InventoryMovement` (reason `ORDER`) **theo từng món lẻ**;
- `restoreForOrder` không đổi — đã chạy theo nhật ký movement nên tự hoàn đúng thành phần.

### 4.3 Mức chuỗi (danh sách thực đơn)
`ProductResponse.available` của combo = combo `available` ∧ mọi thành phần `available` và chưa xoá. Thêm cửa hàng/giỏ: từ chối combo không còn bán (như món lẻ).

## 5. Luồng đơn hàng

- **Tạo đơn:** dòng combo → 1 `OrderItem` (`product` = combo, `unitPrice` = giá combo, `originalUnitPrice` = tổng giá lẻ thành phần) + các `OrderItemComponent` chụp tên/số lượng thành phần. Dòng món lẻ: `unitPrice` = giá hiệu lực, `originalUnitPrice` = `price` khi đang KM, ngược lại = `unitPrice`.
- **Xác nhận (trừ kho), huỷ/giao lỗi (hoàn kho), chuyển cơ sở, webhook thanh toán:** như mục 4.2 — không thêm luồng riêng.
- **Sửa thực đơn sau khi đặt:** đơn cũ giữ snapshot giá và thành phần.

### Sửa/xoá món lẻ đang nằm trong combo
- **Tắt** (`available=false`) hoặc báo hết tại cơ sở: combo chứa nó tự "Tạm hết" — không chặn.
- **Xoá** (soft delete): **chặn** 400 "Món đang nằm trong combo: …" (liệt kê tên combo chưa xoá). Admin sửa/xoá combo trước.
- Xoá combo: soft delete như món thường; `combo_items` giữ nguyên (phục vụ lịch sử).

## 6. API

### 6.1 Catalog
- `ProductResponse` thêm: `productType`, `salePrice`, `saleStartsAt`, `saleEndsAt`, `effectivePrice`, `compareAtPrice` (null = không gạch giá), `discountPercent` (null nếu không giảm), `onSale` (bool, chỉ món lẻ đang trong thời gian KM), `comboItems: [{productId, name, imageUrl, price, quantity}]` (rỗng với món lẻ). `price` giữ nguyên nghĩa giá gốc.
- `ProductRequest` thêm: `productType` (chỉ khi tạo; mặc định `SINGLE`), `salePrice`, `saleStartsAt`, `saleEndsAt`, `comboItems: [{productId, quantity}]`.
- `GET /api/v1/catalog/products` thêm tham số `onSale=true` (món lẻ đang KM) và `type=SINGLE|COMBO`.
- Ghi (tạo/sửa/xoá) vẫn chỉ ADMIN như A.

### 6.2 Giỏ hàng
Mỗi dòng thêm `originalUnitPrice`, `productType`, `comboItems` (tóm tắt); `unitPrice` = giá hiệu lực + topping. Giỏ thêm `savingsAmount`.

### 6.3 Đơn hàng
`OrderItemResponse` thêm `originalUnitPrice`, `components: [{productName, quantity}]`; `OrderResponse` thêm `savingsAmount` (tính từ các dòng).

### 6.4 Tồn kho cơ sở
`StoreStockResponse` thêm `productType` và `blockedBy: [tên món]` (thành phần đang làm combo không bán được tại cơ sở). `PATCH …/stock` với combo → 400.

### 6.5 Báo cáo
`GET /api/v1/admin/reports/price-savings?from&to&storeId` (ADMIN) → `{ amount, orderCount }`: Σ (`original_unit_price` − `unit_price`) × `quantity` trên đơn `DELIVERED` trong khoảng; dòng có `original_unit_price` NULL bỏ qua. Manager xem được số của cơ sở mình qua `/api/v1/manager/reports/price-savings?from&to`.

Top món và theo danh mục: combo tính như một món (gộp theo `product_id`), thuộc danh mục của combo — không đổi truy vấn.

## 7. Giao diện

### Admin — `/admin/menu`
- Form món lẻ: thêm *Giá khuyến mãi*, *Bắt đầu*, *Kết thúc* (`datetime-local`); danh sách hiện nhãn **Đang KM −x%** / **Sắp KM** / **KM đã hết**.
- Tab **Combo**: danh sách combo; form tạo/sửa gồm tên, danh mục, ảnh, mô tả, nổi bật, giá combo, bảng thành phần (chọn món lẻ chưa xoá, số lượng 1–20, thêm/xoá dòng). Dòng tóm tắt "Tổng giá lẻ X → Giá combo Y (tiết kiệm Z%)"; chặn lưu phía client khi Y ≥ X (server vẫn kiểm).

### Khách
- `ProductCard`: nhãn **−x%** + giá hiệu lực + ~~giá gốc~~; combo: nhãn **Combo** + "Gồm: …".
- `MenuPage`: chip lọc **Đang khuyến mãi**.
- `ProductDetailPage`: gạch giá, "KM đến dd/MM HH:mm" nếu có hạn; combo liệt kê thành phần (link sang từng món), không có khối topping.
- `CartPage`, `CheckoutPage`: đơn giá có gạch giá; dòng **Bạn tiết kiệm được** ở phần tổng.
- `OrderTrackingPage`, danh sách đơn: gạch giá + thành phần combo theo snapshot.

### Nhân viên / quản lý
- Hàng đợi bếp, trang shipper: dòng combo kèm thành phần.
- `StoreStockPage`: combo chỉ có nút bật/tắt, không có nhập tồn; hiện "Tạm hết do: …" từ `blockedBy`.
- `ManagerReportsPage`, `AdminReportsPage`: ô **Tiền ưu đãi từ giá KM và combo**.

## 8. Kiểm thử
- Unit `ProductPricing`: trước/sau/đúng mốc `saleStartsAt`, `saleEndsAt`; NULL hai phía; combo `originalPrice`, `compareAtPrice` null khi không rẻ hơn.
- Unit `ComboExpander`: gộp combo + món lẻ cùng thành phần.
- `InventoryServiceTest`: trừ/hoàn kho đơn có combo; thiếu một thành phần → không trừ gì; combo bị manager tắt tại cơ sở.
- Validation catalog: các ràng buộc mục 3; chặn xoá món đang trong combo.
- `OrderServiceTest`: snapshot `originalUnitPrice` và thành phần; mã giảm giá cộng dồn trên tạm tính đã giảm.
- Báo cáo `price-savings` có lọc cơ sở.
- Migration V14 trên bản sao DB; smoke HTTP + chụp giao diện bằng Chrome như A.

## 9. Rủi ro
- **Lệch giờ** giữa server và trình duyệt khi hiển thị KM: server là nguồn sự thật (`effectivePrice` trong response); client chỉ hiển thị.
- **Hiệu năng**: thực đơn tải thêm thành phần combo — dùng fetch join/`@EntityGraph`, thực đơn nhỏ.
- **Đơn cũ** (`original_unit_price` NULL) hiển thị như không có ưu đãi; không backfill.
