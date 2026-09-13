# BẢNG ĐẶC TẢ CHI TIẾT TEST CASE (TEST SPECIFICATION)

- **Dự án**: Bánh Mỳ King (`banhmyking`)
- **Nhánh**: `test/order-cart`
- **Mô tả Issue**: Viết unit và integration test bảo vệ các luồng tính tiền, vòng đời đơn hàng và quyền sở hữu (ownership).
- **Framework & Công cụ**: JUnit 5 (Jupiter), Mockito, AssertJ, Spring Boot Test, Maven Surefire Plugin.
- **Tổng số test cases**: 139 test cases (thuộc 5 test class chính của issue).
- **Kết quả thực tế**: **139/139 PASSED (100%)** (Toàn bộ project: 268/268 PASSED).

---

## MỤC LỤC
1. [AC 1: Snapshot giá (Price Immutability)](#1-ac-1-snapshot-giá-price-immutability)
2. [AC 2: State Machine 8x8 & OrderStatusValidator](#2-ac-2-state-machine-8x8--orderstatusvalidator)
3. [AC 3: Quyền sở hữu đơn hàng (Ownership / IDOR Protection)](#3-ac-3-quyền-sở-hữu-đơn-hàng-ownership--idor-protection)
4. [AC 4: Độ chính xác tính toán PriceCalculator & DeliveryFeeCalculator](#4-ac-4-độ-chính-xác-tính-toán-pricecalculator--deliveryfeecalculator)
5. [Thống kê tổng hợp](#5-thống-kê-tổng-hợp)

---

## 1. AC 1: Snapshot giá (Price Immutability)
* **File kiểm thử**: [`OrderPriceSnapshotTest.java`](file:///Users/huangocthinh/Library/Mobile%20Documents/com~apple~CloudDocs/Workspace/banhmyking/src/test/java/com/banhmyking/banhmyking/service/OrderPriceSnapshotTest.java)
* **Mục tiêu**: Đảm bảo khi giá sản phẩm hoặc option trong Catalog thay đổi, các đơn hàng đã tạo trong quá khứ vẫn giữ nguyên giá trị đã chốt (Snapshot).

| Test Case ID | Tên kịch bản | Loại test | Tiền điều kiện | Dữ liệu đầu vào (Input) | Các bước thực hiện (Steps) | Kết quả kỳ vọng (Expected Output) | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-SNAP-01** | Bất biến giá Order & OrderItem trong Database khi giá Product/Option tăng | Integration | Database đã có Product (35.000đ), Option (10.000đ), Cart có 2 phần | - Product ID: 1, giá cũ: 35.000đ<br>- Option ID: 1, giá cũ: 10.000đ<br>- Số lượng: 2 | 1. Gọi `orderService.createOrderFromCart()`.<br>2. Sửa trực tiếp `product.setPrice(60.000đ)` và `option.setExtraPrice(25.000đ)` trong DB.<br>3. Query lại Order và OrderItem từ DB. | - `OrderItem.unitPrice` = 45.000đ<br>- `OrderItem.lineTotal` = 90.000đ<br>- `Order.subtotal` = 90.000đ<br>- `Order.total` = 105.000đ (kèm ship 15k) | **PASSED** |
| **TC-SNAP-02** | DTO `OrderResponse` và `OrderItemResponse` giữ nguyên giá trị snapshot | Integration | Đơn hàng đã chốt ở TC-SNAP-01, Catalog đã cập nhật giá mới | Mã đơn: `BMK-20260912-SNAP1`, User ID: 1 | 1. Gọi `orderService.getOrderByCode(userId, orderCode)`.<br>2. Kiểm tra dữ liệu trả về trong DTO `OrderResponse`. | - `OrderResponse.getTotal()` = 105.000đ<br>- `OrderItemResponse.getUnitPrice()` = 45.000đ<br>- Dữ liệu không bị recalculate theo giá 60k/25k | **PASSED** |

---

## 2. AC 2: State Machine 8x8 & OrderStatusValidator
* **File kiểm thử**: [`OrderStatusValidatorTest.java`](file:///Users/huangocthinh/Library/Mobile%20Documents/com~apple~CloudDocs/Workspace/banhmyking/src/test/java/com/banhmyking/banhmyking/validator/OrderStatusValidatorTest.java) *(63 test cases)*
* **Mục tiêu**: Kiểm tra ma trận chuyển trạng thái 8x8 và phân quyền vai trò (Customer, Shipper, Staff, Admin) khi chuyển trạng thái hoặc hủy đơn.

### 2.1. Ma trận chuyển đổi 8x8 (State Transition Matrix)
Danh sách 8 trạng thái: `PENDING`, `CONFIRMED`, `PREPARING`, `READY_FOR_PICKUP`, `DELIVERING`, `DELIVERED`, `CANCELLED`, `REFUNDED`.

| Test Case ID | Trạng thái hiện tại | Trạng thái mong muốn | Vai trò (Role) | Kết quả kỳ vọng | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-STM-01** | PENDING | CONFIRMED | STAFF / ADMIN | Chuyển trạng thái thành công | **PASSED** |
| **TC-STM-02** | PENDING | CANCELLED | CUSTOMER / STAFF / ADMIN | Cho phép hủy đơn ở PENDING (kèm lý do) | **PASSED** |
| **TC-STM-03** | PENDING | PREPARING / DELIVERING / DELIVERED | Bất kỳ | Lỗi 400 `BUSINESS_ERROR`: Không được nhảy cóc giai đoạn | **PASSED** |
| **TC-STM-04** | CONFIRMED | PREPARING | STAFF / ADMIN | Chuyển trạng thái thành công | **PASSED** |
| **TC-STM-05** | CONFIRMED | CANCELLED | STAFF / ADMIN | Quán/Admin được phép hủy khi đã xác nhận | **PASSED** |
| **TC-STM-06** | PREPARING | READY_FOR_PICKUP | STAFF / ADMIN | Chuẩn bị xong món, chờ shipper lấy hàng | **PASSED** |
| **TC-STM-07** | PREPARING | CANCELLED | STAFF / ADMIN | Quán hủy đơn trong lúc chuẩn bị (hết món, sự cố) | **PASSED** |
| **TC-STM-08** | READY_FOR_PICKUP | DELIVERING | SHIPPER / STAFF / ADMIN | Shipper nhận hàng bắt đầu giao | **PASSED** |
| **TC-STM-09** | READY_FOR_PICKUP | CANCELLED | STAFF / ADMIN | Cho phép hủy nếu không có shipper nhận | **PASSED** |
| **TC-STM-10** | DELIVERING | DELIVERED | SHIPPER / STAFF / ADMIN | Giao hàng thành công | **PASSED** |
| **TC-STM-11** | DELIVERING | CANCELLED | STAFF / ADMIN | Hủy do giao hàng thất bại (khách bom, không liên lạc được) | **PASSED** |
| **TC-STM-12** | DELIVERED | REFUNDED | STAFF / ADMIN | Cho phép hoàn tiền cho đơn đã giao nếu có khiếu nại | **PASSED** |
| **TC-STM-13** | DELIVERED | PENDING / CONFIRMED / DELIVERING | Bất kỳ | Lỗi 400 `BUSINESS_ERROR`: Trạng thái kết thúc, không quay lui | **PASSED** |
| **TC-STM-14** | CANCELLED | Bất kỳ trạng thái khác | Bất kỳ | Lỗi 400 `BUSINESS_ERROR`: Đơn đã hủy là trạng thái đóng | **PASSED** |
| **TC-STM-15** | REFUNDED | Bất kỳ trạng thái khác | Bất kỳ | Lỗi 400 `BUSINESS_ERROR`: Đơn đã hoàn tiền là trạng thái đóng | **PASSED** |
| **TC-STM-16** | X (bất kỳ) | X (chính trạng thái hiện tại) | Bất kỳ | No-op: Cho phép cập nhật trùng trạng thái không ném lỗi | **PASSED** |

### 2.2. Kiểm tra phân quyền Vai trò (Role-based Validation)

| Test Case ID | Thao tác | Vai trò thực hiện | Kết quả kỳ vọng | Trạng thái |
| :--- | :--- | :--- | :--- | :--- |
| **TC-ROLE-01** | Chuyển PENDING $\rightarrow$ CONFIRMED | CUSTOMER | Lỗi 403 `FORBIDDEN`: Khách không được tự duyệt đơn | **PASSED** |
| **TC-ROLE-02** | Chuyển PENDING $\rightarrow$ CONFIRMED | SHIPPER | Lỗi 403 `FORBIDDEN`: Shipper không có quyền duyệt đơn | **PASSED** |
| **TC-ROLE-03** | Chuyển READY_FOR_PICKUP $\rightarrow$ DELIVERING | CUSTOMER | Lỗi 403 `FORBIDDEN`: Khách không được đổi trạng thái giao | **PASSED** |
| **TC-ROLE-04** | Chuyển DELIVERING $\rightarrow$ DELIVERED | CUSTOMER | Lỗi 403 `FORBIDDEN`: Khách không được tự nhận đã giao | **PASSED** |
| **TC-ROLE-05** | Hủy đơn tại CONFIRMED / PREPARING | CUSTOMER | Lỗi 403 `FORBIDDEN`: Khách không được hủy khi quán đã nhận làm | **PASSED** |
| **TC-ROLE-06** | Hủy đơn tại PENDING không nhập lý do | CUSTOMER | Lỗi 400 `BAD_REQUEST`: Bắt buộc nhập lý do hủy đơn | **PASSED** |
| **TC-ROLE-07** | Input validation: `currentStatus == null` | Bất kỳ | Lỗi 400 `BAD_REQUEST`: Trạng thái hiện tại không hợp lệ | **PASSED** |
| **TC-ROLE-08** | Input validation: `nextStatus == null` | Bất kỳ | Lỗi 400 `BAD_REQUEST`: Trạng thái tiếp theo không hợp lệ | **PASSED** |
| **TC-ROLE-09** | Input validation: `userRole == null` | Bất kỳ | Lỗi 403 `FORBIDDEN`: Không xác định được vai trò người dùng | **PASSED** |

---

## 3. AC 3: Quyền sở hữu đơn hàng (Ownership / IDOR Protection)
* **File kiểm thử**: [`OrderOwnershipTest.java`](file:///Users/huangocthinh/Library/Mobile%20Documents/com~apple~CloudDocs/Workspace/banhmyking/src/test/java/com/banhmyking/banhmyking/service/OrderOwnershipTest.java) *(14 test cases)*
* **Mục tiêu**: Ngăn chặn hoàn toàn lỗ hổng IDOR (Insecure Direct Object Reference) - Khách hàng không thể xem, hủy, can thiệp hoặc tra cứu thông tin thanh toán của khách hàng khác.

| Test Case ID | Chức năng kiểm thử | Tác nhân (Actor) | Đối tượng mục tiêu | Kết quả kỳ vọng | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-OWN-01** | Xem chi tiết đơn (`getOrderByCode`) | Customer A (ID: 10) | Đơn hàng thuộc sở hữu Customer A | Thành công 200 OK, trả về thông tin chi tiết đơn | **PASSED** |
| **TC-OWN-02** | Xem chi tiết đơn (`getOrderByCode`) | Customer B (ID: 20) | Đơn hàng thuộc sở hữu Customer A | Ném `BusinessException` (403 `FORBIDDEN`: Không có quyền truy cập) | **PASSED** |
| **TC-OWN-03** | Xem chi tiết đơn (`getOrderByCode`) | Nhân viên Quán (STAFF) | Đơn hàng thuộc sở hữu Customer A | Thành công 200 OK (Quyền xem đơn để chế biến) | **PASSED** |
| **TC-OWN-04** | Xem chi tiết đơn (`getOrderByCode`) | Quản trị viên (ADMIN) | Đơn hàng thuộc sở hữu Customer A | Thành công 200 OK (Toàn quyền hệ thống) | **PASSED** |
| **TC-OWN-05** | Xem lịch sử đơn (`getOrderStatusHistory`) | Customer A (ID: 10) | Đơn hàng thuộc sở hữu Customer A | Thành công, trả về danh sách lịch sử chuyển trạng thái | **PASSED** |
| **TC-OWN-06** | Xem lịch sử đơn (`getOrderStatusHistory`) | Customer B (ID: 20) | Đơn hàng thuộc sở hữu Customer A | Ném `BusinessException` (403 `FORBIDDEN`) | **PASSED** |
| **TC-OWN-07** | Hủy đơn hàng (`cancelOrder`) | Customer A (ID: 10) | Đơn PENDING của chính mình | Hủy thành công, trạng thái chuyển sang `CANCELLED` | **PASSED** |
| **TC-OWN-08** | Hủy đơn hàng (`cancelOrder`) | Customer B (ID: 20) | Đơn PENDING của Customer A | Ném `BusinessException` (403 `FORBIDDEN`: Không thể hủy đơn người khác) | **PASSED** |
| **TC-OWN-09** | Đổi trạng thái (`updateOrderStatus`) | Customer A (ID: 10) | Đơn hàng của Customer A | Ném `BusinessException` (403 `FORBIDDEN`: Khách không có quyền đổi) | **PASSED** |
| **TC-OWN-10** | Phân công shipper (`assignShipper`) | Customer A (ID: 10) | Đơn hàng của Customer A | Ném `BusinessException` (403 `FORBIDDEN`: Khách không có quyền gán shipper) | **PASSED** |
| **TC-OWN-11** | Xác nhận giao hàng (`confirmDelivery`) | Shipper khác (ID: 60) | Đơn gán cho Shipper 50 | Ném `BusinessException` (403 `FORBIDDEN`: Không phải shipper được phân công) | **PASSED** |
| **TC-OWN-12** | Tra cứu thanh toán (`getPaymentByOrderCode`) | Customer A (ID: 10) | Đơn hàng thuộc sở hữu Customer A | Thành công, trả về thông tin thanh toán (COD, PENDING) | **PASSED** |
| **TC-OWN-13** | Tra cứu thanh toán (`getPaymentByOrderCode`) | Customer B (ID: 20) | Đơn hàng thuộc sở hữu Customer A | Ném `BusinessException` (403 `FORBIDDEN`) | **PASSED** |
| **TC-OWN-14** | Tra cứu thanh toán (`getPaymentByOrderCode`) | Admin (ID: 40) | Đơn hàng thuộc sở hữu Customer A | Thành công 200 OK | **PASSED** |

---

## 4. AC 4: Độ chính xác tính toán PriceCalculator & DeliveryFeeCalculator

### 4.1. PriceCalculator
* **File kiểm thử**: [`PriceCalculatorTest.java`](file:///Users/huangocthinh/Library/Mobile%20Documents/com~apple~CloudDocs/Workspace/banhmyking/src/test/java/com/banhmyking/banhmyking/service/PriceCalculatorTest.java) *(21 test cases)*

| Test Case ID | Phân nhóm | Dữ liệu đầu vào (Input) | Kết quả kỳ vọng (Expected Output) | Trạng thái |
| :--- | :--- | :--- | :--- | :--- |
| **TC-CALC-01** | Subtotal đơn lẻ | 1 món: (30k + 8k) x 2 phần | `subtotal` = 76.000đ, `shipping` = 15.000đ, `total` = 91.000đ | **PASSED** |
| **TC-CALC-02** | Subtotal nhiều món | Món 1: 76.000đ + Món 2: (25k + 5k) x 3 = 90.000đ | `subtotal` = 166.000đ, `shipping` = 15.000đ, `total` = 181.000đ | **PASSED** |
| **TC-CALC-03** | Giỏ hàng rỗng / null | Cart không có item hoặc Cart null | `subtotal` = 0đ, `total` = 15.000đ (phí ship) | **PASSED** |
| **TC-CALC-04** | Giá trị null an toàn | Item quantity null, Product/Option price null | Quantity fallback 1, Price fallback 0đ, không lỗi NPE | **PASSED** |
| **TC-CALC-05** | Phí ship tùy biến | Phí ship truyền vào: 25.000đ | `shippingFee` = 25.000đ, `total` = 76.000 + 25.000 = 101.000đ | **PASSED** |
| **TC-CALC-06** | Phí ship 0đ (Freeship) | Phí ship truyền vào: 0đ | `shippingFee` = 0đ, `total` = 76.000đ | **PASSED** |
| **TC-CALC-07** | Phí ship null | Phí ship truyền vào: null | Tự động fallback về `DEFAULT_SHIPPING_FEE` = 15.000đ | **PASSED** |
| **TC-CALC-08** | Giảm % có trần (Cap) | 10% của 76.000đ = 7.600đ; Trần tối đa = 5.000đ | `discountAmount` = 5.000đ, `total` = 86.000đ | **PASSED** |
| **TC-CALC-09** | Giảm % không trần | 10% của 76.000đ = 7.600đ; Trần = null | `discountAmount` = 7.600đ, `total` = 83.400đ | **PASSED** |
| **TC-CALC-10** | Giảm 100% | 100% của 76.000đ | `discountAmount` = 76.000đ, `total` = 15.000đ (còn phí ship) | **PASSED** |
| **TC-CALC-11** | Giảm số tiền cố định | Giảm 20.000đ trên đơn 76.000đ | `discountAmount` = 20.000đ, `total` = 71.000đ | **PASSED** |
| **TC-CALC-12** | Giảm tiền vượt Subtotal | Giảm 100.000đ trên đơn 76.000đ | `discountAmount` chặn ở 76.000đ, `total` = 15.000đ (không âm tiền) | **PASSED** |
| **TC-CALC-13** | Giảm phí ship (FREE_SHIP) | Voucher ship 30.000đ trên phí ship 15.000đ | `discountAmount` chặn ở 15.000đ, `total` = 76.000đ | **PASSED** |
| **TC-CALC-14** | Giảm phí ship 1 phần | Voucher ship 10.000đ trên phí ship 15.000đ | `discountAmount` = 10.000đ, `total` = 81.000đ | **PASSED** |
| **TC-CALC-15** | Validate: Mã inactive | `promotion.active = false` | Ném `BusinessException`: "Mã khuyến mãi hiện không kích hoạt" | **PASSED** |
| **TC-CALC-16** | Validate: Chưa đến hạn | `startsAt` ở tương lai | Ném `BusinessException`: "Mã khuyến mãi chưa đến thời gian áp dụng" | **PASSED** |
| **TC-CALC-17** | Validate: Quá hạn | `endsAt` ở quá khứ | Ném `BusinessException`: "Mã khuyến mãi đã hết hạn sử dụng" | **PASSED** |
| **TC-CALC-18** | Validate: Hết lượt dùng | `usedCount >= maxUsage` | Ném `BusinessException`: "Mã khuyến mãi đã hết lượt sử dụng" | **PASSED** |
| **TC-CALC-19** | Biên `minOrderAmount` bằng | Subtotal = 76.000đ, minOrder = 76.000đ | Áp dụng thành công mã giảm giá | **PASSED** |
| **TC-CALC-20** | Biên `minOrderAmount` thiếu 1đ | Subtotal = 76.000đ, minOrder = 76.001đ | Ném `BusinessException`: "chưa đạt giá trị tối thiểu" | **PASSED** |

### 4.2. DeliveryFeeCalculator
* **File kiểm thử**: [`DeliveryFeeCalculatorTest.java`](file:///Users/huangocthinh/Library/Mobile%20Documents/com~apple~CloudDocs/Workspace/banhmyking/src/test/java/com/banhmyking/banhmyking/service/DeliveryFeeCalculatorTest.java) *(39 test cases)*

| Test Case ID | Phân nhóm | Dữ liệu đầu vào (Input) | Kết quả kỳ vọng (Expected Output) | Trạng thái |
| :--- | :--- | :--- | :--- | :--- |
| **TC-DELV-01** | Khoảng cách < 2km | 1.5 km, Nội thành | Phí = 15.000đ (Phí cơ bản trong 2km) | **PASSED** |
| **TC-DELV-02** | Khoảng cách = 2.0km | 2.0 km (chạm biên), Nội thành | Phí = 15.000đ | **PASSED** |
| **TC-DELV-03** | Khoảng cách 2.0001km | 2.0001 km (vượt 0.0001km $\rightarrow$ ceil = 1km) | 15.000 + 1 x 5.000 = 20.000đ | **PASSED** |
| **TC-DELV-04** | Khoảng cách = 3.0km | 3.0 km (vượt 1.0km $\rightarrow$ ceil = 1km) | 15.000 + 1 x 5.000 = 20.000đ | **PASSED** |
| **TC-DELV-05** | Khoảng cách 3.001km | 3.001 km (vượt 1.001km $\rightarrow$ ceil = 2km) | 15.000 + 2 x 5.000 = 25.000đ | **PASSED** |
| **TC-DELV-06** | Khoảng cách 7.5km | 7.5 km (vượt 5.5km $\rightarrow$ ceil = 6km) | 15.000 + 6 x 5.000 = 45.000đ | **PASSED** |
| **TC-DELV-07** | Khoảng cách null / <= 0 | distance = null hoặc 0 km | Fallback tính theo khu vực: Nội thành = 15.000đ | **PASSED** |
| **TC-DELV-08** | Nhận diện 15 huyện HN | Gia Lâm, Đông Anh, Sóc Sơn, Thanh Trì, Hoài Đức, Đan Phượng, Thạch Thất, Quốc Oai, Thường Tín, Phú Xuyên, Mê Linh, Ba Vì, Sơn Tây, Ứng Hòa, Mỹ Đức | Khu vực: `SUBURBAN` | **PASSED** |
| **TC-DELV-09** | Nhận diện 7 quận/huyện TP.HCM & ven | Hóc Môn, Bình Chánh, Nhà Bè, Củ Chi, Cần Giờ, Bình Dương, Đồng Nai | Khu vực: `SUBURBAN` | **PASSED** |
| **TC-DELV-10** | Không dấu & Viết hoa/thường | "XA DA TON, HUYEN GIA LAM", "dong anh ha noi" | Khu vực: `SUBURBAN` | **PASSED** |
| **TC-DELV-11** | Địa chỉ nội thành | "Quận Hoàn Kiếm", "Quận 1 TP.HCM", null, rỗng | Khu vực: `INNER_CITY` | **PASSED** |
| **TC-DELV-12** | Sàn ngoại thành (không km) | Ngoại thành, distance = null | Áp dụng sàn ngoại thành = 30.000đ | **PASSED** |
| **TC-DELV-13** | Sàn ngoại thành (gần 1.5km) | 1.5 km, Ngoại thành (phí tính 15k < sàn 30k) | Lấy max(15k, 30k) = 30.000đ | **PASSED** |
| **TC-DELV-14** | Sàn ngoại thành (4.0km) | 4.0 km, Ngoại thành (phí tính 25k < sàn 30k) | Lấy max(25k, 30k) = 30.000đ | **PASSED** |
| **TC-DELV-15** | Phí khoảng cách vượt sàn (10km) | 10.0 km, Ngoại thành (phí tính 55k > sàn 30k) | Lấy max(55k, 30k) = 55.000đ | **PASSED** |
| **TC-DELV-16** | Freeship cận dưới 199.999đ | Subtotal = 199.999đ (< 200.000đ) | `isFreeship = false`, Phí ship thu đủ | **PASSED** |
| **TC-DELV-17** | Freeship chạm mốc 200.000đ | Subtotal = 200.000đ | `isFreeship = true`, Phí ship = 0đ | **PASSED** |
| **TC-DELV-18** | Freeship đơn ngoại thành | Subtotal = 250.000đ, Ngoại thành (gốc 55k) | `isFreeship = true`, Phí ship = 0đ | **PASSED** |
| **TC-DELV-19** | Subtotal null | Subtotal = null | `isFreeship = false`, Phí ship tính bình thường | **PASSED** |

---

## 5. Thống kê tổng hợp

```text
================================================================================
KẾT QUẢ KIỂM THỬ NHÁNH test/order-cart
================================================================================
- OrderPriceSnapshotTest (AC 1)         : 2 / 2 tests passed       (100%)
- OrderStatusValidatorTest (AC 2)       : 63 / 63 tests passed     (100%)
- OrderOwnershipTest (AC 3)             : 14 / 14 tests passed     (100%)
- PriceCalculatorTest (AC 4)            : 21 / 21 tests passed     (100%)
- DeliveryFeeCalculatorTest (AC 4)      : 39 / 39 tests passed     (100%)
--------------------------------------------------------------------------------
TỔNG CỘNG TEST CASES CỦA ISSUE          : 139 / 139 tests passed   (100%)
TỔNG CỘNG TEST SUITE TOÀN BỘ PROJECT    : 268 / 268 tests passed   (100%)
================================================================================
```
