# HƯỚNG DẪN CÁC LỆNH KIỂM THỬ (TEST COMMANDS)
**Dự án**: Bánh Mỳ King (`banhmyking`)  
**Nhánh**: `test/order-cart`  
**Công cụ**: Maven Wrapper (`./mvnw`)

---

## 1. Chạy toàn bộ Test Suite của dự án
Lệnh này sẽ chạy tự động toàn bộ **268 test cases** trong dự án:

```bash
./mvnw test
```

---

## 2. Chạy Test theo từng Acceptance Criteria (AC) của Issue

### AC 1: Snapshot giá (Price Immutability)
Kiểm tra tính bất biến của giá đơn hàng khi giá sản phẩm hoặc option trong Catalog thay đổi:
```bash
./mvnw test -Dtest=OrderPriceSnapshotTest
```

### AC 2: Vòng đời đơn hàng & OrderStatusValidator (State Machine 8x8)
Kiểm tra toàn diện 64 cặp chuyển trạng thái và phân quyền Role:
```bash
./mvnw test -Dtest=OrderStatusValidatorTest
```

### AC 3: Quyền sở hữu đơn hàng (Ownership / IDOR Protection)
Kiểm tra Customer không thể truy cập, hủy, can thiệp hoặc xem thanh toán của khách khác:
```bash
./mvnw test -Dtest=OrderOwnershipTest
```

### AC 4: Độ chính xác tính toán Giá & Phí giao hàng
* Kiểm tra tính tiền giỏ hàng, giảm giá (Percentage, Fixed, Freeship):
```bash
./mvnw test -Dtest=PriceCalculatorTest
```

* Kiểm tra tính phí giao hàng theo km, sàn ngoại thành, và freeship:
```bash
./mvnw test -Dtest=DeliveryFeeCalculatorTest
```

* Chạy đồng thời cả 2 bộ test tính toán:
```bash
./mvnw test -Dtest=PriceCalculatorTest,DeliveryFeeCalculatorTest
```

---

## 3. Chạy nhóm tất cả các Test của Issue `test/order-cart`
Chạy cùng lúc cả 5 file test thuộc phạm vi của issue này:

```bash
./mvnw test -Dtest=OrderPriceSnapshotTest,OrderStatusValidatorTest,OrderOwnershipTest,PriceCalculatorTest,DeliveryFeeCalculatorTest
```

---

## 4. Chạy từng Test Method / Nhóm kịch bản cụ thể (Granular Test)

### 4.1. Trong `OrderPriceSnapshotTest`
* Chỉ chạy test snapshot trong DB:
```bash
./mvnw test -Dtest=OrderPriceSnapshotTest#createOrder_whenProductAndOptionPriceChangedLater_shouldRetainSnapshotPrice
```
* Chỉ chạy test DTO `OrderResponse`:
```bash
./mvnw test -Dtest=OrderPriceSnapshotTest#getOrderByCode_whenProductPriceChangedLater_shouldReturnSnapshotPricesInResponse
```

### 4.2. Trong `OrderStatusValidatorTest`
* Chỉ chạy nhóm ma trận 8x8:
```bash
./mvnw test -Dtest=OrderStatusValidatorTest\$StateTransitionMatrixTests
```
* Chỉ chạy nhóm phân quyền Role khi chuyển trạng thái:
```bash
./mvnw test -Dtest=OrderStatusValidatorTest\$RoleBasedTransitionTests
```
* Chỉ chạy nhóm quy tắc hủy đơn hàng:
```bash
./mvnw test -Dtest=OrderStatusValidatorTest\$CancelOrderValidationTests
```

### 4.3. Trong `OrderOwnershipTest`
* Chỉ chạy test tra cứu chi tiết đơn (`getOrderByCode`):
```bash
./mvnw test -Dtest=OrderOwnershipTest\$GetOrderByCodeOwnershipTests
```
* Chỉ chạy test quyền hủy đơn (`cancelOrder`):
```bash
./mvnw test -Dtest=OrderOwnershipTest\$CancelOrderOwnershipTests
```
* Chỉ chạy test quyền tra cứu thanh toán (`PaymentService`):
```bash
./mvnw test -Dtest=OrderOwnershipTest\$PaymentOwnershipTests
```

### 4.4. Trong `DeliveryFeeCalculatorTest`
* Chỉ chạy test biên khoảng cách (Distance Boundaries):
```bash
./mvnw test -Dtest=DeliveryFeeCalculatorTest\$DistanceBoundaryTests
```
* Chỉ chạy test nhận diện vùng ngoại thành:
```bash
./mvnw test -Dtest=DeliveryFeeCalculatorTest\$SuburbanAreaTests
```
* Chỉ chạy test ngưỡng Freeship (200.000đ):
```bash
./mvnw test -Dtest=DeliveryFeeCalculatorTest\$FreeshipBoundaryTests
```

---

## 5. Lưu ý quan trọng khi chạy trên macOS (zsh Shell)

Nếu bạn sử dụng shell `zsh` và muốn **loại trừ** một test class (bằng ký tự `!`), **KHÔNG** dùng dấu ngoặc kép `""` vì `zsh` sẽ hiểu nhầm là lịch sử lệnh (`event not found`).

* **Đúng (Dùng dấu nháy đơn `''`):**
  ```bash
  ./mvnw test -Dtest='!BanhmykingApplicationTests'
  ```
* **Đúng (Thêm dấu gạch chéo `\!`):**
  ```bash
  ./mvnw test -Dtest="\!BanhmykingApplicationTests"
  ```
* **Sai:**
  ```bash
  # Lệnh này sẽ báo lỗi: zsh: event not found
  ./mvnw test -Dtest="!BanhmykingApplicationTests"
  ```

---

## 6. Xem Báo cáo kiểm thử chi tiết (Test Reports)

Sau khi chạy test, báo cáo chi tiết dạng XML và TXT được lưu tự động tại:
```text
target/surefire-reports/
```

* Xem tóm tắt kết quả:
```bash
cat target/surefire-reports/*.txt | grep -E "Tests run:|FAILURE"
```

* Chạy test với chế độ debug chi tiết khi có lỗi:
```bash
./mvnw test -Dtest=OrderOwnershipTest -X
```
