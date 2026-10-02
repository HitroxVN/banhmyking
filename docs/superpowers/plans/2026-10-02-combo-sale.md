# Giá khuyến mãi gạch giá + Combo (dự án con B) — Kế hoạch triển khai

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm giá khuyến mãi có thời hạn cho món lẻ (hiển thị gạch giá) và combo cố định (một `Product` loại `COMBO` gồm nhiều món lẻ), đi xuyên suốt thực đơn → giỏ → đơn → tồn kho theo cơ sở → báo cáo.

**Architecture:** Migration V14 thêm cột giá KM + `product_type` vào `products`, bảng `combo_items`, cột `order_items.original_unit_price` và bảng snapshot `order_item_components`. Mọi phép tính giá đi qua một bean duy nhất `ProductPricing` (inject `Clock`); mọi phép tính nhu cầu tồn kho đi qua một hàm tĩnh `ComboExpander.expand` (combo × q → thành phần × quantity × q, cộng dồn theo món lẻ). Combo tái dùng danh mục, ảnh, giỏ, đơn, đánh giá, bật/tắt theo cơ sở của A; combo không có tồn riêng.

**Tech Stack:** Java 17, Spring Boot 4.1, Hibernate 7 (`ddl-auto=validate`), Flyway, MariaDB 10.4 (XAMPP cổng 3307) / MySQL 8, JUnit 5 + Mockito + AssertJ; React 19 + TypeScript + Vite, oxlint.

**Spec:** `docs/superpowers/specs/2026-10-02-combo-sale-design.md`

## Global Constraints

- **Agent KHÔNG chạy `git add/commit/push/stash/checkout/reset`.** Mỗi bước "Commit (người dùng tự chạy)" chỉ là gợi ý lệnh để **người dùng** tự chạy; message tiếng Việt, không thêm dòng Co-Authored-By.
- Làm trên nhánh `feature/combo-sale` (tạo từ `feature/multi-store`).
- File Java của repo dùng CRLF: sửa bằng công cụ Edit/Write, **không dùng `sed -i`**. File mới viết bằng Write được chấp nhận (`core.autocrlf=true` chuẩn hoá khi commit).
- `spring.jpa.hibernate.ddl-auto=validate`: entity phải khớp migration (tên cột, kiểu, có/không có cột).
- **Chỉ một migration mới: `V14__combo_sale.sql`.** Không sửa V1–V13. SQL phải chạy trên **MariaDB 10.4** (và MySQL 8) và giữ dữ liệu cũ hợp lệ: món cũ = `SINGLE`, không KM; dòng đơn cũ `original_unit_price = NULL` (= không có ưu đãi, không backfill).
- Ràng buộc nghiệp vụ kiểm ở service, **không dùng CHECK của DB**, để giữ thông báo lỗi tiếng Việt.
- Lỗi nghiệp vụ: `throw new BusinessException(ErrorCode.VALIDATION_ERROR | BUSINESS_ERROR, "<tiếng Việt>")` (cả hai → HTTP 400); không tìm thấy → `ResourceNotFoundException` (404).
- Thời gian: inject `java.time.Clock` (bean ở `config/TimeConfig`, múi giờ `TimeConfig.VIETNAM`). Code mới **không** gọi `LocalDateTime.now()` không kèm clock. `sale_starts_at`/`sale_ends_at` là giờ Việt Nam.
- Giá: `DECIMAL(12,2)` / `BigDecimal`. `products.price` giữ nghĩa giá gốc; `order_items.unit_price` = giá bán 1 đơn vị **chưa gồm topping** (nay = giá hiệu lực); `line_total` không đổi nghĩa.
- `ProductPricing` là nguồn sự thật duy nhất của giá (spec §3); `ComboExpander.expand(Map<Product,Integer>)` là hàm duy nhất gộp nhu cầu theo món lẻ (spec §4.2).
- Mã giảm giá **cộng dồn** với giá KM/combo (B3): `minOrderAmount` và `PERCENTAGE` xét trên tạm tính đã theo giá hiệu lực.
- Phạm vi cơ sở của A (`StoreAccessGuard`) giữ nguyên: ghi thực đơn chỉ ADMIN; `/store-inventory/**` STAFF/MANAGER cơ sở mình, ADMIN mọi cơ sở; `/manager/**` khoá về cơ sở của người gọi.
- Lệnh test backend (Git Bash, thư mục gốc, luôn `clean`): `./mvnw -B clean test -Dtest=<TênClass>`; toàn bộ: `./mvnw -B clean test` (cần MariaDB XAMPP cổng 3307 đang chạy cho test tích hợp `@SpringBootTest`).
- Cổng kiểm frontend (không có framework test FE), trong `frontend/`: `npx tsc -b` (0 lỗi), `npm run build` (`✓ built`), `npx oxlint src` (không thêm cảnh báo mới so với trước khi sửa).
- Ngoài phạm vi (KHÔNG làm): combo có lựa chọn, giá KM theo cơ sở, KM theo danh mục, khung giờ vàng lặp, topping trong combo, combo lồng combo.

## Làm rõ spec (chốt khi lập kế hoạch)

1. `order_item_components` thêm `created_at`/`updated_at` (entity kế thừa `BaseEntity` như `order_item_options`); FK `product_id` dùng `ON DELETE SET NULL` để đúng ý "NULL nếu món bị xoá cứng".
2. `ProductResponse.available` của combo là giá trị suy diễn (spec §4.3) nên thêm `enabled` = cờ `is_available` của chính sản phẩm; nút bật/tắt và form sửa của admin dùng `enabled`.
3. `salePrice` bỏ trống khi lưu = hết KM (xoá luôn hai mốc thời gian); vì vậy nút bật/tắt nhanh ở admin gửi kèm giá KM hiện có. `comboItems` bỏ trống khi sửa combo = giữ nguyên thành phần (vẫn kiểm giá combo < tổng giá lẻ).
4. Spec §6.4 ghi `PATCH …/stock`; endpoint thật là `POST /api/v1/store-inventory/{storeId}/products/{productId}/stock` → combo bị từ chối ở `InventoryService.adjustStock` (400).
5. Spec §6.5 ghi `from&to`; dùng `fromDate&toDate` cho khớp các endpoint báo cáo hiện có. Khoảng ngày lọc theo `orders.created_at` (như mọi báo cáo khác); `orderCount` = số đơn đã giao có ít nhất một dòng ưu đãi.
6. Trừ kho khi xác nhận đơn dùng thành phần **hiện tại** của combo qua `ComboExpander.expand` (spec §4.2); snapshot `order_item_components` chỉ để hiển thị/lịch sử. Hoàn kho đi theo sổ ORDER nên luôn đúng.
7. `original_unit_price` snapshot = `max(originalPrice, unitPrice)` để tiền tiết kiệm không âm khi combo đắt hơn tổng giá lẻ (giá thành phần giảm sau khi tạo combo).
8. Lọc khoảng giá và sắp xếp theo giá trên thực đơn vẫn dùng cột `price` (giá gốc) — không đổi hành vi hiện có.
9. Kiểm "đang khuyến mãi" ở thực đơn dùng `ProductPricing.now()` (Clock); `PriceCalculator.validatePromotion` giữ `LocalDateTime.now()` của code cũ (ngoài phạm vi B).
10. Sửa giá món lẻ làm combo chứa nó không còn rẻ hơn tổng giá lẻ: không chặn — combo chỉ mất gạch giá (`compareAtPrice = null`) và lần sửa combo kế tiếp sẽ bị kiểm lại.

## Bản đồ file

Gốc backend: `M = src/main/java/com/banhmyking/banhmyking/`, `T = src/test/java/com/banhmyking/banhmyking/`.

**Backend — tạo mới**

| File | Trách nhiệm |
|---|---|
| `src/main/resources/db/migration/V14__combo_sale.sql` | Cột KM + loại SP, `combo_items`, `original_unit_price`, `order_item_components` |
| `M/enums/ProductType.java` | `SINGLE`, `COMBO` |
| `M/entity/ComboItem.java`, `M/entity/ComboItemId.java` | Thành phần combo (khoá ghép combo + món) |
| `M/entity/OrderItemComponent.java` | Snapshot tên/số lượng thành phần của dòng combo |
| `M/repository/ComboItemRepository.java` | Ghi thành phần; tìm combo chưa xoá chứa một món |
| `M/service/ProductPricing.java` | Giá hiệu lực / giá gốc / giá gạch / % giảm / đơn giá dòng giỏ |
| `M/service/ComboExpander.java` | Gộp nhu cầu theo món lẻ; combo còn bán ở mức chuỗi |
| `M/dto/catalog/ComboItemRequest.java`, `M/dto/catalog/ComboItemResponse.java` | DTO thành phần combo |
| `M/dto/order/OrderItemComponentResponse.java` | DTO snapshot thành phần trong đơn |
| `M/dto/report/PriceSavingsResponse.java` | `{ amount, orderCount }` |

**Backend — sửa:** `Product`, `OrderItem`, `PriceCalculator`, `CatalogService(+Impl)`, `CatalogController`, `ProductSpecifications`, `ProductRequest`, `ProductResponse`, `CartServiceImpl`, `CartItemResponse`, `CartResponse`, `OrderServiceImpl`, `OrderItemResponse`, `OrderResponse`, `DeliveryController`, `InventoryServiceImpl`, `StoreStockResponse`, `OrderItemRepository`, `AdminReportService(+Impl)`, `AdminReportController`, `ManagerController`.

**Frontend — tạo mới** (gốc `frontend/src/`): `utils/pricing.ts`, `components/product/PriceTag.tsx`, `components/product/ComboContents.tsx`, `styles/components/pricing.css`.

**Frontend — sửa:** `types/staff.ts`, `types/cart.ts`, `types/order.ts`, `types/store.ts`, `types/admin.ts`, `api/catalogApi.ts`, `api/staffCatalogApi.ts`, `api/adminReportsApi.ts`, `api/managerApi.ts`, `context/cartContextDef.ts`, `context/CartProvider.tsx`, `components/product/ProductCard.tsx`, `pages/MenuPage.tsx`, `pages/ProductDetailPage.tsx`, `pages/CartPage.tsx`, `pages/CheckoutPage.tsx`, `pages/OrderTrackingPage.tsx`, `pages/OrdersPage.tsx`, `pages/staff/StaffOrderQueuePage.tsx`, `pages/shipper/ShipperOrdersPage.tsx`, `pages/staff/StaffMenuPage.tsx` (route `/admin/menu`), `pages/staff/StoreStockPage.tsx`, `pages/admin/AdminReportsPage.tsx`, `pages/manager/ManagerReportsPage.tsx`, `styles/components/staff-menu.css`.

---

## Task 1: Schema V14 + entity + repository thành phần combo

**Files:**
- Create: `src/main/resources/db/migration/V14__combo_sale.sql`
- Create: `M/enums/ProductType.java`, `M/entity/ComboItemId.java`, `M/entity/ComboItem.java`, `M/entity/OrderItemComponent.java`, `M/repository/ComboItemRepository.java`
- Modify: `M/entity/Product.java`, `M/entity/OrderItem.java`
- Test: `T/repository/ComboSchemaIntegrationTest.java`

**Interfaces:**
- Consumes: entity `Category`, `Store`, `User`, `Order`, repository `CategoryRepository`, `ProductRepository`, `StoreRepository`, `UserRepository`, `OrderRepository` (đã có).
- Produces:
  - `enum ProductType { SINGLE, COMBO }`.
  - `Product`: `getProductType()/setProductType(ProductType)` (mặc định `SINGLE`), `getSalePrice()/setSalePrice(BigDecimal)`, `getSaleStartsAt()/setSaleStartsAt(LocalDateTime)`, `getSaleEndsAt()/setSaleEndsAt(LocalDateTime)`, `getComboItems()/setComboItems(List<ComboItem>)` (phía đọc, `mappedBy = "combo"`), `boolean isCombo()`.
  - `ComboItemId(Long comboId, Long componentId)` (Lombok getter/setter, equals/hashCode, no-args + all-args).
  - `ComboItem`: `id (ComboItemId)`, `combo (Product)`, `component (Product)`, `int quantity`.
  - `OrderItem`: `getOriginalUnitPrice()/setOriginalUnitPrice(BigDecimal)`, `getComponents(): List<OrderItemComponent>` (cascade ALL từ OrderItem).
  - `OrderItemComponent extends BaseEntity`: `orderItem`, `product` (nullable), `productName`, `Integer quantity`.
  - `ComboItemRepository extends JpaRepository<ComboItem, ComboItemId>`: `List<String> findActiveComboNamesContaining(Long componentId)`.

- [ ] **Step 1: Viết test tích hợp (đỏ)**

`T/repository/ComboSchemaIntegrationTest.java`:

```java
package com.banhmyking.banhmyking.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.banhmyking.banhmyking.entity.Category;
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.OrderItemComponent;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.ProductType;
import com.banhmyking.banhmyking.enums.RoleName;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** V14 trên DB dev thật (giống các test repository khác) — mọi thay đổi rollback sau test. */
@SpringBootTest
@Transactional
class ComboSchemaIntegrationTest {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ComboItemRepository comboItemRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void migrationKeepsExistingProductsValid() {
        Integer invalidType = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE product_type NOT IN ('SINGLE', 'COMBO')", Integer.class);
        assertThat(invalidType).isZero();
        Integer combosOfLegacyRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE product_type = 'COMBO' AND sale_price IS NOT NULL", Integer.class);
        assertThat(combosOfLegacyRows).isZero();
    }

    @Test
    void savesComboWithComponentsAndFindsActiveCombosOfAComponent() {
        String tag = tag();
        Category category = categoryRepository.save(category(tag));
        Product banhMi = productRepository.save(product(category, "Bánh mì V14 " + tag, "30000", ProductType.SINGLE));
        Product coffee = productRepository.save(product(category, "Cà phê V14 " + tag, "20000", ProductType.SINGLE));
        Product combo = productRepository.save(product(category, "Combo V14 " + tag, "60000", ProductType.COMBO));
        comboItemRepository.saveAll(List.of(comboItem(combo, banhMi, 1), comboItem(combo, coffee, 2)));
        entityManager.flush();
        entityManager.clear();

        Product loaded = productRepository.findById(combo.getId()).orElseThrow();
        assertThat(loaded.isCombo()).isTrue();
        assertThat(loaded.getComboItems())
                .extracting(item -> item.getComponent().getName(), ComboItem::getQuantity)
                .containsExactlyInAnyOrder(tuple("Bánh mì V14 " + tag, 1), tuple("Cà phê V14 " + tag, 2));
        assertThat(comboItemRepository.findActiveComboNamesContaining(coffee.getId()))
                .containsExactly("Combo V14 " + tag);

        loaded.setDeleted(true);
        entityManager.flush();
        assertThat(comboItemRepository.findActiveComboNamesContaining(coffee.getId())).isEmpty();
    }

    @Test
    void savesSaleWindowAndOrderItemSnapshot() {
        String tag = tag();
        Category category = categoryRepository.save(category(tag));
        Product banhMi = product(category, "Bánh mì KM " + tag, "30000", ProductType.SINGLE);
        banhMi.setSalePrice(new BigDecimal("25000"));
        banhMi.setSaleStartsAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        banhMi.setSaleEndsAt(LocalDateTime.of(2026, 10, 31, 22, 0));
        banhMi = productRepository.save(banhMi);

        Store store = new Store();
        store.setCode("V14" + tag);
        store.setName("Cơ sở V14 " + tag);
        store.setAddress("Địa chỉ thử nghiệm");
        store = storeRepository.save(store);
        User customer = new User();
        customer.setEmail("v14-" + tag.toLowerCase() + "@test.local");
        customer.setPassword("not-used");
        customer.setFullName("Khách V14");
        customer.setRole(RoleName.CUSTOMER);
        customer = userRepository.save(customer);

        Order order = new Order();
        order.setOrderCode("V14-" + tag);
        order.setUser(customer);
        order.setStore(store);
        order.setStatus(OrderStatus.PENDING);
        order.setReceiverName("Khách thử");
        order.setReceiverPhone("0900000000");
        order.setShippingAddress("1 Đường Thử");
        order.setSubtotal(new BigDecimal("45000"));
        order.setTotal(new BigDecimal("45000"));
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(banhMi);
        item.setProductName("Combo snapshot");
        item.setUnitPrice(new BigDecimal("45000"));
        item.setOriginalUnitPrice(new BigDecimal("50000"));
        item.setQuantity(1);
        item.setLineTotal(new BigDecimal("45000"));
        OrderItemComponent component = new OrderItemComponent();
        component.setOrderItem(item);
        component.setProduct(banhMi);
        component.setProductName("Bánh mì KM " + tag);
        component.setQuantity(2);
        item.getComponents().add(component);
        order.getItems().add(item);
        order = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        Product reloaded = productRepository.findById(banhMi.getId()).orElseThrow();
        assertThat(reloaded.getProductType()).isEqualTo(ProductType.SINGLE);
        assertThat(reloaded.getSalePrice()).isEqualByComparingTo("25000");
        assertThat(reloaded.getSaleEndsAt()).isEqualTo(LocalDateTime.of(2026, 10, 31, 22, 0));
        OrderItem loadedItem = orderRepository.findById(order.getId()).orElseThrow().getItems().get(0);
        assertThat(loadedItem.getOriginalUnitPrice()).isEqualByComparingTo("50000");
        assertThat(loadedItem.getComponents()).singleElement().satisfies(c -> {
            assertThat(c.getProductName()).isEqualTo("Bánh mì KM " + tag);
            assertThat(c.getQuantity()).isEqualTo(2);
        });
    }

    private static String tag() {
        return Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    private static Category category(String tag) {
        Category category = new Category();
        category.setName("Danh mục V14 " + tag);
        return category;
    }

    private static Product product(Category category, String name, String price, ProductType type) {
        Product product = new Product();
        product.setCategory(category);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setProductType(type);
        return product;
    }

    private static ComboItem comboItem(Product combo, Product component, int quantity) {
        ComboItem item = new ComboItem();
        item.setId(new ComboItemId(combo.getId(), component.getId()));
        item.setCombo(combo);
        item.setComponent(component);
        item.setQuantity(quantity);
        return item;
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=ComboSchemaIntegrationTest`
Expected: COMPILATION ERROR (`ProductType`, `ComboItem`, `ComboItemId`, `OrderItemComponent`, `ComboItemRepository` chưa tồn tại).

- [ ] **Step 3: Migration `V14__combo_sale.sql`**

```sql
-- V14: giá khuyến mãi gạch giá + combo (spec docs/superpowers/specs/2026-10-02-combo-sale-design.md §2)
-- Chỉ thêm cột/bảng nên dữ liệu cũ hợp lệ ngay: món hiện có = SINGLE, chưa KM;
-- dòng đơn cũ original_unit_price = NULL (hiển thị như không có ưu đãi, không backfill).
-- Không dùng CHECK: ràng buộc nghiệp vụ kiểm ở service để giữ thông báo tiếng Việt.

ALTER TABLE products
    ADD COLUMN product_type   VARCHAR(20)    NOT NULL DEFAULT 'SINGLE' AFTER category_id,
    ADD COLUMN sale_price     DECIMAL(12, 2) NULL AFTER price,
    ADD COLUMN sale_starts_at DATETIME       NULL AFTER sale_price,
    ADD COLUMN sale_ends_at   DATETIME       NULL AFTER sale_starts_at,
    ADD INDEX idx_products_type (product_type);

CREATE TABLE combo_items (
    combo_id     BIGINT NOT NULL,
    component_id BIGINT NOT NULL,
    quantity     INT    NOT NULL,
    PRIMARY KEY (combo_id, component_id),
    CONSTRAINT fk_combo_items_combo FOREIGN KEY (combo_id) REFERENCES products (id),
    CONSTRAINT fk_combo_items_component FOREIGN KEY (component_id) REFERENCES products (id),
    INDEX idx_combo_items_component (component_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE order_items
    ADD COLUMN original_unit_price DECIMAL(12, 2) NULL AFTER unit_price;

CREATE TABLE order_item_components (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT       NOT NULL,
    product_id    BIGINT       NULL,
    product_name  VARCHAR(255) NOT NULL,
    quantity      INT          NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NULL,
    CONSTRAINT fk_oic_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_oic_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL,
    INDEX idx_oic_order_item (order_item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
```

- [ ] **Step 4: `M/enums/ProductType.java`**

```java
package com.banhmyking.banhmyking.enums;

/** Loại sản phẩm (spec combo-sale B4): món lẻ hoặc combo cố định gồm nhiều món lẻ. */
public enum ProductType {
    SINGLE,
    COMBO
}
```

- [ ] **Step 5: `M/entity/ComboItemId.java` và `M/entity/ComboItem.java`**

```java
package com.banhmyking.banhmyking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class ComboItemId implements Serializable {

    @Column(name = "combo_id")
    private Long comboId;

    @Column(name = "component_id")
    private Long componentId;
}
```

```java
package com.banhmyking.banhmyking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Một món lẻ trong combo (spec combo-sale §2.2). Khoá ghép (combo, món) nên mỗi món tối đa một dòng.
 * Combo bị xoá mềm vẫn giữ dòng này để phục vụ lịch sử.
 */
@Getter
@Setter
@Entity
@Table(name = "combo_items")
public class ComboItem {

    @EmbeddedId
    private ComboItemId id;

    @MapsId("comboId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private Product combo;

    @MapsId("componentId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
    private Product component;

    /** Số phần của món trong MỘT combo (1–20). */
    @Column(nullable = false)
    private int quantity;
}
```

- [ ] **Step 6: `M/entity/OrderItemComponent.java`**

```java
package com.banhmyking.banhmyking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Snapshot thành phần của một dòng combo trong đơn — tên + số lượng tại lúc đặt (spec §2.4). */
@Getter
@Setter
@Entity
@Table(name = "order_item_components")
public class OrderItemComponent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    /** NULL nếu món sau này bị xoá cứng — tên vẫn đọc từ snapshot. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    /** Số lượng trong MỘT combo. */
    @Column(nullable = false)
    private Integer quantity;
}
```

- [ ] **Step 7: Sửa `M/entity/Product.java`**

Thêm import (giữ các import cũ):

```java
import com.banhmyking.banhmyking.enums.ProductType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDateTime;
import org.hibernate.annotations.BatchSize;
```

Thêm `@BatchSize(size = 50)` ngay trên `public class Product extends BaseEntity {` (nạp proxy thành phần combo theo lô khi duyệt thực đơn — spec §9 hiệu năng):

```java
@BatchSize(size = 50)
public class Product extends BaseEntity {
```

Ngay sau khối `private Category category;` thêm:

```java

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType = ProductType.SINGLE;
```

Ngay sau khối `private BigDecimal price;` thêm:

```java

    /** Giá khuyến mãi (chỉ món lẻ); NULL = không KM. Hiệu lực tính ở ProductPricing. */
    @Column(name = "sale_price", precision = 12, scale = 2)
    private BigDecimal salePrice;

    /** Giờ Việt Nam; NULL = áp dụng ngay. */
    @Column(name = "sale_starts_at")
    private LocalDateTime saleStartsAt;

    /** Giờ Việt Nam; NULL = không hết hạn. */
    @Column(name = "sale_ends_at")
    private LocalDateTime saleEndsAt;
```

Ngay sau khối `private List<Review> reviews = new ArrayList<>();` thêm:

```java

    /** Thành phần (chỉ combo). Ghi qua ComboItemRepository — phía này chỉ đọc. */
    @OneToMany(mappedBy = "combo", fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<ComboItem> comboItems = new ArrayList<>();

    public boolean isCombo() {
        return productType == ProductType.COMBO;
    }
```

- [ ] **Step 8: Sửa `M/entity/OrderItem.java`**

Thêm import `org.hibernate.annotations.BatchSize;`. Ngay sau khối `private BigDecimal unitPrice;` thêm:

```java

    /** Giá gốc 1 đơn vị (chưa gồm topping) lúc đặt; NULL ở đơn cũ = không có ưu đãi. */
    @Column(name = "original_unit_price", precision = 12, scale = 2)
    private BigDecimal originalUnitPrice;
```

Ngay sau khối `private List<OrderItemOption> options = new ArrayList<>();` thêm:

```java

    /** Snapshot thành phần khi dòng là combo; rỗng với món lẻ. */
    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<OrderItemComponent> components = new ArrayList<>();
```

- [ ] **Step 9: `M/repository/ComboItemRepository.java`**

```java
package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ComboItemRepository extends JpaRepository<ComboItem, ComboItemId> {

    /** Tên các combo CHƯA xoá đang chứa món — dùng để chặn xoá món lẻ (spec §5). */
    @Query("SELECT DISTINCT ci.combo.name FROM ComboItem ci "
            + "WHERE ci.component.id = :componentId AND ci.combo.deleted = false "
            + "ORDER BY ci.combo.name")
    List<String> findActiveComboNamesContaining(@Param("componentId") Long componentId);
}
```

- [ ] **Step 10: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=ComboSchemaIntegrationTest`
Expected: PASS (3 test). Log có `Migrating schema ... to version "14 - combo sale"` (lần đầu trên DB dev).

- [ ] **Step 11: Chạy toàn bộ test**

Run: `./mvnw -B clean test`
Expected: BUILD SUCCESS (chỉ thêm cột/bảng; Hibernate validate khớp).

- [ ] **Step 12: Commit (người dùng tự chạy)**

```
git add src/main/resources/db/migration/V14__combo_sale.sql src/main/java/com/banhmyking/banhmyking/enums/ProductType.java src/main/java/com/banhmyking/banhmyking/entity/ComboItemId.java src/main/java/com/banhmyking/banhmyking/entity/ComboItem.java src/main/java/com/banhmyking/banhmyking/entity/OrderItemComponent.java src/main/java/com/banhmyking/banhmyking/entity/Product.java src/main/java/com/banhmyking/banhmyking/entity/OrderItem.java src/main/java/com/banhmyking/banhmyking/repository/ComboItemRepository.java src/test/java/com/banhmyking/banhmyking/repository/ComboSchemaIntegrationTest.java
git commit -m "feat(combo): V14 cột giá khuyến mãi, loại sản phẩm, bảng combo_items và snapshot thành phần đơn"
```

---

## Task 2: `ProductPricing` — nguồn sự thật của giá

**Files:**
- Create: `M/service/ProductPricing.java`
- Test: `T/service/ProductPricingTest.java`

**Interfaces:**
- Consumes: `Product` (`isCombo()`, `getSalePrice()`, `getSaleStartsAt()`, `getSaleEndsAt()`, `getPrice()`, `getComboItems()`), `ComboItem` (`getComponent()`, `getQuantity()`), `CartItem`, `CartItemOption` (Task 1 / có sẵn), bean `Clock` (`TimeConfig`).
- Produces (`@Component`, constructor `ProductPricing(Clock clock)`):
  - `LocalDateTime now()`
  - `boolean isSaleActive(Product p, LocalDateTime now)`
  - `BigDecimal effectivePrice(Product p, LocalDateTime now)`
  - `BigDecimal originalPrice(Product p)`
  - `BigDecimal compareAtPrice(Product p, LocalDateTime now)` (null = không gạch giá)
  - `Integer discountPercent(Product p, LocalDateTime now)` (null nếu không giảm)
  - `BigDecimal optionsExtra(CartItem item)`
  - `BigDecimal unitPrice(CartItem item, LocalDateTime now)` = giá hiệu lực + topping
  - `BigDecimal lineTotal(CartItem item, LocalDateTime now)` = unitPrice × quantity (null → 1)
  - `BigDecimal lineSavings(CartItem item, LocalDateTime now)` = max(0, originalPrice − effectivePrice) × quantity

- [ ] **Step 1: Viết test (đỏ)**

`T/service/ProductPricingTest.java`:

```java
package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.banhmyking.banhmyking.config.TimeConfig;
import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.CartItemOption;
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.ProductOption;
import com.banhmyking.banhmyking.enums.ProductType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductPricingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 10, 0);
    private final ProductPricing pricing = new ProductPricing(
            Clock.fixed(NOW.atZone(TimeConfig.VIETNAM).toInstant(), TimeConfig.VIETNAM));

    @Test
    @DisplayName("now() lấy giờ Việt Nam từ Clock")
    void nowComesFromClock() {
        assertThat(pricing.now()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Giá KM không giới hạn thời gian: đang KM, gạch giá gốc, giảm 16%")
    void saleWithoutWindowIsActive() {
        Product p = single(1L, "30000", "25000", null, null);

        assertThat(pricing.isSaleActive(p, NOW)).isTrue();
        assertThat(pricing.effectivePrice(p, NOW)).isEqualByComparingTo("25000");
        assertThat(pricing.compareAtPrice(p, NOW)).isEqualByComparingTo("30000");
        assertThat(pricing.discountPercent(p, NOW)).isEqualTo(16);
    }

    @Test
    @DisplayName("Trước saleStartsAt: chưa KM, không gạch giá")
    void saleNotStartedYet() {
        Product p = single(1L, "30000", "25000", NOW.plusMinutes(1), null);

        assertThat(pricing.isSaleActive(p, NOW)).isFalse();
        assertThat(pricing.effectivePrice(p, NOW)).isEqualByComparingTo("30000");
        assertThat(pricing.compareAtPrice(p, NOW)).isNull();
        assertThat(pricing.discountPercent(p, NOW)).isNull();
    }

    @Test
    @DisplayName("Đúng mốc saleStartsAt: đã KM (now ≥ starts)")
    void saleStartsExactlyNow() {
        assertThat(pricing.isSaleActive(single(1L, "30000", "25000", NOW, null), NOW)).isTrue();
    }

    @Test
    @DisplayName("Đúng mốc saleEndsAt: đã hết KM (now < ends là sai)")
    void saleEndsExactlyNowIsOver() {
        assertThat(pricing.isSaleActive(single(1L, "30000", "25000", null, NOW), NOW)).isFalse();
    }

    @Test
    @DisplayName("Trước saleEndsAt 1 giây: còn KM; sau saleEndsAt: hết KM")
    void saleAroundEnd() {
        assertThat(pricing.isSaleActive(single(1L, "30000", "25000", null, NOW.plusSeconds(1)), NOW)).isTrue();
        assertThat(pricing.isSaleActive(single(1L, "30000", "25000", NOW.minusDays(2), NOW.minusDays(1)), NOW))
                .isFalse();
    }

    @Test
    @DisplayName("Không có salePrice: giá hiệu lực = giá gốc")
    void noSalePrice() {
        Product p = single(1L, "30000", null, null, null);

        assertThat(pricing.isSaleActive(p, NOW)).isFalse();
        assertThat(pricing.effectivePrice(p, NOW)).isEqualByComparingTo("30000");
        assertThat(pricing.originalPrice(p)).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("Combo: giá gốc = Σ giá GỐC thành phần × số lượng (bỏ qua giá KM của thành phần)")
    void comboOriginalPriceUsesComponentListPrices() {
        Product banhMi = single(1L, "30000", "20000", null, null); // thành phần đang KM
        Product coffee = single(2L, "20000", null, null, null);
        Product combo = combo(9L, "45000", banhMi, 1, coffee, 2);
        combo.setSalePrice(new BigDecimal("1000")); // combo không bao giờ dùng giá KM

        assertThat(pricing.originalPrice(combo)).isEqualByComparingTo("70000");
        assertThat(pricing.isSaleActive(combo, NOW)).isFalse();
        assertThat(pricing.effectivePrice(combo, NOW)).isEqualByComparingTo("45000");
        assertThat(pricing.compareAtPrice(combo, NOW)).isEqualByComparingTo("70000");
        assertThat(pricing.discountPercent(combo, NOW)).isEqualTo(35);
    }

    @Test
    @DisplayName("Combo không rẻ hơn tổng giá lẻ: không gạch giá, không % giảm")
    void comboNotCheaperHasNoCompareAt() {
        Product combo = combo(9L, "80000", single(1L, "30000", null, null, null), 1,
                single(2L, "20000", null, null, null), 2);

        assertThat(pricing.compareAtPrice(combo, NOW)).isNull();
        assertThat(pricing.discountPercent(combo, NOW)).isNull();
    }

    @Test
    @DisplayName("Dòng giỏ: đơn giá = giá hiệu lực + topping; tiết kiệm = chênh giá × số lượng")
    void cartLinePricesUseEffectivePricePlusToppings() {
        CartItem item = new CartItem();
        item.setProduct(single(1L, "30000", "25000", null, null));
        item.setQuantity(2);
        ProductOption pate = new ProductOption();
        pate.setExtraPrice(new BigDecimal("5000"));
        CartItemOption selected = new CartItemOption();
        selected.setProductOption(pate);
        item.getSelectedOptions().add(selected);

        assertThat(pricing.optionsExtra(item)).isEqualByComparingTo("5000");
        assertThat(pricing.unitPrice(item, NOW)).isEqualByComparingTo("30000");
        assertThat(pricing.lineTotal(item, NOW)).isEqualByComparingTo("60000");
        assertThat(pricing.lineSavings(item, NOW)).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("Dòng giỏ không ưu đãi: tiết kiệm = 0; số lượng null tính là 1")
    void cartLineWithoutSale() {
        CartItem item = new CartItem();
        item.setProduct(single(1L, "30000", null, null, null));
        item.setQuantity(null);

        assertThat(pricing.lineTotal(item, NOW)).isEqualByComparingTo("30000");
        assertThat(pricing.lineSavings(item, NOW)).isEqualByComparingTo("0");
    }

    private static Product single(Long id, String price, String salePrice, LocalDateTime starts, LocalDateTime ends) {
        Product p = new Product();
        p.setId(id);
        p.setName("Món " + id);
        p.setPrice(new BigDecimal(price));
        p.setSalePrice(salePrice == null ? null : new BigDecimal(salePrice));
        p.setSaleStartsAt(starts);
        p.setSaleEndsAt(ends);
        return p;
    }

    private static Product combo(Long id, String price, Product first, int firstQty, Product second, int secondQty) {
        Product combo = new Product();
        combo.setId(id);
        combo.setName("Combo " + id);
        combo.setProductType(ProductType.COMBO);
        combo.setPrice(new BigDecimal(price));
        combo.setComboItems(List.of(item(combo, first, firstQty), item(combo, second, secondQty)));
        return combo;
    }

    private static ComboItem item(Product combo, Product component, int quantity) {
        ComboItem item = new ComboItem();
        item.setId(new ComboItemId(combo.getId(), component.getId()));
        item.setCombo(combo);
        item.setComponent(component);
        item.setQuantity(quantity);
        return item;
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=ProductPricingTest`
Expected: COMPILATION ERROR (`ProductPricing` chưa tồn tại).

- [ ] **Step 3: `M/service/ProductPricing.java`**

```java
package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.CartItemOption;
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.Product;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * Nguồn sự thật DUY NHẤT của giá bán (spec combo-sale §3): giá KM theo thời gian, giá gốc của combo,
 * giá gạch, đơn giá dòng giỏ. PriceCalculator, giỏ, đơn, báo giá và thực đơn đều gọi qua đây —
 * không nơi nào tự cộng giá lại.
 */
@Component
public class ProductPricing {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Clock clock;

    public ProductPricing(Clock clock) {
        this.clock = clock;
    }

    /** Giờ Việt Nam hiện tại — mốc dùng chung cho một lần tính (giỏ, báo giá) hoặc mốc chốt giá (tạo đơn). */
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** Món lẻ có salePrice và {@code now} nằm trong [saleStartsAt, saleEndsAt); NULL = không giới hạn phía đó. */
    public boolean isSaleActive(Product product, LocalDateTime now) {
        if (product == null || product.isCombo() || product.getSalePrice() == null) {
            return false;
        }
        if (product.getSaleStartsAt() != null && now.isBefore(product.getSaleStartsAt())) {
            return false;
        }
        return product.getSaleEndsAt() == null || now.isBefore(product.getSaleEndsAt());
    }

    /** Giá bán 1 phần chưa gồm topping: giá KM khi đang KM, ngược lại giá niêm yết (combo: giá combo). */
    public BigDecimal effectivePrice(Product product, LocalDateTime now) {
        if (product == null) {
            return BigDecimal.ZERO;
        }
        if (isSaleActive(product, now)) {
            return product.getSalePrice();
        }
        return nullToZero(product.getPrice());
    }

    /** Món lẻ: giá niêm yết. Combo: Σ giá GỐC thành phần × số lượng (không dùng giá KM của thành phần). */
    public BigDecimal originalPrice(Product product) {
        if (product == null) {
            return BigDecimal.ZERO;
        }
        if (!product.isCombo()) {
            return nullToZero(product.getPrice());
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (ComboItem item : product.getComboItems()) {
            if (item.getComponent() != null) {
                sum = sum.add(nullToZero(item.getComponent().getPrice())
                        .multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        return sum;
    }

    /** Giá gạch = giá gốc khi giá gốc lớn hơn giá đang bán; null = không gạch giá. */
    public BigDecimal compareAtPrice(Product product, LocalDateTime now) {
        BigDecimal original = originalPrice(product);
        return original.compareTo(effectivePrice(product, now)) > 0 ? original : null;
    }

    /** Làm tròn xuống (compareAt − effective) / compareAt × 100; chỉ để hiển thị. */
    public Integer discountPercent(Product product, LocalDateTime now) {
        BigDecimal compareAt = compareAtPrice(product, now);
        if (compareAt == null || compareAt.signum() <= 0) {
            return null;
        }
        return compareAt.subtract(effectivePrice(product, now))
                .multiply(HUNDRED)
                .divide(compareAt, 0, RoundingMode.DOWN)
                .intValue();
    }

    /** Tổng phụ phí topping của dòng giỏ (null-safe). */
    public BigDecimal optionsExtra(CartItem item) {
        BigDecimal extra = BigDecimal.ZERO;
        if (item.getSelectedOptions() != null) {
            for (CartItemOption option : item.getSelectedOptions()) {
                if (option.getProductOption() != null && option.getProductOption().getExtraPrice() != null) {
                    extra = extra.add(option.getProductOption().getExtraPrice());
                }
            }
        }
        return extra;
    }

    /** Đơn giá dòng giỏ = giá hiệu lực + topping (combo không có topping). */
    public BigDecimal unitPrice(CartItem item, LocalDateTime now) {
        return effectivePrice(item.getProduct(), now).add(optionsExtra(item));
    }

    public BigDecimal lineTotal(CartItem item, LocalDateTime now) {
        return unitPrice(item, now).multiply(BigDecimal.valueOf(quantityOf(item)));
    }

    /** Tiết kiệm của dòng = (giá gốc − giá hiệu lực) × số lượng, không âm. */
    public BigDecimal lineSavings(CartItem item, LocalDateTime now) {
        BigDecimal perUnit = originalPrice(item.getProduct()).subtract(effectivePrice(item.getProduct(), now));
        return perUnit.signum() > 0 ? perUnit.multiply(BigDecimal.valueOf(quantityOf(item))) : BigDecimal.ZERO;
    }

    private static int quantityOf(CartItem item) {
        return item.getQuantity() != null ? item.getQuantity() : 1;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
```

- [ ] **Step 4: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=ProductPricingTest`
Expected: PASS (11 test).

- [ ] **Step 5: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/service/ProductPricing.java src/test/java/com/banhmyking/banhmyking/service/ProductPricingTest.java
git commit -m "feat(gia): ProductPricing - giá hiệu lực, giá gốc combo, giá gạch theo Clock"
```

---

## Task 3: Giá hiệu lực đi qua mọi luồng tính tiền (PriceCalculator, giỏ, tạo đơn, báo giá)

**Files:**
- Modify: `M/service/PriceCalculator.java` (viết lại toàn bộ), `M/service/impl/CartServiceImpl.java`, `M/service/impl/OrderServiceImpl.java`, `M/controller/DeliveryController.java`
- Test: `T/service/PriceCalculatorTest.java`, `T/service/OrderServiceTest.java`, `T/service/OrderPriceSnapshotTest.java`, `T/service/OrderStoreScopeTest.java`, `T/service/CartServiceTest.java`

**Interfaces:**
- Consumes: `ProductPricing` (Task 2): `now()`, `lineTotal(CartItem, LocalDateTime)`, `unitPrice(CartItem, LocalDateTime)`, `effectivePrice(Product, LocalDateTime)`.
- Produces:
  - `PriceCalculator(ProductPricing productPricing)` (constructor, bỏ static `unitPriceOf`/`lineTotalOf`).
  - `BigDecimal calculateSubtotal(Cart cart)`; `BigDecimal calculateSubtotal(Cart cart, LocalDateTime pricedAt)`.
  - `PriceBreakdown calculate(Cart, Promotion)`; `calculate(Cart, Promotion, BigDecimal shippingFee)`; `calculate(Cart, Promotion, BigDecimal shippingFee, LocalDateTime pricedAt)`.
  - `computeDiscount(...)` giữ nguyên chữ ký.
  - `OrderServiceImpl` có field `productPricing`; trong `createFromCart` dùng biến `LocalDateTime pricedAt = productPricing.now()` (Task 9 dùng lại tên này).
  - `CartServiceImpl` có field `productPricing`.

- [ ] **Step 1: Viết test đỏ — `PriceCalculatorTest`**

Sửa khai báo calculator (dòng `private final PriceCalculator priceCalculator = new PriceCalculator();`) thành:

```java
    /** 10:00 ngày 02/10/2026 giờ Việt Nam — mốc cố định cho giá KM. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 10, 0);

    private final PriceCalculator priceCalculator = new PriceCalculator(new ProductPricing(
            java.time.Clock.fixed(NOW.atZone(com.banhmyking.banhmyking.config.TimeConfig.VIETNAM).toInstant(),
                    com.banhmyking.banhmyking.config.TimeConfig.VIETNAM)));
```

Thêm nested class mới ở cuối class (trước dấu `}` cuối file):

```java

    @Nested
    @DisplayName("7. Giá khuyến mãi + mã giảm giá cộng dồn (B3)")
    class SalePriceTests {

        private Promotion promo(DiscountType type, BigDecimal value, BigDecimal minOrder) {
            Promotion promo = new Promotion();
            promo.setCode("SALE_TEST");
            promo.setActive(true);
            promo.setDiscountType(type);
            promo.setValue(value);
            promo.setMinOrderAmount(minOrder);
            promo.setStartsAt(LocalDateTime.now().minusDays(1));
            promo.setEndsAt(LocalDateTime.now().plusDays(1));
            return promo;
        }

        @Test
        @DisplayName("Tạm tính dùng giá KM đang hiệu lực: 2 × (25.000 + 8.000) = 66.000đ")
        void subtotalUsesActiveSalePrice() {
            product1.setSalePrice(BigDecimal.valueOf(25000));

            assertThat(priceCalculator.calculateSubtotal(cart)).isEqualByComparingTo("66000");
        }

        @Test
        @DisplayName("KM hết hạn đúng mốc now → quay về giá gốc 76.000đ")
        void expiredSaleFallsBackToOriginalPrice() {
            product1.setSalePrice(BigDecimal.valueOf(25000));
            product1.setSaleEndsAt(NOW);

            assertThat(priceCalculator.calculateSubtotal(cart)).isEqualByComparingTo("76000");
        }

        @Test
        @DisplayName("Mốc pricedAt truyền vào quyết định giá (chốt giá lúc tạo đơn)")
        void pricedAtDecidesSale() {
            product1.setSalePrice(BigDecimal.valueOf(25000));
            product1.setSaleStartsAt(NOW.plusHours(1));

            assertThat(priceCalculator.calculateSubtotal(cart, NOW)).isEqualByComparingTo("76000");
            assertThat(priceCalculator.calculateSubtotal(cart, NOW.plusHours(2))).isEqualByComparingTo("66000");
        }

        @Test
        @DisplayName("PERCENTAGE 10% tính trên tạm tính đã giảm: 10% × 66.000 = 6.600đ")
        void percentagePromotionAppliesOnDiscountedSubtotal() {
            product1.setSalePrice(BigDecimal.valueOf(25000));
            Promotion promo = promo(DiscountType.PERCENTAGE, BigDecimal.TEN, BigDecimal.ZERO);

            PriceBreakdown breakdown = priceCalculator.calculate(cart, promo, BigDecimal.ZERO, NOW);

            assertThat(breakdown.getSubtotal()).isEqualByComparingTo("66000");
            assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo("6600");
            assertThat(breakdown.getTotal()).isEqualByComparingTo("59400");
        }

        @Test
        @DisplayName("Đơn tối thiểu xét trên tạm tính đã giảm: 66.000 < 70.000 → từ chối")
        void minOrderAmountCheckedOnDiscountedSubtotal() {
            product1.setSalePrice(BigDecimal.valueOf(25000));
            Promotion promo = promo(DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(5000), BigDecimal.valueOf(70000));

            assertThatThrownBy(() -> priceCalculator.calculate(cart, promo, BigDecimal.ZERO, NOW))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("chưa đạt giá trị tối thiểu");
        }
    }
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=PriceCalculatorTest`
Expected: COMPILATION ERROR (constructor `PriceCalculator(ProductPricing)`, `calculateSubtotal(Cart, LocalDateTime)`, `calculate(..., LocalDateTime)` chưa có).

- [ ] **Step 3: Viết lại `M/service/PriceCalculator.java`**

```java
package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.order.PriceBreakdown;
import com.banhmyking.banhmyking.entity.Cart;
import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.Promotion;
import com.banhmyking.banhmyking.enums.DiscountType;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
public class PriceCalculator {

    public static final BigDecimal DEFAULT_SHIPPING_FEE = BigDecimal.valueOf(15000);

    private final ProductPricing productPricing;

    public PriceCalculator(ProductPricing productPricing) {
        this.productPricing = productPricing;
    }

    /**
     * Tính toán subtotal, shippingFee, discountAmount và total cho giỏ hàng.
     */
    public PriceBreakdown calculate(Cart cart, Promotion promotion) {
        return calculate(cart, promotion, DEFAULT_SHIPPING_FEE);
    }

    public PriceBreakdown calculate(Cart cart, Promotion promotion, BigDecimal shippingFee) {
        return calculate(cart, promotion, shippingFee, productPricing.now());
    }

    public BigDecimal calculateSubtotal(Cart cart) {
        return calculateSubtotal(cart, productPricing.now());
    }

    /**
     * Tạm tính theo giá hiệu lực tại {@code pricedAt} (giá KM / giá combo) + topping. Mã giảm giá
     * (đơn tối thiểu, PERCENTAGE) xét trên đúng số này — cộng dồn với giá KM (spec combo-sale B3).
     */
    public BigDecimal calculateSubtotal(Cart cart, LocalDateTime pricedAt) {
        BigDecimal subtotal = BigDecimal.ZERO;
        if (cart != null && cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                subtotal = subtotal.add(productPricing.lineTotal(item, pricedAt));
            }
        }
        return subtotal.setScale(2, RoundingMode.HALF_UP);
    }

    /** Như {@link #calculate(Cart, Promotion, BigDecimal)} nhưng chốt giá tại {@code pricedAt} (lúc tạo đơn). */
    public PriceBreakdown calculate(Cart cart, Promotion promotion, BigDecimal shippingFee, LocalDateTime pricedAt) {
        if (shippingFee == null) {
            shippingFee = DEFAULT_SHIPPING_FEE;
        }
        shippingFee = shippingFee.setScale(2, RoundingMode.HALF_UP);

        // 1. Tính subtotal từ các món trong giỏ (giá hiệu lực tại pricedAt)
        BigDecimal subtotal = calculateSubtotal(cart, pricedAt);

        // 2. Tính discount từ promotion (nếu có)
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (promotion != null) {
            validatePromotion(promotion, subtotal);
            discountAmount = computeDiscount(promotion, subtotal, shippingFee);
        }
        discountAmount = discountAmount.setScale(2, RoundingMode.HALF_UP);

        // 3. Tính total = subtotal + shippingFee - discountAmount
        BigDecimal total = subtotal.add(shippingFee).subtract(discountAmount);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }
        total = total.setScale(2, RoundingMode.HALF_UP);

        return PriceBreakdown.builder()
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .discountAmount(discountAmount)
                .total(total)
                .build();
    }

    private void validatePromotion(Promotion promotion, BigDecimal subtotal) {
        if (!promotion.isActive()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã khuyến mãi hiện không kích hoạt");
        }

        LocalDateTime now = LocalDateTime.now();
        if (promotion.getStartsAt() != null && now.isBefore(promotion.getStartsAt())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã khuyến mãi chưa đến thời gian áp dụng");
        }
        if (promotion.getEndsAt() != null && now.isAfter(promotion.getEndsAt())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã khuyến mãi đã hết hạn sử dụng");
        }

        if (promotion.getMaxUsage() != null && promotion.getMaxUsage() > 0) {
            int used = promotion.getUsedCount() != null ? promotion.getUsedCount() : 0;
            if (used >= promotion.getMaxUsage()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã khuyến mãi đã hết lượt sử dụng");
            }
        }

        if (promotion.getMinOrderAmount() != null && subtotal.compareTo(promotion.getMinOrderAmount()) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    String.format("Đơn hàng chưa đạt giá trị tối thiểu %,.0fđ để áp dụng mã giảm giá",
                            promotion.getMinOrderAmount().doubleValue()));
        }
    }

    /**
     * Công thức giảm giá duy nhất của hệ thống — cả lúc tạo đơn lẫn lúc kiểm tra mã
     * ({@code POST /promotions/validate}) đều phải gọi hàm này, nếu không khách sẽ thấy
     * số tiền giảm khác với số tiền thực bị trừ.
     */
    public BigDecimal computeDiscount(Promotion promotion, BigDecimal subtotal, BigDecimal shippingFee) {
        DiscountType type = promotion.getDiscountType();
        BigDecimal value = promotion.getValue() != null ? promotion.getValue() : BigDecimal.ZERO;

        if (type == null) {
            return BigDecimal.ZERO;
        }

        return switch (type) {
            case PERCENTAGE -> {
                BigDecimal discount = subtotal.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (promotion.getMaxDiscountAmount() != null && discount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
                    yield promotion.getMaxDiscountAmount();
                }
                yield discount;
            }
            case FIXED_AMOUNT -> value.min(subtotal);
            case FREE_SHIP -> value.min(shippingFee);
        };
    }
}
```

(`validatePromotion` giữ nguyên `LocalDateTime.now()` của code cũ — không thuộc phạm vi B.)

- [ ] **Step 4: Sửa `CartServiceImpl` cho biên dịch được**

Thay import `import com.banhmyking.banhmyking.service.PriceCalculator;` bằng:

```java
import com.banhmyking.banhmyking.service.ProductPricing;
```

và thêm `import java.time.LocalDateTime;` vào nhóm `java.*`.

Ngay sau dòng `    private final ProductOptionRepository productOptionRepository;` thêm:

```java
    private final ProductPricing productPricing;
```

Trong `toCartResponse`, thay:

```java
        List<CartItemResponse> itemResponses = new ArrayList<>();
```

bằng:

```java
        // Một mốc giờ cho cả giỏ: giỏ luôn tính theo giá hiện tại, chỉ chốt giá khi tạo đơn (spec §3)
        LocalDateTime now = productPricing.now();
        List<CartItemResponse> itemResponses = new ArrayList<>();
```

và thay:

```java
                // dùng chung công thức giá với PriceCalculator (không tự tính lại)
                BigDecimal unitPrice = PriceCalculator.unitPriceOf(item);
                BigDecimal itemSubtotal = PriceCalculator.lineTotalOf(item);
```

bằng:

```java
                // dùng chung công thức giá với ProductPricing (không tự tính lại)
                BigDecimal unitPrice = productPricing.unitPrice(item, now);
                BigDecimal itemSubtotal = productPricing.lineTotal(item, now);
```

- [ ] **Step 5: Sửa `DeliveryController`**

Thay import `import com.banhmyking.banhmyking.service.PriceCalculator;` bằng `import com.banhmyking.banhmyking.service.ProductPricing;`; thêm `import java.time.LocalDateTime;`.

Ngay sau `    private final CartRepository cartRepository;` thêm:

```java
    private final ProductPricing productPricing;
```

Thay:

```java
        Cart cart = cartRepository.findByUserIdWithDetails(userId).orElse(null);
        if (cart != null && cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                items.merge(item.getProduct(), item.getQuantity(), Integer::sum);
                subtotal = subtotal.add(PriceCalculator.lineTotalOf(item));
```

bằng:

```java
        Cart cart = cartRepository.findByUserIdWithDetails(userId).orElse(null);
        LocalDateTime pricedAt = productPricing.now();
        if (cart != null && cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                items.merge(item.getProduct(), item.getQuantity(), Integer::sum);
                subtotal = subtotal.add(productPricing.lineTotal(item, pricedAt));
```

- [ ] **Step 6: Sửa `OrderServiceImpl.createFromCart`**

Ngay sau `    private final PriceCalculator priceCalculator;` thêm:

```java
    private final com.banhmyking.banhmyking.service.ProductPricing productPricing;
```

Thay:

```java
        // #15: dùng chung công thức unitPrice/lineTotal với PriceCalculator — không tự tính lại
        BigDecimal cartSubtotal = BigDecimal.ZERO;
        if (cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                cartSubtotal = cartSubtotal.add(PriceCalculator.lineTotalOf(item));
            }
        }
```

bằng:

```java
        // Thời điểm chốt giá = lúc tạo đơn (spec combo-sale §3): một mốc cho tạm tính, mã giảm giá và snapshot.
        LocalDateTime pricedAt = productPricing.now();
        BigDecimal cartSubtotal = BigDecimal.ZERO;
        if (cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                cartSubtotal = cartSubtotal.add(productPricing.lineTotal(item, pricedAt));
            }
        }
```

Thay `BigDecimal promoSubtotal = priceCalculator.calculateSubtotal(cart);` bằng:

```java
            BigDecimal promoSubtotal = priceCalculator.calculateSubtotal(cart, pricedAt);
```

Thay `PriceBreakdown priceBreakdown = priceCalculator.calculate(cart, promotion, shippingFee);` bằng:

```java
        PriceBreakdown priceBreakdown = priceCalculator.calculate(cart, promotion, shippingFee, pricedAt);
```

Thay:

```java
            orderItem.setUnitPrice(product.getPrice());       // Snapshot giá gốc
```

bằng:

```java
            orderItem.setUnitPrice(productPricing.effectivePrice(product, pricedAt)); // Snapshot giá hiệu lực (giá KM nếu đang KM)
```

Thay:

```java
            BigDecimal lineTotal = PriceCalculator.lineTotalOf(cartItem)
                    .setScale(2, RoundingMode.HALF_UP);
```

bằng:

```java
            BigDecimal lineTotal = productPricing.lineTotal(cartItem, pricedAt)
                    .setScale(2, RoundingMode.HALF_UP);
```

- [ ] **Step 7: Sửa các test đang dựng `OrderServiceImpl` / `CartServiceImpl` / `PriceCalculator`**

`T/service/OrderStoreScopeTest.java` — thay `@Spy private PriceCalculator priceCalculator = new PriceCalculator();` bằng:

```java
    @Spy private PriceCalculator priceCalculator = new PriceCalculator(
            new ProductPricing(java.time.Clock.system(com.banhmyking.banhmyking.config.TimeConfig.VIETNAM)));
```

`T/service/OrderServiceTest.java`:
- Thêm import: `com.banhmyking.banhmyking.config.TimeConfig`, `java.time.Clock`, `java.time.Instant`, `java.time.LocalDateTime`.
- Ngay sau khối `@Mock private InventoryService inventoryService;` thêm:

```java
    /** 10:00 02/10/2026 giờ Việt Nam. */
    @Spy
    private ProductPricing productPricing = new ProductPricing(
            Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), TimeConfig.VIETNAM));
```

- Thay **mọi** chuỗi `priceCalculator.calculate(eq(testCart), any(), any())` bằng `priceCalculator.calculate(eq(testCart), any(), any(), any())` (4 chỗ) và **mọi** `priceCalculator.calculate(eq(testCart), eq(promo), any())` bằng `priceCalculator.calculate(eq(testCart), eq(promo), any(), any())` (2 chỗ) — dùng Edit với `replace_all: true`.
- Thêm test mới (sau `createFromCart_success`):

```java
    @Test
    @DisplayName("Giá KM: dòng đơn chụp giá hiệu lực tại lúc tạo đơn và truyền đúng mốc chốt giá cho PriceCalculator")
    void createFromCart_snapshotsEffectiveSalePrice() {
        testProduct.setSalePrice(BigDecimal.valueOf(30000)); // giá gốc 35.000
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressId(200L)
                .paymentMethod(PaymentMethod.COD)
                .build();
        PriceBreakdown breakdown = PriceBreakdown.builder()
                .subtotal(BigDecimal.valueOf(70000))
                .shippingFee(BigDecimal.valueOf(15000))
                .discountAmount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(85000))
                .build();
        LocalDateTime pricedAt = LocalDateTime.of(2026, 10, 2, 10, 0);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(testCart));
        when(addressRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(testAddress));
        when(priceCalculator.calculate(eq(testCart), any(), any(), eq(pricedAt))).thenReturn(breakdown);
        when(orderCodeGenerator.generateUniqueCode(any(), anyInt())).thenReturn("BMK-20261002-SALE1");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.createFromCart(1L, request);

        // (30.000 + 5.000 topping) × 2 = 70.000
        assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo("30000");
        assertThat(response.getItems().get(0).getLineTotal()).isEqualByComparingTo("70000");
    }
```

`T/service/OrderPriceSnapshotTest.java`:
- Thêm import `com.banhmyking.banhmyking.config.TimeConfig`, `java.time.Clock`, `java.time.Instant`.
- Ngay sau khối `@Mock private InventoryService inventoryService;` thêm:

```java
    @Spy
    private ProductPricing productPricing = new ProductPricing(
            Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), TimeConfig.VIETNAM));
```

- Thay `when(priceCalculator.calculate(eq(cart), any(), eq(BigDecimal.valueOf(15000)))).thenReturn(breakdown);` bằng:

```java
        when(priceCalculator.calculate(eq(cart), any(), eq(BigDecimal.valueOf(15000)), any())).thenReturn(breakdown);
```

`T/service/CartServiceTest.java`:
- Thêm import `com.banhmyking.banhmyking.config.TimeConfig`, `java.time.Clock`, `java.time.Instant`, `org.mockito.Spy`.
- Ngay sau khối `@Mock private UserRepository userRepository;` thêm:

```java
    @Spy
    private ProductPricing productPricing = new ProductPricing(
            Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), TimeConfig.VIETNAM));
```

- Thêm test:

```java
    @Test
    @DisplayName("Giỏ tính theo giá KM đang hiệu lực")
    void addToCart_usesActiveSalePrice() {
        availableProduct.setSalePrice(BigDecimal.valueOf(25000));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser));
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(availableProduct));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse response = cartService.addToCart(1L, AddToCartRequest.builder().productId(10L).quantity(2).build());

        assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo("25000");
        assertThat(response.getSubtotal()).isEqualByComparingTo("50000");
    }
```

- [ ] **Step 8: Chạy các test liên quan**

Run: `./mvnw -B clean test -Dtest=PriceCalculatorTest,OrderServiceTest,OrderPriceSnapshotTest,OrderStoreScopeTest,CartServiceTest`
Expected: PASS toàn bộ (gồm 5 test mới của PriceCalculator, 1 của OrderService, 1 của Cart).

- [ ] **Step 9: Chạy toàn bộ test**

Run: `./mvnw -B clean test`
Expected: BUILD SUCCESS (không còn tham chiếu `PriceCalculator.unitPriceOf` / `lineTotalOf`; `grep -rn "lineTotalOf\|unitPriceOf" src` không ra kết quả).

- [ ] **Step 10: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/service/PriceCalculator.java src/main/java/com/banhmyking/banhmyking/service/impl/CartServiceImpl.java src/main/java/com/banhmyking/banhmyking/service/impl/OrderServiceImpl.java src/main/java/com/banhmyking/banhmyking/controller/DeliveryController.java src/test/java/com/banhmyking/banhmyking/service/PriceCalculatorTest.java src/test/java/com/banhmyking/banhmyking/service/OrderServiceTest.java src/test/java/com/banhmyking/banhmyking/service/OrderPriceSnapshotTest.java src/test/java/com/banhmyking/banhmyking/service/OrderStoreScopeTest.java src/test/java/com/banhmyking/banhmyking/service/CartServiceTest.java
git commit -m "feat(gia): giỏ, tạo đơn, báo giá và mã giảm giá tính theo giá khuyến mãi đang hiệu lực"
```

---

## Task 4: `ComboExpander` — nhu cầu gộp theo món lẻ

**Files:**
- Create: `M/service/ComboExpander.java`
- Test: `T/service/ComboExpanderTest.java`

**Interfaces:**
- Consumes: `Product.isCombo()`, `Product.getComboItems()`, `ComboItem.getComponent()/getQuantity()` (Task 1).
- Produces (lớp tiện ích tĩnh, `final`, constructor private — dùng được trong unit test mà không cần inject):
  - `static Map<Product, Integer> expand(Map<Product, Integer> lines)` — dòng `SINGLE` giữ nguyên; dòng `COMBO` × q → mỗi thành phần × (quantity × q); cộng dồn cùng món theo `id` (id null → theo chính đối tượng); giữ thứ tự xuất hiện; khoá trả về là đối tượng `Product` gặp đầu tiên.
  - `static boolean isChainAvailable(Product p)` — chưa xoá ∧ `available` ∧ (món lẻ, hoặc combo có ≥ 1 thành phần và mọi thành phần `available` và chưa xoá) — spec §4.3.

- [ ] **Step 1: Viết test (đỏ)**

`T/service/ComboExpanderTest.java`:

```java
package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.enums.ProductType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComboExpanderTest {

    @Test
    @DisplayName("Combo × 2 + món lẻ trùng thành phần → cộng dồn theo món lẻ")
    void expandsComboAndMergesWithSingleLines() {
        Product banhMi = single(1L, "Bánh mì");
        Product coffee = single(2L, "Cà phê sữa đá");
        Product combo = combo(9L, banhMi, 1, coffee, 2);
        Map<Product, Integer> lines = new LinkedHashMap<>();
        lines.put(combo, 2);
        lines.put(banhMi, 1);

        Map<Product, Integer> demand = ComboExpander.expand(lines);

        assertThat(demand).hasSize(2);
        assertThat(demand.get(banhMi)).isEqualTo(3);   // 1 × 2 + 1
        assertThat(demand.get(coffee)).isEqualTo(4);   // 2 × 2
        assertThat(demand.keySet()).containsExactly(banhMi, coffee);
    }

    @Test
    @DisplayName("Hai đối tượng Product cùng id được gộp chung một dòng")
    void mergesByIdNotByInstance() {
        Product first = single(1L, "Bánh mì");
        Product sameId = single(1L, "Bánh mì");
        Map<Product, Integer> lines = new LinkedHashMap<>();
        lines.put(first, 1);
        lines.put(sameId, 2);

        Map<Product, Integer> demand = ComboExpander.expand(lines);

        assertThat(demand).containsExactly(Map.entry(first, 3));
    }

    @Test
    @DisplayName("Món lẻ không id (chưa lưu) vẫn giữ nguyên, không lỗi")
    void keepsProductsWithoutId() {
        Product transientProduct = new Product();
        Map<Product, Integer> demand = ComboExpander.expand(Map.of(transientProduct, 2));

        assertThat(demand).containsExactly(Map.entry(transientProduct, 2));
    }

    @Test
    @DisplayName("Combo còn bán ở mức chuỗi chỉ khi chính combo và mọi thành phần đang bán, chưa xoá")
    void chainAvailability() {
        Product banhMi = single(1L, "Bánh mì");
        Product coffee = single(2L, "Cà phê sữa đá");
        Product combo = combo(9L, banhMi, 1, coffee, 1);

        assertThat(ComboExpander.isChainAvailable(banhMi)).isTrue();
        assertThat(ComboExpander.isChainAvailable(combo)).isTrue();

        coffee.setAvailable(false);
        assertThat(ComboExpander.isChainAvailable(combo)).isFalse();

        coffee.setAvailable(true);
        combo.setAvailable(false);
        assertThat(ComboExpander.isChainAvailable(combo)).isFalse();

        combo.setAvailable(true);
        banhMi.setDeleted(true);
        assertThat(ComboExpander.isChainAvailable(combo)).isFalse();
        assertThat(ComboExpander.isChainAvailable(banhMi)).isFalse();

        Product empty = combo(10L, single(3L, "x"), 1, single(4L, "y"), 1);
        empty.setComboItems(List.of());
        assertThat(ComboExpander.isChainAvailable(empty)).isFalse();
        assertThat(ComboExpander.isChainAvailable(null)).isFalse();
    }

    private static Product single(Long id, String name) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal("20000"));
        p.setAvailable(true);
        return p;
    }

    private static Product combo(Long id, Product first, int firstQty, Product second, int secondQty) {
        Product combo = single(id, "Combo " + id);
        combo.setProductType(ProductType.COMBO);
        combo.setComboItems(List.of(item(combo, first, firstQty), item(combo, second, secondQty)));
        return combo;
    }

    private static ComboItem item(Product combo, Product component, int quantity) {
        ComboItem item = new ComboItem();
        item.setId(new ComboItemId(combo.getId(), component.getId()));
        item.setCombo(combo);
        item.setComponent(component);
        item.setQuantity(quantity);
        return item;
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=ComboExpanderTest`
Expected: COMPILATION ERROR (`ComboExpander` chưa tồn tại).

- [ ] **Step 3: `M/service/ComboExpander.java`**

```java
package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.Product;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hàm dùng chung DUY NHẤT để quy giỏ/đơn về nhu cầu theo MÓN LẺ (spec combo-sale §4.2) và để biết
 * combo còn bán ở mức chuỗi (§4.3). Tĩnh, không phụ thuộc bean — tồn kho, báo giá, tạo đơn đều gọi.
 */
public final class ComboExpander {

    private ComboExpander() {
    }

    /**
     * Dòng SINGLE giữ nguyên; dòng COMBO × q → mỗi thành phần × (quantity × q). Cùng món (theo id) thì
     * cộng dồn. Thứ tự theo lần xuất hiện đầu tiên; khoá là đối tượng Product gặp đầu tiên.
     */
    public static Map<Product, Integer> expand(Map<Product, Integer> lines) {
        Map<Object, Product> products = new LinkedHashMap<>();
        Map<Object, Integer> quantities = new LinkedHashMap<>();
        for (Map.Entry<Product, Integer> line : lines.entrySet()) {
            Product product = line.getKey();
            Integer quantity = line.getValue();
            if (product == null || quantity == null) {
                continue;
            }
            if (product.isCombo()) {
                for (ComboItem item : product.getComboItems()) {
                    if (item.getComponent() != null) {
                        add(products, quantities, item.getComponent(), item.getQuantity() * quantity);
                    }
                }
            } else {
                add(products, quantities, product, quantity);
            }
        }
        Map<Product, Integer> demand = new LinkedHashMap<>();
        products.forEach((key, product) -> demand.put(product, quantities.get(key)));
        return demand;
    }

    /** Mức chuỗi (thực đơn, thêm giỏ, tạo đơn): combo bán được khi chính nó và mọi thành phần đang bán. */
    public static boolean isChainAvailable(Product product) {
        if (product == null || product.isDeleted() || !product.isAvailable()) {
            return false;
        }
        if (!product.isCombo()) {
            return true;
        }
        List<ComboItem> items = product.getComboItems();
        return !items.isEmpty() && items.stream()
                .map(ComboItem::getComponent)
                .allMatch(component -> component != null && component.isAvailable() && !component.isDeleted());
    }

    private static void add(Map<Object, Product> products, Map<Object, Integer> quantities,
                            Product product, int quantity) {
        Object key = product.getId() != null ? product.getId() : product;
        products.putIfAbsent(key, product);
        quantities.merge(key, quantity, Integer::sum);
    }
}
```

- [ ] **Step 4: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=ComboExpanderTest`
Expected: PASS (4 test).

- [ ] **Step 5: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/service/ComboExpander.java src/test/java/com/banhmyking/banhmyking/service/ComboExpanderTest.java
git commit -m "feat(combo): ComboExpander gộp nhu cầu theo món lẻ và kiểm combo còn bán toàn chuỗi"
```

---

## Task 5: Tồn kho và hết món theo cơ sở cho combo

**Files:**
- Modify: `M/service/impl/InventoryServiceImpl.java` (viết lại toàn bộ), `M/dto/store/StoreStockResponse.java` (viết lại toàn bộ)
- Test: `T/service/InventoryServiceTest.java`

**Interfaces:**
- Consumes: `ComboExpander.expand(...)` (Task 4), `Product.isCombo()/getComboItems()/getProductType()` (Task 1).
- Produces:
  - `InventoryService` giữ nguyên chữ ký (StoreSelectionService, OrderServiceImpl, PaymentServiceImpl không đổi). Hành vi mới: `decreaseForOrder`/`tryDecreaseForOrder` trừ theo nhu cầu gộp món lẻ (thành phần hiện tại của combo — spec §4.2), ghi sổ `ORDER` theo từng món lẻ; `restoreForOrder` không đổi; `unavailableItems(storeId, quantities)` kiểm combo (điều kiện 1) + thành phần (2–3) với nhu cầu gộp, trả `"<tên combo> (hết <thành phần>, ...)"`; `adjustStock` với combo → 400 "Combo không có tồn kho riêng…"; `listStoreStock`/`setAvailability` trả thêm `productType`, `blockedBy`.
  - `StoreStockResponse`: thêm `ProductType productType`, `List<String> blockedBy` (rỗng với món lẻ).

- [ ] **Step 1: Viết test đỏ — thêm vào `InventoryServiceTest`**

Thêm import:

```java
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.times;

import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import com.banhmyking.banhmyking.enums.ProductType;
import org.springframework.data.domain.Sort;
```

Thêm hằng số ngay dưới `private static final Long STORE_ID = 3L;`:

```java
    private static final Long COFFEE_ID = 2L;
    private static final Long COMBO_ID = 50L;
```

Thêm các test (ngay trước dòng `    // --------------------------------------------------------------------- helpers`):

```java
    // ------------------------------------------------------------------ combo

    @Test
    @DisplayName("decreaseForOrder: combo × 2 + bánh mì lẻ → trừ theo món lẻ đã gộp, ghi sổ ORDER từng món")
    void decreaseForOrderExpandsComboIntoComponents() {
        Product banhMi = banhMi();
        Product coffee = coffee();
        Order order = order(combo(banhMi, coffee), 2);
        OrderItem single = new OrderItem();
        single.setProduct(banhMi);
        single.setQuantity(1);
        order.setItems(List.of(order.getItems().get(0), single));
        stubTracked(row(10, true), rowFor(COFFEE_ID, 10, true));
        when(storeProductRepository.decrementStockAtomic(STORE_ID, PRODUCT_ID, 3)).thenReturn(1);
        when(storeProductRepository.decrementStockAtomic(STORE_ID, COFFEE_ID, 4)).thenReturn(1);

        inventoryService.decreaseForOrder(order);

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(m -> m.getProduct().getId(), InventoryMovement::getChangeQty)
                .containsExactlyInAnyOrder(tuple(PRODUCT_ID, -3), tuple(COFFEE_ID, -4));
        verify(storeProductRepository, never()).decrementStockAtomic(eq(STORE_ID), eq(COMBO_ID), anyInt());
    }

    @Test
    @DisplayName("tryDecreaseForOrder: thiếu một thành phần của combo → không trừ gì (cộng trả) và không ghi sổ")
    void tryDecreaseForOrderComboAllOrNothing() {
        Order order = order(combo(banhMi(), coffee()), 1);
        stubTracked(row(10, true), rowFor(COFFEE_ID, 1, true));
        when(storeProductRepository.decrementStockAtomic(STORE_ID, PRODUCT_ID, 1)).thenReturn(1);
        when(storeProductRepository.decrementStockAtomic(STORE_ID, COFFEE_ID, 2)).thenReturn(0);

        assertThat(inventoryService.tryDecreaseForOrder(order)).isFalse();

        verify(storeProductRepository).incrementStockAtomic(STORE_ID, PRODUCT_ID, 1);
        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("unavailableItems: quản lý báo hết chính combo tại cơ sở → trả tên combo")
    void unavailableItemsWhenComboTurnedOffAtStore() {
        Product combo = combo(banhMi(), coffee());
        when(storeProductRepository.findByIdStoreIdAndIdProductIdIn(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of(rowFor(COMBO_ID, null, false)));

        assertThat(inventoryService.unavailableItems(STORE_ID, Map.of(combo, 1)))
                .containsExactly("Combo Sáng no nê");
    }

    @Test
    @DisplayName("unavailableItems: thiếu tồn thành phần theo nhu cầu gộp → 'Combo (hết <món>)'")
    void unavailableItemsNamesBlockingComponent() {
        Product combo = combo(banhMi(), coffee());
        when(storeProductRepository.findByIdStoreIdAndIdProductIdIn(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of(rowFor(COFFEE_ID, 3, true)));

        // combo × 2 cần 4 cà phê, cơ sở chỉ còn 3
        assertThat(inventoryService.unavailableItems(STORE_ID, Map.of(combo, 2)))
                .containsExactly("Combo Sáng no nê (hết Cà phê sữa đá)");
    }

    @Test
    @DisplayName("unavailableItems: combo đủ hàng thì không báo gì")
    void unavailableItemsComboAvailable() {
        when(storeProductRepository.findByIdStoreIdAndIdProductIdIn(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of(rowFor(COFFEE_ID, 10, true)));

        assertThat(inventoryService.unavailableItems(STORE_ID, Map.of(combo(banhMi(), coffee()), 2))).isEmpty();
    }

    @Test
    @DisplayName("adjustStock: combo không có tồn riêng → lỗi nghiệp vụ, không ghi sổ")
    void adjustStockRejectsCombo() {
        when(productRepository.findByIdAndDeletedFalse(COMBO_ID)).thenReturn(Optional.of(combo(banhMi(), coffee())));

        assertThatThrownBy(() -> inventoryService.adjustStock(STORE_ID, COMBO_ID, change(5), 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Combo không có tồn kho riêng");
        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("listStoreStock: combo loại COMBO, không có số tồn, blockedBy = thành phần đang hết")
    void listStoreStockMarksBlockedCombo() {
        Product banhMi = banhMi();
        Product coffee = coffee();
        Product combo = combo(banhMi, coffee);
        when(storeProductRepository.findByIdStoreId(STORE_ID)).thenReturn(List.of(rowFor(COFFEE_ID, null, false)));
        when(productRepository.findAll(any(Sort.class))).thenReturn(List.of(banhMi, coffee, combo));

        List<StoreStockResponse> result = inventoryService.listStoreStock(STORE_ID);

        StoreStockResponse comboRow = result.stream()
                .filter(r -> r.getProductId().equals(COMBO_ID)).findFirst().orElseThrow();
        assertThat(comboRow.getProductType()).isEqualTo(ProductType.COMBO);
        assertThat(comboRow.getBlockedBy()).containsExactly("Cà phê sữa đá");
        assertThat(comboRow.getStockQuantity()).isNull();
        StoreStockResponse banhMiRow = result.stream()
                .filter(r -> r.getProductId().equals(PRODUCT_ID)).findFirst().orElseThrow();
        assertThat(banhMiRow.getProductType()).isEqualTo(ProductType.SINGLE);
        assertThat(banhMiRow.getBlockedBy()).isEmpty();
    }
```

Thêm helper (cuối class, trước `}` cuối file):

```java

    private Product coffee() {
        Product product = new Product();
        product.setId(COFFEE_ID);
        product.setName("Cà phê sữa đá");
        product.setAvailable(true);
        return product;
    }

    /** Combo Sáng no nê = 1 bánh mì + 2 cà phê. */
    private Product combo(Product banhMi, Product coffee) {
        Product combo = new Product();
        combo.setId(COMBO_ID);
        combo.setName("Combo Sáng no nê");
        combo.setProductType(ProductType.COMBO);
        combo.setAvailable(true);
        combo.setComboItems(List.of(comboItem(combo, banhMi, 1), comboItem(combo, coffee, 2)));
        return combo;
    }

    private ComboItem comboItem(Product combo, Product component, int quantity) {
        ComboItem item = new ComboItem();
        item.setId(new ComboItemId(combo.getId(), component.getId()));
        item.setCombo(combo);
        item.setComponent(component);
        item.setQuantity(quantity);
        return item;
    }

    private StoreProduct rowFor(Long productId, Integer stock, boolean available) {
        StoreProduct sp = new StoreProduct();
        sp.setId(new StoreProductId(STORE_ID, productId));
        sp.setAvailable(available);
        sp.setStockQuantity(stock);
        return sp;
    }
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=InventoryServiceTest`
Expected: COMPILATION ERROR (`StoreStockResponse.getProductType()` / `getBlockedBy()` chưa có).

- [ ] **Step 3: Viết lại `M/dto/store/StoreStockResponse.java`**

```java
package com.banhmyking.banhmyking.dto.store;

import com.banhmyking.banhmyking.enums.ProductType;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Một món trong trang "Tình trạng món" của một cơ sở. */
@Getter
@Builder
public class StoreStockResponse {
    private Long productId;
    private String productName;
    private String categoryName;
    private String imageUrl;
    private BigDecimal price;
    /** SINGLE | COMBO — combo không có tồn riêng, chỉ bật/tắt. */
    private ProductType productType;
    /** Món còn trong thực đơn chuỗi (admin tắt = cả chuỗi ngừng bán). */
    private boolean onChainMenu;
    /** Cơ sở đang bán món (không đánh dấu hết món). */
    private boolean available;
    /** NULL = không quản tồn tại cơ sở (combo luôn NULL). */
    private Integer stockQuantity;
    private int lowStockThreshold;
    private boolean lowStock;
    /** Thành phần đang làm combo không bán được tại cơ sở; rỗng với món lẻ. */
    private List<String> blockedBy;
}
```

- [ ] **Step 4: Viết lại `M/service/impl/InventoryServiceImpl.java`**

```java
package com.banhmyking.banhmyking.service.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.banhmyking.banhmyking.dto.catalog.StockChangeRequest;
import com.banhmyking.banhmyking.dto.catalog.StockMovementResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.store.StoreStockResponse;
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.InventoryMovement;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.StoreProduct;
import com.banhmyking.banhmyking.entity.StoreProductId;
import com.banhmyking.banhmyking.enums.InventoryReason;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.InventoryMovementRepository;
import com.banhmyking.banhmyking.repository.ProductRepository;
import com.banhmyking.banhmyking.repository.StoreProductRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.ComboExpander;
import com.banhmyking.banhmyking.service.InventoryService;
import com.banhmyking.banhmyking.util.PageableFactory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final ProductRepository productRepository;
    private final StoreProductRepository storeProductRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decreaseForOrder(Order order) {
        Product shortage = decreaseAllOrNothing(order);
        if (shortage != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Sản phẩm \"" + shortage.getName() + "\" không đủ tồn kho để xác nhận đơn");
        }
    }

    @Override
    public boolean tryDecreaseForOrder(Order order) {
        return decreaseAllOrNothing(order) == null;
    }

    /**
     * Trừ tồn theo NHU CẦU GỘP món lẻ (spec combo-sale §4.2): dòng combo × q → từng thành phần ×
     * (số lượng × q), cộng dồn với dòng món lẻ cùng món. Thiếu ở một món thì cộng trả các món đã trừ
     * và trả về món thiếu; đủ hết thì ghi sổ ORDER theo từng MÓN LẺ (để restoreForOrder hoàn đúng).
     */
    private Product decreaseAllOrNothing(Order order) {
        Long storeId = order.getStore().getId();
        Map<Product, Integer> lines = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null && item.getQuantity() != null) {
                lines.merge(item.getProduct(), item.getQuantity(), Integer::sum);
            }
        }
        Map<Product, Integer> demand = ComboExpander.expand(lines);
        Set<Long> tracked = trackedProductIds(storeId, demand.keySet());
        Map<Product, Integer> decreased = new LinkedHashMap<>();
        for (Map.Entry<Product, Integer> entry : demand.entrySet()) {
            Product product = entry.getKey();
            if (!tracked.contains(product.getId())) {
                continue;
            }
            // Số row = 0 nghĩa là không đủ hàng (hoặc giao dịch khác vừa lấy mất hàng).
            if (storeProductRepository.decrementStockAtomic(storeId, product.getId(), entry.getValue()) == 0) {
                decreased.forEach((done, quantity) ->
                        storeProductRepository.incrementStockAtomic(storeId, done.getId(), quantity));
                return product;
            }
            decreased.put(product, entry.getValue());
        }
        decreased.forEach((product, quantity) ->
                saveMovement(order.getStore(), product, -quantity, InventoryReason.ORDER, order, null, null));
        return null;
    }

    /** Món được quản tồn tại cơ sở = có dòng store_products với stock_quantity khác NULL. */
    private Set<Long> trackedProductIds(Long storeId, Collection<Product> products) {
        List<Long> productIds = products.stream()
                .map(Product::getId).filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) {
            return Set.of();
        }
        return storeProductRepository.findByIdStoreIdAndIdProductIdIn(storeId, productIds).stream()
                .filter(sp -> sp.getStockQuantity() != null)
                .map(sp -> sp.getId().getProductId())
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreForOrder(Order order) {
        Long orderId = order.getId();
        if (orderId == null) {
            return;
        }
        // Hoàn về ĐÚNG cơ sở đã bị trừ — lấy store_id ghi trên sổ ORDER của đơn, không dùng order.store
        // (spec §3.6): nếu đơn từng đổi cơ sở sau khi trừ tồn, order.store đã là cơ sở mới.
        // Sổ ORDER ghi theo món lẻ (kể cả đơn có combo) nên hoàn theo sổ là hoàn đúng thành phần.
        Map<StoreProductId, Integer> quantityByKey = new LinkedHashMap<>();
        Map<StoreProductId, InventoryMovement> sampleByKey = new LinkedHashMap<>();
        for (InventoryMovement decrease : inventoryMovementRepository.findByOrderIdAndReason(orderId, InventoryReason.ORDER)) {
            if (decrease.getStore() == null || decrease.getProduct() == null || decrease.getChangeQty() == null) {
                continue;
            }
            StoreProductId key = new StoreProductId(decrease.getStore().getId(), decrease.getProduct().getId());
            quantityByKey.merge(key, Math.abs(decrease.getChangeQty()), Integer::sum);
            sampleByKey.putIfAbsent(key, decrease);
        }
        for (Map.Entry<StoreProductId, Integer> entry : quantityByKey.entrySet()) {
            StoreProductId key = entry.getKey();
            int quantity = entry.getValue();
            if (quantity <= 0 || inventoryMovementRepository.existsByOrderIdAndStoreIdAndProductIdAndReason(
                    orderId, key.getStoreId(), key.getProductId(), InventoryReason.RESTORE)) {
                continue;
            }
            InventoryMovement sample = sampleByKey.get(key);
            if (storeProductRepository.incrementStockAtomic(key.getStoreId(), key.getProductId(), quantity) > 0) {
                saveMovement(sample.getStore(), sample.getProduct(), quantity,
                        InventoryReason.RESTORE, order, null, null);
            }
        }
    }

    /**
     * Món lẻ: hết món / tắt / thiếu tồn so với nhu cầu gộp. Combo (spec §4.1): (1) chính combo tắt, xoá
     * hoặc bị báo hết tại cơ sở → tên combo; (2–3) thành phần không bán được hoặc thiếu tồn so với nhu
     * cầu gộp của cả giỏ → "Tên combo (hết A, B)".
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> unavailableItems(Long storeId, Map<Product, Integer> quantities) {
        if (quantities.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> demandById = new HashMap<>();
        ComboExpander.expand(quantities).forEach((product, quantity) -> {
            if (product.getId() != null) {
                demandById.merge(product.getId(), quantity, Integer::sum);
            }
        });
        Set<Long> ids = new LinkedHashSet<>(demandById.keySet());
        quantities.keySet().stream().map(Product::getId).filter(Objects::nonNull).forEach(ids::add);
        Map<Long, StoreProduct> rows = rowsOf(storeId, ids);

        List<String> names = new ArrayList<>();
        for (Map.Entry<Product, Integer> entry : quantities.entrySet()) {
            Product product = entry.getKey();
            StoreProduct row = rows.get(product.getId());
            if (!product.isCombo()) {
                int needed = product.getId() != null
                        ? demandById.getOrDefault(product.getId(), entry.getValue())
                        : entry.getValue();
                if (isBlocked(product, row, needed)) {
                    names.add(product.getName());
                }
                continue;
            }
            if (!product.isAvailable() || product.isDeleted() || (row != null && !row.isAvailable())) {
                names.add(product.getName());
                continue;
            }
            List<String> missing = product.getComboItems().stream()
                    .map(ComboItem::getComponent)
                    .filter(Objects::nonNull)
                    .filter(component -> isBlocked(component, rows.get(component.getId()),
                            demandById.getOrDefault(component.getId(), 0)))
                    .map(Product::getName)
                    .sorted()
                    .toList();
            if (!missing.isEmpty()) {
                names.add(product.getName() + " (hết " + String.join(", ", missing) + ")");
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoreStockResponse> listStoreStock(Long storeId) {
        Map<Long, StoreProduct> rows = new HashMap<>();
        for (StoreProduct row : storeProductRepository.findByIdStoreId(storeId)) {
            rows.put(row.getId().getProductId(), row);
        }
        return productRepository.findAll(Sort.by("name")).stream()
                .filter(product -> !product.isDeleted())
                .map(product -> toStockResponse(product, rows.get(product.getId()), blockedBy(product, rows)))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreStockResponse setAvailability(Long storeId, Long productId, boolean available) {
        Product product = requireProduct(productId);
        StoreProduct row = storeProductRepository.findByIdStoreIdAndIdProductId(storeId, productId)
                .orElseGet(() -> newRow(storeId, product));
        row.setAvailable(available);
        StoreProduct saved = storeProductRepository.save(row);
        return toStockResponse(product, saved, comboBlockedBy(storeId, product));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreStockResponse adjustStock(Long storeId, Long productId, StockChangeRequest request, Long actorId) {
        Product product = requireProduct(productId);
        if (product.isCombo()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Combo không có tồn kho riêng — hãy nhập tồn cho từng món trong combo");
        }
        int changeQty = request.getChangeQty();
        if (changeQty == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Số lượng thay đổi phải khác 0");
        }
        StoreProduct row = storeProductRepository.findByIdStoreIdAndIdProductId(storeId, productId).orElse(null);
        Integer current = row == null ? null : row.getStockQuantity();
        if (current == null) {
            // Chưa quản tồn: lần nhập đầu tiên đặt luôn con số ban đầu.
            if (changeQty < 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Món chưa quản tồn tại cơ sở nên không thể giảm; nhập số dương để bắt đầu quản.");
            }
            if (row == null) {
                row = newRow(storeId, product);
            }
            row.setStockQuantity(changeQty);
            row = storeProductRepository.save(row);
        } else if (changeQty > 0) {
            storeProductRepository.incrementStockAtomic(storeId, productId, changeQty);
            // Bulk UPDATE không đụng tới entity đang managed; không đọc lại thì response
            // trong cùng request (open-in-view) trả về số tồn cũ.
            entityManager.refresh(row);
        } else {
            if (storeProductRepository.decrementStockAtomic(storeId, productId, -changeQty) == 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Tồn kho không đủ để giảm " + (-changeQty) + " (đang có " + current + ")");
            }
            entityManager.refresh(row);
        }
        saveMovement(row.getStore(), product, changeQty,
                changeQty > 0 ? InventoryReason.IMPORT : InventoryReason.ADJUST, null, request.getNote(), actorId);
        return toStockResponse(product, row, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> getMovements(Long storeId, Long productId, int page, int size) {
        Pageable pageable = PageableFactory.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(inventoryMovementRepository
                .findPageByStoreIdAndProductId(storeId, productId, pageable)
                .map(this::toMovementResponse));
    }

    private Product requireProduct(Long productId) {
        return productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
    }

    private StoreProduct newRow(Long storeId, Product product) {
        StoreProduct row = new StoreProduct();
        row.setId(new StoreProductId(storeId, product.getId()));
        row.setStore(entityManager.getReference(Store.class, storeId));
        row.setProduct(product);
        return row;
    }

    /** HashMap (không dùng Map.of) để get(null) an toàn với Product chưa có id. */
    private Map<Long, StoreProduct> rowsOf(Long storeId, Collection<Long> productIds) {
        Map<Long, StoreProduct> rows = new HashMap<>();
        if (productIds.isEmpty()) {
            return rows;
        }
        for (StoreProduct row : storeProductRepository.findByIdStoreIdAndIdProductIdIn(storeId, productIds)) {
            rows.put(row.getId().getProductId(), row);
        }
        return rows;
    }

    /** Món lẻ không bán được tại cơ sở: tắt toàn chuỗi, đã xoá, bị báo hết, hoặc tồn < nhu cầu. */
    private static boolean isBlocked(Product product, StoreProduct row, int demand) {
        boolean soldOut = !product.isAvailable() || product.isDeleted() || (row != null && !row.isAvailable());
        boolean shortStock = row != null && row.getStockQuantity() != null && row.getStockQuantity() < demand;
        return soldOut || shortStock;
    }

    /** Thành phần đang làm MỘT combo không bán được tại cơ sở; món lẻ → rỗng. */
    private List<String> blockedBy(Product product, Map<Long, StoreProduct> rows) {
        if (!product.isCombo()) {
            return List.of();
        }
        return product.getComboItems().stream()
                .filter(item -> item.getComponent() != null)
                .filter(item -> isBlocked(item.getComponent(), rows.get(item.getComponent().getId()),
                        item.getQuantity()))
                .map(item -> item.getComponent().getName())
                .sorted()
                .toList();
    }

    private List<String> comboBlockedBy(Long storeId, Product product) {
        if (!product.isCombo()) {
            return List.of();
        }
        List<Long> componentIds = product.getComboItems().stream()
                .map(ComboItem::getComponent).filter(Objects::nonNull).map(Product::getId).toList();
        return blockedBy(product, rowsOf(storeId, componentIds));
    }

    private StoreStockResponse toStockResponse(Product product, StoreProduct row, List<String> blockedBy) {
        boolean combo = product.isCombo();
        return StoreStockResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .categoryName(product.getCategory() == null ? null : product.getCategory().getName())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .productType(product.getProductType())
                .onChainMenu(product.isAvailable())
                .available(row == null || row.isAvailable())
                .stockQuantity(combo || row == null ? null : row.getStockQuantity())
                .lowStockThreshold(row == null ? 5 : row.getLowStockThreshold())
                .lowStock(!combo && row != null && row.isLowStock())
                .blockedBy(blockedBy)
                .build();
    }

    private void saveMovement(Store store, Product product, int changeQty, InventoryReason reason,
                              Order order, String note, Long actorId) {
        InventoryMovement movement = new InventoryMovement();
        movement.setStore(store);
        movement.setProduct(product);
        movement.setChangeQty(changeQty);
        movement.setReason(reason);
        movement.setOrder(order);
        movement.setNote(note);
        if (actorId != null) {
            movement.setCreatedBy(userRepository.getReferenceById(actorId));
        }
        inventoryMovementRepository.save(movement);
    }

    private StockMovementResponse toMovementResponse(InventoryMovement movement) {
        return StockMovementResponse.builder()
                .id(movement.getId())
                .changeQty(movement.getChangeQty())
                .reason(movement.getReason())
                .orderCode(movement.getOrder() == null ? null : movement.getOrder().getOrderCode())
                .note(movement.getNote())
                .createdAt(movement.getCreatedAt())
                .build();
    }
}
```

- [ ] **Step 5: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=InventoryServiceTest,StoreSelectionServiceTest`
Expected: PASS (test cũ giữ hành vi + 7 test combo mới).

- [ ] **Step 6: Chạy toàn bộ test**

Run: `./mvnw -B clean test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/service/impl/InventoryServiceImpl.java src/main/java/com/banhmyking/banhmyking/dto/store/StoreStockResponse.java src/test/java/com/banhmyking/banhmyking/service/InventoryServiceTest.java
git commit -m "feat(combo): trừ/hoàn tồn theo thành phần combo, báo combo tạm hết theo cơ sở, chặn nhập tồn combo"
```

---

## Task 6: Thực đơn đọc — giá KM, giá gạch, thành phần combo, lọc `onSale` / `type`

**Files:**
- Create: `M/dto/catalog/ComboItemResponse.java`
- Modify: `M/dto/catalog/ProductResponse.java`, `M/service/CatalogService.java`, `M/service/impl/CatalogServiceImpl.java`, `M/repository/specification/ProductSpecifications.java`, `M/controller/CatalogController.java`
- Test: `T/service/CatalogServiceTest.java`

**Interfaces:**
- Consumes: `ProductPricing` (Task 2), `ComboExpander.isChainAvailable` (Task 4), `ProductType` (Task 1).
- Produces:
  - `ComboItemResponse` (`productId, name, imageUrl, price, quantity`) + `static List<ComboItemResponse> listOf(Product product)` (combo → thành phần sắp theo tên; món lẻ → `List.of()`). Task 8 dùng lại.
  - `ProductResponse` thêm: `boolean enabled` (cờ `is_available` của chính sản phẩm — dùng cho nút bật/tắt của admin), `ProductType productType`, `BigDecimal salePrice`, `LocalDateTime saleStartsAt`, `LocalDateTime saleEndsAt`, `BigDecimal effectivePrice`, `BigDecimal compareAtPrice`, `Integer discountPercent`, `boolean onSale`, `List<ComboItemResponse> comboItems`. `available` của combo = combo bật ∧ mọi thành phần bật và chưa xoá.
  - `CatalogService.getProducts(Long categoryId, boolean availableOnly, String keyword, Boolean featured, BigDecimal minPrice, BigDecimal maxPrice, Boolean onSale, ProductType type, ProductSort sort, int page, int size)`.
  - `ProductSpecifications.search(Long, boolean, String, Boolean, BigDecimal, BigDecimal, Boolean onSale, ProductType type, LocalDateTime now)`; overload 6 tham số cũ giữ nguyên (gọi bản mới với `null, null, null`).
  - `GET /api/v1/catalog/products?onSale=true&type=SINGLE|COMBO`.
  - `CatalogServiceImpl` có field `productPricing`.

- [ ] **Step 1: Viết test đỏ — `CatalogServiceTest`**

Thêm import:

```java
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.mockito.Spy;
import com.banhmyking.banhmyking.config.TimeConfig;
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import com.banhmyking.banhmyking.enums.ProductType;
```

Ngay sau khối `@Mock private FileStorageService fileStorageService;` thêm:

```java
    /** 10:00 02/10/2026 giờ Việt Nam. */
    @Spy
    private ProductPricing productPricing = new ProductPricing(
            Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), TimeConfig.VIETNAM));
```

Sửa 2 lời gọi cũ (thêm 2 tham số `null, null` cho `onSale`, `type`):
- `catalogService.getProducts(null, true, null, null, null, null, ProductSort.FEATURED, 0, 12)` → `catalogService.getProducts(null, true, null, null, null, null, null, null, ProductSort.FEATURED, 0, 12)`
- `catalogService.getProducts(null, true, null, null, null, null, ProductSort.PRICE_DESC, 2, 999)` → `catalogService.getProducts(null, true, null, null, null, null, null, null, ProductSort.PRICE_DESC, 2, 999)`

Thêm test mới (cuối class, trước `}` cuối file):

```java

    // ─── Giá KM + combo trên thực đơn (spec combo-sale §6.1) ─────────────────

    private static Category comboCategory() {
        Category category = new Category();
        category.setId(5L);
        category.setName("Mặn");
        return category;
    }

    private static Product menuProduct(Long id, String name, String price) {
        Product product = new Product();
        product.setId(id);
        product.setCategory(comboCategory());
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setAvailable(true);
        return product;
    }

    private static Product comboOf(Long id, String price, Product... components) {
        Product combo = menuProduct(id, "Combo Sáng no nê", price);
        combo.setProductType(ProductType.COMBO);
        List<ComboItem> items = new ArrayList<>();
        for (Product component : components) {
            ComboItem item = new ComboItem();
            item.setId(new ComboItemId(id, component.getId()));
            item.setCombo(combo);
            item.setComponent(component);
            item.setQuantity(1);
            items.add(item);
        }
        combo.setComboItems(items);
        return combo;
    }

    @Test
    void getProductsMapsSalePricingAndComboComponents() {
        Product banhMi = menuProduct(10L, "Bánh mì", "30000");
        banhMi.setSalePrice(new BigDecimal("25000"));
        Product coffee = menuProduct(11L, "Cà phê", "20000");
        Product combo = comboOf(12L, "40000", banhMi, coffee);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(banhMi, combo), PageRequest.of(0, 12), 2));

        PageResponse<ProductResponse> result =
                catalogService.getProducts(null, true, null, null, null, null, null, null, ProductSort.FEATURED, 0, 12);

        ProductResponse sale = result.content().get(0);
        assertThat(sale.getProductType()).isEqualTo(ProductType.SINGLE);
        assertThat(sale.isOnSale()).isTrue();
        assertThat(sale.getPrice()).isEqualByComparingTo("30000");
        assertThat(sale.getEffectivePrice()).isEqualByComparingTo("25000");
        assertThat(sale.getCompareAtPrice()).isEqualByComparingTo("30000");
        assertThat(sale.getDiscountPercent()).isEqualTo(16);
        assertThat(sale.getComboItems()).isEmpty();

        ProductResponse comboResponse = result.content().get(1);
        assertThat(comboResponse.getProductType()).isEqualTo(ProductType.COMBO);
        assertThat(comboResponse.isOnSale()).isFalse();
        assertThat(comboResponse.getEffectivePrice()).isEqualByComparingTo("40000");
        // giá gốc combo = giá GỐC thành phần: 30.000 + 20.000 (không dùng giá KM 25.000 của bánh mì)
        assertThat(comboResponse.getCompareAtPrice()).isEqualByComparingTo("50000");
        assertThat(comboResponse.getDiscountPercent()).isEqualTo(20);
        assertThat(comboResponse.getComboItems())
                .extracting(ComboItemResponse::getName, ComboItemResponse::getQuantity)
                .containsExactly(tuple("Bánh mì", 1), tuple("Cà phê", 1));
        assertThat(comboResponse.isAvailable()).isTrue();
    }

    @Test
    void comboWithDisabledComponentIsUnavailableButStillEnabled() {
        Product coffee = menuProduct(11L, "Cà phê", "20000");
        coffee.setAvailable(false);
        Product combo = comboOf(12L, "40000", menuProduct(10L, "Bánh mì", "30000"), coffee);
        when(productRepository.findByIdAndDeletedFalse(12L)).thenReturn(Optional.of(combo));

        ProductResponse response = catalogService.getProduct(12L);

        assertThat(response.isAvailable()).isFalse();
        assertThat(response.isEnabled()).isTrue();
    }

    @Test
    void searchOnSaleAddsSaleWindowPredicates() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        Root<Product> root = mock(Root.class);
        @SuppressWarnings("unchecked")
        Path<Object> path = mock(Path.class);
        when(root.get(anyString())).thenReturn(path);
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 10, 0);

        ProductSpecifications.search(null, true, null, null, null, null, true, null, now)
                .toPredicate(root, null, cb);

        verify(cb).equal(path, ProductType.SINGLE);
        verify(cb).isNotNull(path);
        verify(cb).lessThanOrEqualTo(any(), eq(now));
        verify(cb).greaterThan(any(), eq(now));
    }

    @Test
    void searchTypeFilterAddsEqualityOnly() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        Root<Product> root = mock(Root.class);
        @SuppressWarnings("unchecked")
        Path<Object> path = mock(Path.class);
        when(root.get(anyString())).thenReturn(path);

        ProductSpecifications.search(null, false, null, null, null, null, null, ProductType.COMBO,
                LocalDateTime.of(2026, 10, 2, 10, 0)).toPredicate(root, null, cb);

        verify(cb).equal(path, ProductType.COMBO);
        verify(cb, never()).isNotNull(any());
    }
```

(Import thêm `com.banhmyking.banhmyking.dto.catalog.ComboItemResponse`.)

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=CatalogServiceTest`
Expected: COMPILATION ERROR (`getProducts` 11 tham số, `ComboItemResponse`, `ProductResponse.getEffectivePrice()`… chưa có).

- [ ] **Step 3: `M/dto/catalog/ComboItemResponse.java`**

```java
package com.banhmyking.banhmyking.dto.catalog;

import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.Product;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Một món lẻ trong combo — dùng ở thực đơn và giỏ hàng. */
@Getter
@Builder
public class ComboItemResponse {
    private Long productId;
    private String name;
    private String imageUrl;
    /** Giá gốc của món lẻ (không phải giá KM). */
    private BigDecimal price;
    /** Số phần trong một combo. */
    private int quantity;

    /** Thành phần sắp theo tên; món lẻ → rỗng. */
    public static List<ComboItemResponse> listOf(Product product) {
        if (product == null || !product.isCombo()) {
            return List.of();
        }
        return product.getComboItems().stream()
                .filter(item -> item.getComponent() != null)
                .sorted(Comparator.comparing((ComboItem item) -> item.getComponent().getName()))
                .map(item -> ComboItemResponse.builder()
                        .productId(item.getComponent().getId())
                        .name(item.getComponent().getName())
                        .imageUrl(item.getComponent().getImageUrl())
                        .price(item.getComponent().getPrice())
                        .quantity(item.getQuantity())
                        .build())
                .toList();
    }
}
```

- [ ] **Step 4: Sửa `M/dto/catalog/ProductResponse.java`**

Thêm import `com.banhmyking.banhmyking.enums.ProductType;` và `java.time.LocalDateTime;`. Thay:

```java
    private BigDecimal price;
    private boolean available;
```

bằng:

```java
    /** Giá gốc (giá niêm yết). Giá đang bán xem effectivePrice. */
    private BigDecimal price;
    /** Khách đặt được ở mức chuỗi: món bật; combo còn cần mọi thành phần bật và chưa xoá. */
    private boolean available;
    /** Cờ bật/tắt của CHÍNH sản phẩm (is_available) — nút bật/tắt của admin dùng cờ này. */
    private boolean enabled;
    /** SINGLE | COMBO */
    private ProductType productType;
    /** Giá KM đã cấu hình (kể cả chưa tới hạn / đã hết) — chỉ món lẻ. */
    private BigDecimal salePrice;
    /** Giờ Việt Nam; null = áp dụng ngay. */
    private LocalDateTime saleStartsAt;
    /** Giờ Việt Nam; null = không hết hạn. */
    private LocalDateTime saleEndsAt;
    /** Giá đang bán lúc trả response (server là nguồn sự thật). */
    private BigDecimal effectivePrice;
    /** Giá gạch; null = không gạch giá. */
    private BigDecimal compareAtPrice;
    /** % giảm làm tròn xuống; null nếu không giảm. */
    private Integer discountPercent;
    /** Món lẻ đang trong thời gian KM. */
    private boolean onSale;
    /** Thành phần combo; rỗng với món lẻ. */
    private List<ComboItemResponse> comboItems;
```

- [ ] **Step 5: Sửa `M/repository/specification/ProductSpecifications.java`**

Thêm import `com.banhmyking.banhmyking.enums.ProductType;` và `java.time.LocalDateTime;`. Thay chữ ký + dòng đầu của `search` cũ:

```java
    public static Specification<Product> search(Long categoryId, boolean availableOnly,
                                                String keyword, Boolean featured,
                                                BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
```

bằng:

```java
    public static Specification<Product> search(Long categoryId, boolean availableOnly,
                                                String keyword, Boolean featured,
                                                BigDecimal minPrice, BigDecimal maxPrice) {
        return search(categoryId, availableOnly, keyword, featured, minPrice, maxPrice, null, null, null);
    }

    /**
     * Như trên, thêm lọc loại sản phẩm và "đang khuyến mãi" (spec combo-sale §6.1): món lẻ có giá KM và
     * {@code now} nằm trong [saleStartsAt, saleEndsAt) — cùng điều kiện với ProductPricing.isSaleActive.
     */
    public static Specification<Product> search(Long categoryId, boolean availableOnly,
                                                String keyword, Boolean featured,
                                                BigDecimal minPrice, BigDecimal maxPrice,
                                                Boolean onSale, ProductType type, LocalDateTime now) {
        return (root, query, cb) -> {
```

và ngay trước dòng `            if (keyword != null && !keyword.isBlank()) {` thêm:

```java
            if (type != null) {
                predicates.add(cb.equal(root.get("productType"), type));
            }
            if (Boolean.TRUE.equals(onSale) && now != null) {
                predicates.add(cb.equal(root.get("productType"), ProductType.SINGLE));
                predicates.add(cb.isNotNull(root.get("salePrice")));
                predicates.add(cb.or(cb.isNull(root.get("saleStartsAt")),
                        cb.lessThanOrEqualTo(root.<LocalDateTime>get("saleStartsAt"), now)));
                predicates.add(cb.or(cb.isNull(root.get("saleEndsAt")),
                        cb.greaterThan(root.<LocalDateTime>get("saleEndsAt"), now)));
            }

```

- [ ] **Step 6: Sửa `M/service/CatalogService.java`**

Thêm import `com.banhmyking.banhmyking.enums.ProductType;`. Thay chữ ký `getProducts` bằng:

```java
    /**
     * Tìm/lọc/sắp xếp thực đơn ở phía server. {@code keyword}, {@code featured}, khoảng giá,
     * {@code onSale}, {@code type} bỏ trống = không lọc theo tiêu chí đó.
     */
    PageResponse<ProductResponse> getProducts(Long categoryId, boolean availableOnly, String keyword,
                                              Boolean featured, BigDecimal minPrice, BigDecimal maxPrice,
                                              Boolean onSale, ProductType type,
                                              ProductSort sort, int page, int size);
```

- [ ] **Step 7: Sửa `M/service/impl/CatalogServiceImpl.java`**

Thêm import:

```java
import java.time.LocalDateTime;

import com.banhmyking.banhmyking.dto.catalog.ComboItemResponse;
import com.banhmyking.banhmyking.enums.ProductType;
import com.banhmyking.banhmyking.service.ComboExpander;
import com.banhmyking.banhmyking.service.ProductPricing;
```

Ngay sau `    private final ReviewRepository reviewRepository;` thêm:

```java
    private final ProductPricing productPricing;
```

Thay toàn bộ phương thức `getProducts` bằng:

```java
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getProducts(Long categoryId, boolean availableOnly, String keyword,
                                                     Boolean featured, BigDecimal minPrice, BigDecimal maxPrice,
                                                     Boolean onSale, ProductType type,
                                                     ProductSort sort, int page, int size) {
        Page<Product> result = productRepository.findAll(
                ProductSpecifications.search(categoryId, availableOnly, keyword, featured, minPrice, maxPrice,
                        onSale, type, productPricing.now()),
                PageableFactory.of(page, size, sort.toSort()));

        // Bọc lại PageImpl để enrich cả trang trong 1 lượt — map từng món riêng sẽ thành N+1
        List<ProductResponse> content = toProductResponses(result.getContent());
        return PageResponse.from(new PageImpl<>(content, result.getPageable(), result.getTotalElements()));
    }
```

Trong `toProductResponse(Product product, Double averageRating, ...)` (bản 6 tham số), thay:

```java
            List<String> images, List<ProductOption> options, List<OptionGroup> groups) {
        return ProductResponse.builder()
```

bằng:

```java
            List<String> images, List<ProductOption> options, List<OptionGroup> groups) {
        LocalDateTime now = productPricing.now();
        return ProductResponse.builder()
```

và thay:

```java
                .price(product.getPrice())
                .available(product.isAvailable())
                .featured(product.isFeatured())
```

bằng:

```java
                .price(product.getPrice())
                .available(ComboExpander.isChainAvailable(product))
                .enabled(product.isAvailable())
                .featured(product.isFeatured())
                .productType(product.getProductType())
                .salePrice(product.getSalePrice())
                .saleStartsAt(product.getSaleStartsAt())
                .saleEndsAt(product.getSaleEndsAt())
                .effectivePrice(productPricing.effectivePrice(product, now))
                .compareAtPrice(productPricing.compareAtPrice(product, now))
                .discountPercent(productPricing.discountPercent(product, now))
                .onSale(productPricing.isSaleActive(product, now))
                .comboItems(ComboItemResponse.listOf(product))
```

- [ ] **Step 8: Sửa `M/controller/CatalogController.java`**

Thêm import `com.banhmyking.banhmyking.enums.ProductType;`. Trong `getProducts`, ngay sau tham số `maxPrice` thêm:

```java
            @Parameter(description = "Chỉ món lẻ đang trong thời gian khuyến mãi", example = "true")
            @RequestParam(required = false) Boolean onSale,
            @Parameter(description = "Lọc theo loại: SINGLE (món lẻ) hoặc COMBO")
            @RequestParam(required = false) ProductType type,
```

và thay lời gọi service:

```java
                catalogService.getProducts(categoryId, availableOnly, keyword, featured,
                        minPrice, maxPrice, sort, page, size)));
```

bằng:

```java
                catalogService.getProducts(categoryId, availableOnly, keyword, featured,
                        minPrice, maxPrice, onSale, type, sort, page, size)));
```

- [ ] **Step 9: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=CatalogServiceTest,OptionGroupSyncIntegrationTest`
Expected: PASS (test cũ + 4 test mới).

- [ ] **Step 10: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/dto/catalog/ComboItemResponse.java src/main/java/com/banhmyking/banhmyking/dto/catalog/ProductResponse.java src/main/java/com/banhmyking/banhmyking/service/CatalogService.java src/main/java/com/banhmyking/banhmyking/service/impl/CatalogServiceImpl.java src/main/java/com/banhmyking/banhmyking/repository/specification/ProductSpecifications.java src/main/java/com/banhmyking/banhmyking/controller/CatalogController.java src/test/java/com/banhmyking/banhmyking/service/CatalogServiceTest.java
git commit -m "feat(thuc-don): trả giá đang bán, giá gạch, % giảm, thành phần combo; lọc đang khuyến mãi và loại sản phẩm"
```

---

## Task 7: Thực đơn ghi — ràng buộc giá KM, tạo/sửa combo, chặn xoá món đang trong combo

**Files:**
- Create: `M/dto/catalog/ComboItemRequest.java`
- Modify: `M/dto/catalog/ProductRequest.java`, `M/service/impl/CatalogServiceImpl.java`
- Test: `T/service/CatalogServiceTest.java`, `T/repository/ComboCatalogIntegrationTest.java` (mới)

**Interfaces:**
- Consumes: `ComboItemRepository` (`saveAll`, `deleteAll`, `findActiveComboNamesContaining`) (Task 1), `ProductPricing.originalPrice` (Task 2), `ComboItemResponse.listOf` (Task 6), `productRepository.findAllById(Iterable<Long>)`.
- Produces:
  - `ComboItemRequest { Long productId; Integer quantity; }` (bean validation 1–20).
  - `ProductRequest` thêm: `ProductType productType` (chỉ khi tạo; null = SINGLE), `BigDecimal salePrice`, `LocalDateTime saleStartsAt`, `LocalDateTime saleEndsAt`, `List<ComboItemRequest> comboItems` (sửa combo mà bỏ trống = giữ nguyên thành phần).
  - Thông báo lỗi (400, tiếng Việt): "Giá khuyến mãi phải lớn hơn 0", "Giá khuyến mãi phải nhỏ hơn giá gốc", "Thời điểm kết thúc khuyến mãi phải sau thời điểm bắt đầu", "Combo không dùng giá khuyến mãi — hãy đặt thẳng giá combo", "Combo không có topping hay nhóm lựa chọn", "Chỉ combo mới có món thành phần", "Combo phải có ít nhất một món thành phần", "Combo phải có tổng ít nhất 2 phần món", "Số lượng mỗi món trong combo từ 1 đến 20", "Món thành phần bị trùng trong combo — hãy gộp số lượng vào một dòng", "Không tìm thấy món thành phần với ID: …", "Thành phần combo phải là món lẻ: …", "Giá combo phải thấp hơn tổng giá lẻ của các món (…đ)", "Không thể đổi loại sản phẩm đã tạo (món lẻ ↔ combo)", "Món đang nằm trong combo: … — hãy sửa hoặc xoá combo trước".
  - `CatalogServiceImpl` có field `comboItemRepository`.

- [ ] **Step 1: Viết unit test đỏ — `CatalogServiceTest`**

Thêm import `com.banhmyking.banhmyking.dto.catalog.ComboItemRequest;`, `com.banhmyking.banhmyking.repository.ComboItemRepository;`. Ngay sau field `productPricing` (Task 6) thêm:

```java
    @Mock
    private ComboItemRepository comboItemRepository;
```

Thêm test (cuối class):

```java

    // ─── Ràng buộc khi lưu (spec combo-sale §3) ─────────────────────────────

    private static ProductRequest singleRequest(String price) {
        ProductRequest request = new ProductRequest();
        request.setCategoryId(5L);
        request.setName("Bánh mì");
        request.setPrice(new BigDecimal(price));
        return request;
    }

    private static ComboItemRequest comboLine(Long productId, int quantity) {
        ComboItemRequest line = new ComboItemRequest();
        line.setProductId(productId);
        line.setQuantity(quantity);
        return line;
    }

    private static ProductRequest comboRequest(String price, ComboItemRequest... lines) {
        ProductRequest request = singleRequest(price);
        request.setName("Combo Sáng no nê");
        request.setProductType(ProductType.COMBO);
        request.setComboItems(new ArrayList<>(List.of(lines)));
        return request;
    }

    private void stubCategory() {
        when(categoryRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(comboCategory()));
    }

    @Test
    void createSingleRejectsSalePriceNotBelowPrice() {
        stubCategory();
        ProductRequest request = singleRequest("30000");
        request.setSalePrice(new BigDecimal("30000"));

        assertThatThrownBy(() -> catalogService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nhỏ hơn giá gốc");
        verify(productRepository, never()).save(any());
    }

    @Test
    void createSingleRejectsNonPositiveSalePrice() {
        stubCategory();
        ProductRequest request = singleRequest("30000");
        request.setSalePrice(BigDecimal.ZERO);

        assertThatThrownBy(() -> catalogService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("lớn hơn 0");
    }

    @Test
    void createSingleRejectsSaleEndNotAfterStart() {
        stubCategory();
        ProductRequest request = singleRequest("30000");
        request.setSalePrice(new BigDecimal("25000"));
        request.setSaleStartsAt(LocalDateTime.of(2026, 10, 5, 8, 0));
        request.setSaleEndsAt(LocalDateTime.of(2026, 10, 5, 8, 0));

        assertThatThrownBy(() -> catalogService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("kết thúc khuyến mãi phải sau");
    }

    @Test
    void createSingleWithActiveSaleIsOnSale() {
        stubCategory();
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product saved = inv.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        ProductRequest request = singleRequest("30000");
        request.setSalePrice(new BigDecimal("25000"));
        request.setSaleStartsAt(LocalDateTime.of(2026, 10, 1, 0, 0));

        ProductResponse response = catalogService.createProduct(request);

        assertThat(response.isOnSale()).isTrue();
        assertThat(response.getEffectivePrice()).isEqualByComparingTo("25000");
        assertThat(response.getProductType()).isEqualTo(ProductType.SINGLE);
    }

    @Test
    void updateWithoutSalePriceClearsSaleWindow() {
        Product product = menuProduct(10L, "Bánh mì", "30000");
        product.setSalePrice(new BigDecimal("25000"));
        product.setSaleEndsAt(LocalDateTime.of(2026, 10, 31, 22, 0));
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(product));
        stubCategory();
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = catalogService.updateProduct(10L, singleRequest("30000"));

        assertThat(product.getSalePrice()).isNull();
        assertThat(product.getSaleEndsAt()).isNull();
        assertThat(response.isOnSale()).isFalse();
    }

    @Test
    void createSingleRejectsComboItems() {
        stubCategory();
        ProductRequest request = singleRequest("30000");
        request.setComboItems(new ArrayList<>(List.of(comboLine(11L, 2))));

        assertThatThrownBy(() -> catalogService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chỉ combo");
    }

    @Test
    void createComboRejectsSalePrice() {
        stubCategory();
        ProductRequest request = comboRequest("45000", comboLine(10L, 1), comboLine(11L, 1));
        request.setSalePrice(new BigDecimal("40000"));

        assertThatThrownBy(() -> catalogService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Combo không dùng giá khuyến mãi");
    }

    @Test
    void createComboRejectsToppings() {
        stubCategory();
        ProductRequest request = comboRequest("45000", comboLine(10L, 1), comboLine(11L, 1));
        ProductOptionRequest topping = new ProductOptionRequest();
        topping.setName("Thêm pate");
        topping.setExtraPrice(new BigDecimal("5000"));
        request.setOptions(new ArrayList<>(List.of(topping)));

        assertThatThrownBy(() -> catalogService.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Combo không có topping");
    }

    @Test
    void createComboNeedsAtLeastTwoPortions() {
        stubCategory();

        assertThatThrownBy(() -> catalogService.createProduct(comboRequest("20000", comboLine(10L, 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất 2 phần");
    }

    @Test
    void createComboRejectsDuplicateComponent() {
        stubCategory();

        assertThatThrownBy(() -> catalogService.createProduct(
                comboRequest("45000", comboLine(10L, 1), comboLine(10L, 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("bị trùng");
    }

    @Test
    void createComboRejectsComboAsComponent() {
        stubCategory();
        Product nested = comboOf(20L, "40000", menuProduct(10L, "Bánh mì", "30000"));
        when(productRepository.findAllById(any())).thenReturn(List.of(nested, menuProduct(11L, "Cà phê", "20000")));

        assertThatThrownBy(() -> catalogService.createProduct(
                comboRequest("45000", comboLine(20L, 1), comboLine(11L, 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("phải là món lẻ");
    }

    @Test
    void createComboRejectsPriceNotBelowSumOfParts() {
        stubCategory();
        when(productRepository.findAllById(any())).thenReturn(
                List.of(menuProduct(10L, "Bánh mì", "30000"), menuProduct(11L, "Cà phê", "20000")));

        assertThatThrownBy(() -> catalogService.createProduct(
                comboRequest("50000", comboLine(10L, 1), comboLine(11L, 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("thấp hơn tổng giá lẻ");
        verify(productRepository, never()).save(any());
    }

    @Test
    void createComboSavesComponentsAndReturnsCompareAt() {
        stubCategory();
        when(productRepository.findAllById(any())).thenReturn(
                List.of(menuProduct(10L, "Bánh mì", "30000"), menuProduct(11L, "Cà phê", "20000")));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product saved = inv.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        ProductResponse response = catalogService.createProduct(
                comboRequest("45000", comboLine(10L, 1), comboLine(11L, 2)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<ComboItem>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(comboItemRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(item -> item.getId().getComboId(), item -> item.getId().getComponentId(),
                        ComboItem::getQuantity)
                .containsExactly(tuple(99L, 10L, 1), tuple(99L, 11L, 2));
        assertThat(response.getProductType()).isEqualTo(ProductType.COMBO);
        assertThat(response.getCompareAtPrice()).isEqualByComparingTo("70000");
        assertThat(response.getComboItems()).hasSize(2);
    }

    @Test
    void updateRejectsChangingProductType() {
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(menuProduct(10L, "Bánh mì", "30000")));
        ProductRequest request = singleRequest("30000");
        request.setProductType(ProductType.COMBO);

        assertThatThrownBy(() -> catalogService.updateProduct(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không thể đổi loại sản phẩm");
    }

    @Test
    void deleteSingleUsedByActiveComboIsBlocked() {
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(menuProduct(10L, "Bánh mì", "30000")));
        when(comboItemRepository.findActiveComboNamesContaining(10L)).thenReturn(List.of("Combo Sáng no nê"));

        assertThatThrownBy(() -> catalogService.deleteProduct(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Món đang nằm trong combo: Combo Sáng no nê");
        verify(productRepository, never()).save(any());
    }
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=CatalogServiceTest`
Expected: COMPILATION ERROR (`ComboItemRequest`, `ProductRequest.setSalePrice/...` chưa có).

- [ ] **Step 3: `M/dto/catalog/ComboItemRequest.java`**

```java
package com.banhmyking.banhmyking.dto.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Một dòng thành phần khi tạo/sửa combo. */
@Getter
@Setter
public class ComboItemRequest {

    @NotNull(message = "Thiếu món thành phần")
    private Long productId;

    @NotNull(message = "Thiếu số lượng món trong combo")
    @Min(value = 1, message = "Số lượng mỗi món trong combo từ 1 đến 20")
    @Max(value = 20, message = "Số lượng mỗi món trong combo từ 1 đến 20")
    private Integer quantity;
}
```

- [ ] **Step 4: Sửa `M/dto/catalog/ProductRequest.java`**

Thêm import `com.banhmyking.banhmyking.enums.ProductType;` và `java.time.LocalDateTime;`. Ngay sau `    private boolean featured = false;` thêm:

```java

    /** Chỉ dùng khi TẠO (bỏ trống = SINGLE). Khi sửa: bỏ trống hoặc phải trùng loại hiện có. */
    private ProductType productType;

    /**
     * Giá khuyến mãi — chỉ món lẻ. Bỏ trống = không khuyến mãi (xoá KM đang có, kể cả hai mốc thời gian).
     * Phải > 0 và < price (kiểm ở service để giữ thông báo tiếng Việt).
     */
    private BigDecimal salePrice;

    /** Giờ Việt Nam; bỏ trống = áp dụng ngay. */
    private LocalDateTime saleStartsAt;

    /** Giờ Việt Nam; bỏ trống = không hết hạn. Phải sau saleStartsAt khi có cả hai. */
    private LocalDateTime saleEndsAt;

    /**
     * Thành phần combo. Tạo combo: bắt buộc. Sửa combo: bỏ trống = giữ nguyên thành phần; gửi mảng =
     * thay toàn bộ. Món lẻ: phải bỏ trống hoặc rỗng.
     */
    @Valid
    @Size(max = 20, message = "Combo tối đa 20 món thành phần")
    private List<ComboItemRequest> comboItems;
```

- [ ] **Step 5: Sửa `M/service/impl/CatalogServiceImpl.java`**

Thêm import:

```java
import java.util.stream.Collectors;

import com.banhmyking.banhmyking.dto.catalog.ComboItemRequest;
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import com.banhmyking.banhmyking.repository.ComboItemRepository;
```

Ngay sau `    private final ProductPricing productPricing;` thêm:

```java
    private final ComboItemRepository comboItemRepository;
```

Thay `createProduct` (thân hàm):

```java
    public ProductResponse createProduct(ProductRequest request) {
        Product product = new Product();
        applyProduct(product, request);
        return saveProductWithOptions(product, request);
    }
```

bằng:

```java
    public ProductResponse createProduct(ProductRequest request) {
        Product product = new Product();
        product.setProductType(request.getProductType() != null ? request.getProductType() : ProductType.SINGLE);
        applyProduct(product, request);
        return saveProductWithOptions(product, request);
    }
```

Trong `updateProduct`, thay:

```java
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
        applyProduct(product, request);
```

bằng:

```java
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
        if (request.getProductType() != null && request.getProductType() != product.getProductType()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Không thể đổi loại sản phẩm đã tạo (món lẻ ↔ combo)");
        }
        applyProduct(product, request);
```

Trong `deleteProduct`, thay:

```java
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
        product.setDeleted(true);
```

bằng:

```java
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
        if (!product.isCombo()) {
            // Tắt món thì combo tự "Tạm hết"; còn xoá thì chặn để combo không mất thành phần (spec §5).
            List<String> combos = comboItemRepository.findActiveComboNamesContaining(productId);
            if (!combos.isEmpty()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Món đang nằm trong combo: " + String.join(", ", combos)
                                + " — hãy sửa hoặc xoá combo trước");
            }
        }
        product.setDeleted(true);
```

Thay toàn bộ `applyProduct` và `saveProductWithOptions`:

```java
    private void applyProduct(Product product, ProductRequest request) {
        product.setCategory(findCategory(request.getCategoryId()));
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        product.setImageUrl(request.getImageUrl() == null ? null : request.getImageUrl().trim());
        product.setPrice(request.getPrice());
        product.setAvailable(request.isAvailable());
        product.setFeatured(request.isFeatured());
    }

    private ProductResponse saveProductWithOptions(Product product, ProductRequest request) {
        Product savedProduct = productRepository.save(product);
        syncOptionsAndGroups(savedProduct, request);
        syncImages(savedProduct, request.getImages());
        return toProductResponse(savedProduct);
    }
```

bằng:

```java
    private void applyProduct(Product product, ProductRequest request) {
        product.setCategory(findCategory(request.getCategoryId()));
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        product.setImageUrl(request.getImageUrl() == null ? null : request.getImageUrl().trim());
        product.setPrice(request.getPrice());
        product.setAvailable(request.isAvailable());
        product.setFeatured(request.isFeatured());
        if (product.isCombo()) {
            rejectSaleAndOptionsOnCombo(request);
        } else {
            if (request.getComboItems() != null && !request.getComboItems().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ combo mới có món thành phần");
            }
            applySale(product, request);
        }
    }

    /** Kiểm hết rồi mới ghi: combo sai luật thì không có row nào được lưu. */
    private ProductResponse saveProductWithOptions(Product product, ProductRequest request) {
        List<ComboLine> comboLines = product.isCombo() ? resolveComboLines(product, request) : null;
        Product savedProduct = productRepository.save(product);
        if (comboLines != null) {
            syncComboItems(savedProduct, comboLines);
        }
        syncOptionsAndGroups(savedProduct, request);
        syncImages(savedProduct, request.getImages());
        return toProductResponse(savedProduct);
    }

    /** Spec §3: salePrice > 0 và < price; ends > starts khi có cả hai. Bỏ trống salePrice = hết KM. */
    private void applySale(Product product, ProductRequest request) {
        BigDecimal salePrice = request.getSalePrice();
        if (salePrice == null) {
            product.setSalePrice(null);
            product.setSaleStartsAt(null);
            product.setSaleEndsAt(null);
            return;
        }
        if (salePrice.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giá khuyến mãi phải lớn hơn 0");
        }
        if (salePrice.compareTo(request.getPrice()) >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giá khuyến mãi phải nhỏ hơn giá gốc");
        }
        if (request.getSaleStartsAt() != null && request.getSaleEndsAt() != null
                && !request.getSaleEndsAt().isAfter(request.getSaleStartsAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Thời điểm kết thúc khuyến mãi phải sau thời điểm bắt đầu");
        }
        product.setSalePrice(salePrice);
        product.setSaleStartsAt(request.getSaleStartsAt());
        product.setSaleEndsAt(request.getSaleEndsAt());
    }

    private void rejectSaleAndOptionsOnCombo(ProductRequest request) {
        if (request.getSalePrice() != null || request.getSaleStartsAt() != null || request.getSaleEndsAt() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Combo không dùng giá khuyến mãi — hãy đặt thẳng giá combo");
        }
        boolean hasOptions = request.getOptions() != null && !request.getOptions().isEmpty();
        boolean hasGroups = request.getOptionGroups() != null && !request.getOptionGroups().isEmpty();
        if (hasOptions || hasGroups) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Combo không có topping hay nhóm lựa chọn");
        }
    }

    /** Một dòng thành phần đã kiểm tra. */
    private record ComboLine(Product component, int quantity) {
    }

    /**
     * Luật combo (spec §3) kiểm TRƯỚC khi ghi. Sửa combo mà {@code comboItems == null} = giữ thành phần
     * cũ (vẫn kiểm giá combo < tổng giá lẻ) và trả null để không đồng bộ lại.
     */
    private List<ComboLine> resolveComboLines(Product combo, ProductRequest request) {
        List<ComboItemRequest> requested = request.getComboItems();
        if (requested == null && combo.getId() != null) {
            assertComboCheaper(combo.getPrice(), productPricing.originalPrice(combo));
            return null;
        }
        if (requested == null || requested.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Combo phải có ít nhất một món thành phần");
        }
        Map<Long, Integer> quantityById = new LinkedHashMap<>();
        int portions = 0;
        for (ComboItemRequest line : requested) {
            if (line.getProductId() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thiếu món thành phần");
            }
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            if (quantity < 1 || quantity > 20) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số lượng mỗi món trong combo từ 1 đến 20");
            }
            if (quantityById.putIfAbsent(line.getProductId(), quantity) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Món thành phần bị trùng trong combo — hãy gộp số lượng vào một dòng");
            }
            portions += quantity;
        }
        if (portions < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Combo phải có tổng ít nhất 2 phần món");
        }

        Map<Long, Product> found = productRepository.findAllById(quantityById.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        List<ComboLine> lines = new ArrayList<>();
        BigDecimal original = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantityById.entrySet()) {
            Product component = found.get(entry.getKey());
            if (component == null || component.isDeleted()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Không tìm thấy món thành phần với ID: " + entry.getKey());
            }
            if (component.isCombo()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Thành phần combo phải là món lẻ: " + component.getName());
            }
            lines.add(new ComboLine(component, entry.getValue()));
            original = original.add(component.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
        }
        assertComboCheaper(combo.getPrice(), original);
        return lines;
    }

    private void assertComboCheaper(BigDecimal comboPrice, BigDecimal original) {
        if (comboPrice == null || comboPrice.compareTo(original) >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    String.format("Giá combo phải thấp hơn tổng giá lẻ của các món (%,.0fđ)", original.doubleValue()));
        }
    }

    /**
     * Diff theo món: món còn trong payload giữ NGUYÊN row (chỉ sửa số lượng) — không xoá rồi chèn lại cùng
     * khoá (combo_id, component_id), vì Hibernate flush INSERT trước DELETE sẽ đụng khoá chính.
     */
    private void syncComboItems(Product combo, List<ComboLine> lines) {
        Map<Long, ComboItem> current = new LinkedHashMap<>();
        for (ComboItem item : combo.getComboItems()) {
            current.put(item.getComponent().getId(), item);
        }
        List<ComboItem> target = new ArrayList<>();
        for (ComboLine line : lines) {
            ComboItem item = current.remove(line.component().getId());
            if (item == null) {
                item = new ComboItem();
                item.setId(new ComboItemId(combo.getId(), line.component().getId()));
                item.setCombo(combo);
                item.setComponent(line.component());
            }
            item.setQuantity(line.quantity());
            target.add(item);
        }
        if (!current.isEmpty()) {
            comboItemRepository.deleteAll(List.copyOf(current.values()));
        }
        comboItemRepository.saveAll(target);
        combo.setComboItems(target);
    }
```

- [ ] **Step 6: Chạy unit test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=CatalogServiceTest`
Expected: PASS (test cũ + 15 test mới của Task 7).

- [ ] **Step 7: Viết test tích hợp trên DB — `T/repository/ComboCatalogIntegrationTest.java`**

```java
package com.banhmyking.banhmyking.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.banhmyking.banhmyking.dto.catalog.ComboItemRequest;
import com.banhmyking.banhmyking.dto.catalog.ComboItemResponse;
import com.banhmyking.banhmyking.dto.catalog.ProductRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductResponse;
import com.banhmyking.banhmyking.entity.Category;
import com.banhmyking.banhmyking.enums.ProductType;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.service.CatalogService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo / sửa / xoá combo qua CatalogService trên MariaDB thật: khoá ghép combo_items chỉ lộ lỗi
 * thứ tự flush khi có DB. Mọi thay đổi rollback sau test.
 */
@SpringBootTest
@Transactional
class ComboCatalogIntegrationTest {

    @Autowired private CatalogService catalogService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long categoryId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin-combo-test", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Category category = new Category();
        category.setName("Combo IT " + System.nanoTime());
        categoryId = categoryRepository.save(category).getId();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUpdateAndDeleteComboAgainstRealDatabase() {
        Long banhMi = catalogService.createProduct(single("Bánh mì IT", "30000")).getId();
        Long coffee = catalogService.createProduct(single("Cà phê IT", "20000")).getId();

        Long comboId = catalogService.createProduct(combo("60000", line(banhMi, 1), line(coffee, 2))).getId();
        flushAndClear();
        ProductResponse created = catalogService.getProduct(comboId);
        assertThat(created.getProductType()).isEqualTo(ProductType.COMBO);
        assertThat(created.getCompareAtPrice()).isEqualByComparingTo("70000");
        assertThat(created.getComboItems()).extracting(ComboItemResponse::getName, ComboItemResponse::getQuantity)
                .containsExactly(tuple("Bánh mì IT", 1), tuple("Cà phê IT", 2));

        // Bỏ cà phê, đổi số lượng bánh mì (row giữ nguyên khoá → UPDATE)
        catalogService.updateProduct(comboId, combo("50000", line(banhMi, 2)));
        flushAndClear();
        assertThat(rowsOf(comboId)).isEqualTo(1);

        // Thêm lại cà phê (INSERT) — không đụng khoá chính
        catalogService.updateProduct(comboId, combo("70000", line(banhMi, 2), line(coffee, 1)));
        flushAndClear();
        assertThat(rowsOf(comboId)).isEqualTo(2);
        assertThat(catalogService.getProduct(comboId).getCompareAtPrice()).isEqualByComparingTo("80000");

        // Sửa combo không gửi comboItems = giữ nguyên thành phần
        ProductRequest keep = combo("75000");
        keep.setComboItems(null);
        catalogService.updateProduct(comboId, keep);
        flushAndClear();
        assertThat(rowsOf(comboId)).isEqualTo(2);

        // Tắt cà phê → combo "Tạm hết" ở mức chuỗi nhưng combo vẫn bật
        ProductRequest coffeeOff = single("Cà phê IT", "20000");
        coffeeOff.setAvailable(false);
        catalogService.updateProduct(coffee, coffeeOff);
        flushAndClear();
        ProductResponse afterOff = catalogService.getProduct(comboId);
        assertThat(afterOff.isAvailable()).isFalse();
        assertThat(afterOff.isEnabled()).isTrue();

        // Xoá món đang trong combo bị chặn; xoá combo trước thì xoá được
        assertThatThrownBy(() -> catalogService.deleteProduct(banhMi))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Món đang nằm trong combo");
        catalogService.deleteProduct(comboId);
        flushAndClear();
        assertThat(rowsOf(comboId)).isEqualTo(2); // combo_items giữ lại cho lịch sử
        catalogService.deleteProduct(banhMi);
    }

    private int rowsOf(Long comboId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM combo_items WHERE combo_id = ?", Integer.class, comboId);
        return count == null ? 0 : count;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private ProductRequest single(String name, String price) {
        ProductRequest request = new ProductRequest();
        request.setCategoryId(categoryId);
        request.setName(name);
        request.setPrice(new BigDecimal(price));
        return request;
    }

    private ProductRequest combo(String price, ComboItemRequest... lines) {
        ProductRequest request = single("Combo IT", price);
        request.setProductType(ProductType.COMBO);
        request.setComboItems(new ArrayList<>(List.of(lines)));
        return request;
    }

    private static ComboItemRequest line(Long productId, int quantity) {
        ComboItemRequest line = new ComboItemRequest();
        line.setProductId(productId);
        line.setQuantity(quantity);
        return line;
    }
}
```

- [ ] **Step 8: Chạy test tích hợp + toàn bộ**

Run: `./mvnw -B clean test -Dtest=ComboCatalogIntegrationTest,OptionGroupSyncIntegrationTest`
Expected: PASS.
Run: `./mvnw -B clean test`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/dto/catalog/ComboItemRequest.java src/main/java/com/banhmyking/banhmyking/dto/catalog/ProductRequest.java src/main/java/com/banhmyking/banhmyking/service/impl/CatalogServiceImpl.java src/test/java/com/banhmyking/banhmyking/service/CatalogServiceTest.java src/test/java/com/banhmyking/banhmyking/repository/ComboCatalogIntegrationTest.java
git commit -m "feat(thuc-don): ràng buộc giá khuyến mãi, tạo/sửa combo, chặn xoá món đang nằm trong combo"
```

---

## Task 8: Giỏ hàng — giá gốc từng dòng, combo, tiền tiết kiệm

**Files:**
- Modify: `M/dto/cart/CartItemResponse.java`, `M/dto/cart/CartResponse.java`, `M/service/impl/CartServiceImpl.java`
- Test: `T/service/CartServiceTest.java`

**Interfaces:**
- Consumes: `ProductPricing` (`now`, `unitPrice`, `lineTotal`, `lineSavings`, `effectivePrice`, `originalPrice`), `ComboExpander.isChainAvailable`, `ComboItemResponse.listOf` (Task 2/4/6).
- Produces (JSON giỏ — Task 11 khai báo kiểu TS tương ứng):
  - `CartItemResponse`: `basePrice` = **giá đang áp dụng** của 1 phần chưa gồm topping; thêm `originalUnitPrice` (giá gốc 1 phần chưa topping: món lẻ = `price`, combo = Σ giá lẻ thành phần), `productType`, `comboItems: List<ComboItemResponse>`; `unitPrice` = giá hiệu lực + topping (đã đúng từ Task 3).
  - `CartResponse.savingsAmount` (tổng tiết kiệm của giỏ; `empty()` = 0).
  - Thêm vào giỏ combo không còn bán ở mức chuỗi → 400 "… hiện không khả dụng …" (như món lẻ).

- [ ] **Step 1: Viết test đỏ — thêm vào `CartServiceTest`**

Thêm import:

```java
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import com.banhmyking.banhmyking.enums.ProductType;
```

Thêm helper + test (cuối class):

```java

    // ─── Giá KM + combo trong giỏ (spec combo-sale §6.2) ─────────────────────

    /** Combo 45.000đ = 1 Bánh mì Thập Cẩm (30.000) + 1 Cà phê (20.000) → giá gốc 50.000. */
    private Product comboProduct(Product coffee) {
        Product combo = new Product();
        combo.setId(30L);
        combo.setName("Combo Sáng no nê");
        combo.setProductType(ProductType.COMBO);
        combo.setPrice(BigDecimal.valueOf(45000));
        combo.setAvailable(true);
        combo.setComboItems(List.of(comboItem(combo, availableProduct, 1), comboItem(combo, coffee, 1)));
        return combo;
    }

    private Product coffee() {
        Product coffee = new Product();
        coffee.setId(31L);
        coffee.setName("Cà phê sữa đá");
        coffee.setPrice(BigDecimal.valueOf(20000));
        coffee.setAvailable(true);
        return coffee;
    }

    private static ComboItem comboItem(Product combo, Product component, int quantity) {
        ComboItem item = new ComboItem();
        item.setId(new ComboItemId(combo.getId(), component.getId()));
        item.setCombo(combo);
        item.setComponent(component);
        item.setQuantity(quantity);
        return item;
    }

    private Cart cartWith(Product product, int quantity) {
        Cart cart = new Cart();
        cart.setId(100L);
        CartItem item = new CartItem();
        item.setId(500L);
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(quantity);
        cart.getItems().add(item);
        return cart;
    }

    @Test
    @DisplayName("Món lẻ đang KM: basePrice = giá KM, originalUnitPrice = giá gốc, giỏ có tiền tiết kiệm")
    void getCart_saleItemShowsOriginalPriceAndSavings() {
        availableProduct.setSalePrice(BigDecimal.valueOf(25000));
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(cartWith(availableProduct, 2)));

        CartResponse response = cartService.getCart(1L);

        var line = response.getItems().get(0);
        assertThat(line.getProductType()).isEqualTo(ProductType.SINGLE);
        assertThat(line.getBasePrice()).isEqualByComparingTo("25000");
        assertThat(line.getOriginalUnitPrice()).isEqualByComparingTo("30000");
        assertThat(line.getUnitPrice()).isEqualByComparingTo("25000");
        assertThat(line.getComboItems()).isEmpty();
        assertThat(response.getSubtotal()).isEqualByComparingTo("50000");
        assertThat(response.getSavingsAmount()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("Dòng combo: giá combo, giá gốc = tổng giá lẻ, kèm thành phần")
    void getCart_comboLine() {
        Product combo = comboProduct(coffee());
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(cartWith(combo, 1)));

        CartResponse response = cartService.getCart(1L);

        var line = response.getItems().get(0);
        assertThat(line.getProductType()).isEqualTo(ProductType.COMBO);
        assertThat(line.getUnitPrice()).isEqualByComparingTo("45000");
        assertThat(line.getOriginalUnitPrice()).isEqualByComparingTo("50000");
        assertThat(line.getComboItems()).extracting("name").containsExactly("Bánh mì Thập Cẩm", "Cà phê sữa đá");
        assertThat(response.getSavingsAmount()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("Không thêm vào giỏ combo có thành phần đã ngừng bán toàn chuỗi")
    void addToCart_rejectsComboWithDisabledComponent() {
        Product coffee = coffee();
        coffee.setAvailable(false);
        Product combo = comboProduct(coffee);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testUser));
        when(productRepository.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(combo));

        assertThatThrownBy(() -> cartService.addToCart(1L,
                AddToCartRequest.builder().productId(30L).quantity(1).build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hiện không khả dụng");
        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("Giỏ rỗng có savingsAmount = 0")
    void getCart_emptyHasZeroSavings() {
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.empty());

        assertThat(cartService.getCart(1L).getSavingsAmount()).isEqualByComparingTo("0");
    }
```

(Nếu file chưa import `CartItem`, `Cart`, `BusinessException`, `List` thì thêm: `com.banhmyking.banhmyking.entity.Cart`, `com.banhmyking.banhmyking.entity.CartItem`, `com.banhmyking.banhmyking.exception.BusinessException`, `java.util.List`.)

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=CartServiceTest`
Expected: COMPILATION ERROR (`getOriginalUnitPrice`, `getProductType`, `getComboItems`, `getSavingsAmount` chưa có).

- [ ] **Step 3: Sửa `M/dto/cart/CartItemResponse.java`**

Thêm import `com.banhmyking.banhmyking.dto.catalog.ComboItemResponse;` và `com.banhmyking.banhmyking.enums.ProductType;`. Thay:

```java
    @Schema(description = "Giá gốc của món", example = "30000.00")
    private BigDecimal basePrice;
```

bằng:

```java
    @Schema(description = "SINGLE | COMBO", example = "SINGLE")
    private ProductType productType;

    @Schema(description = "Giá đang áp dụng của 1 phần (giá KM nếu đang KM, giá combo), chưa gồm topping",
            example = "25000.00")
    private BigDecimal basePrice;

    @Schema(description = "Giá gốc 1 phần chưa gồm topping: món lẻ = giá niêm yết, combo = tổng giá lẻ thành phần",
            example = "30000.00")
    private BigDecimal originalUnitPrice;

    @Schema(description = "Thành phần combo (rỗng với món lẻ)")
    @Builder.Default
    private List<ComboItemResponse> comboItems = new ArrayList<>();
```

- [ ] **Step 4: Sửa `M/dto/cart/CartResponse.java`**

Ngay sau field `subtotal` thêm:

```java

    @Schema(description = "Tổng tiền khách tiết kiệm nhờ giá KM và combo (so với giá gốc)", example = "10000.00")
    private BigDecimal savingsAmount;
```

và trong `empty()` thêm `.savingsAmount(BigDecimal.ZERO)` ngay sau `.subtotal(BigDecimal.ZERO)`.

- [ ] **Step 5: Sửa `M/service/impl/CartServiceImpl.java`**

Thêm import `com.banhmyking.banhmyking.dto.catalog.ComboItemResponse;` và `com.banhmyking.banhmyking.service.ComboExpander;`.

Trong `addToCart`, thay:

```java
        if (!product.isAvailable()) {
```

bằng:

```java
        // Combo còn cần mọi thành phần đang bán toàn chuỗi (spec combo-sale §4.3)
        if (!ComboExpander.isChainAvailable(product)) {
```

Thay toàn bộ phương thức `toCartResponse` bằng:

```java
    /**
     * AC 4: Tạm tính hoàn toàn ở server, theo giá hiện tại (spec combo-sale §3 — giỏ không chốt giá).
     * unitPrice = giá hiệu lực + topping; tiết kiệm dòng = (giá gốc − giá hiệu lực) × số lượng.
     */
    private CartResponse toCartResponse(Cart cart) {
        if (cart == null) {
            return CartResponse.empty();
        }

        LocalDateTime now = productPricing.now();
        List<CartItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalSubtotal = BigDecimal.ZERO;
        BigDecimal totalSavings = BigDecimal.ZERO;
        int totalQuantity = 0;

        if (cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                Product product = item.getProduct();
                List<CartItemOptionResponse> optionResponses = new ArrayList<>();

                if (item.getSelectedOptions() != null) {
                    for (CartItemOption cio : item.getSelectedOptions()) {
                        ProductOption po = cio.getProductOption();
                        BigDecimal extra = (po != null && po.getExtraPrice() != null)
                                ? po.getExtraPrice()
                                : BigDecimal.ZERO;

                        optionResponses.add(CartItemOptionResponse.builder()
                                .id(cio.getId())
                                .productOptionId(po != null ? po.getId() : null)
                                .name(po != null ? po.getName() : null)
                                .extraPrice(extra)
                                .build());
                    }
                }

                BigDecimal unitPrice = productPricing.unitPrice(item, now);
                BigDecimal itemSubtotal = productPricing.lineTotal(item, now);
                int qty = item.getQuantity() != null ? item.getQuantity() : 1;

                totalQuantity += qty;
                totalSubtotal = totalSubtotal.add(itemSubtotal);
                totalSavings = totalSavings.add(productPricing.lineSavings(item, now));

                itemResponses.add(CartItemResponse.builder()
                        .id(item.getId())
                        .productId(product != null ? product.getId() : null)
                        .productName(product != null ? product.getName() : null)
                        .productImageUrl(product != null ? product.getImageUrl() : null)
                        .productType(product != null ? product.getProductType() : null)
                        .basePrice(productPricing.effectivePrice(product, now))
                        .originalUnitPrice(productPricing.originalPrice(product))
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .subtotal(itemSubtotal)
                        .options(optionResponses)
                        .comboItems(new ArrayList<>(ComboItemResponse.listOf(product)))
                        .build());
            }
        }

        return CartResponse.builder()
                .cartId(cart.getId())
                .items(itemResponses)
                .totalQuantity(totalQuantity)
                .subtotal(totalSubtotal)
                .savingsAmount(totalSavings)
                .build();
    }
```

- [ ] **Step 6: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=CartServiceTest,CartControllerTest`
Expected: PASS (test cũ + 4 test mới).

- [ ] **Step 7: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/dto/cart/CartItemResponse.java src/main/java/com/banhmyking/banhmyking/dto/cart/CartResponse.java src/main/java/com/banhmyking/banhmyking/service/impl/CartServiceImpl.java src/test/java/com/banhmyking/banhmyking/service/CartServiceTest.java
git commit -m "feat(gio-hang): giá gốc từng dòng, thành phần combo, tiền tiết kiệm; chặn thêm combo tạm hết"
```

---

## Task 9: Tạo đơn — snapshot giá gốc + thành phần combo, tiền tiết kiệm của đơn

**Files:**
- Create: `M/dto/order/OrderItemComponentResponse.java`
- Modify: `M/dto/order/OrderItemResponse.java`, `M/dto/order/OrderResponse.java`, `M/service/impl/OrderServiceImpl.java`
- Test: `T/service/OrderServiceTest.java`

**Interfaces:**
- Consumes: `ProductPricing.effectivePrice/originalPrice` (Task 2), biến `pricedAt` trong `createFromCart` (Task 3), `ComboExpander.isChainAvailable` (Task 4), `OrderItem.setOriginalUnitPrice/getComponents`, `OrderItemComponent` (Task 1).
- Produces:
  - `record OrderItemComponentResponse(String productName, Integer quantity)`.
  - `OrderItemResponse` thêm `originalUnitPrice` (null ở đơn cũ), `components: List<OrderItemComponentResponse>`.
  - `OrderResponse.savingsAmount` = Σ max(0, originalUnitPrice − unitPrice) × quantity (dòng NULL bỏ qua).
  - Snapshot: dòng món lẻ `unitPrice` = giá hiệu lực, `originalUnitPrice` = `price`; dòng combo `unitPrice` = giá combo, `originalUnitPrice` = Σ giá lẻ thành phần + các `OrderItemComponent` (tên, số lượng trong 1 combo). `originalUnitPrice` không bao giờ nhỏ hơn `unitPrice` (lấy `max`).
  - Xác nhận / huỷ / giao lỗi / chuyển cơ sở / webhook: **không đổi code** — đã đi qua `InventoryService` (Task 5).

- [ ] **Step 1: Viết test đỏ — thêm vào `OrderServiceTest`**

Thêm import:

```java
import static org.assertj.core.api.Assertions.tuple;

import com.banhmyking.banhmyking.dto.order.OrderItemComponentResponse;
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import com.banhmyking.banhmyking.enums.ProductType;
```

Thêm helper + test (cuối class):

```java

    // ─── Giá KM + combo trong đơn (spec combo-sale §5) ───────────────────────

    private Product coffee() {
        Product coffee = new Product();
        coffee.setId(11L);
        coffee.setName("Cà phê sữa đá");
        coffee.setPrice(BigDecimal.valueOf(20000));
        coffee.setAvailable(true);
        return coffee;
    }

    /** Combo 45.000đ = 1 Bánh mì Đặc Biệt (35.000) + 1 Cà phê (20.000) → giá gốc 55.000. */
    private Product combo(Product coffee) {
        Product combo = new Product();
        combo.setId(12L);
        combo.setName("Combo Sáng no nê");
        combo.setProductType(ProductType.COMBO);
        combo.setPrice(BigDecimal.valueOf(45000));
        combo.setAvailable(true);
        combo.setComboItems(List.of(comboItem(combo, testProduct, 1), comboItem(combo, coffee, 1)));
        return combo;
    }

    private static ComboItem comboItem(Product combo, Product component, int quantity) {
        ComboItem item = new ComboItem();
        item.setId(new ComboItemId(combo.getId(), component.getId()));
        item.setCombo(combo);
        item.setComponent(component);
        item.setQuantity(quantity);
        return item;
    }

    private void replaceCartWith(Product product, int quantity) {
        testCart.getItems().clear();
        CartItem line = new CartItem();
        line.setId(501L);
        line.setCart(testCart);
        line.setProduct(product);
        line.setQuantity(quantity);
        testCart.getItems().add(line);
    }

    private void stubSuccessfulCreate() {
        PriceBreakdown breakdown = PriceBreakdown.builder()
                .subtotal(BigDecimal.valueOf(90000))
                .shippingFee(BigDecimal.valueOf(15000))
                .discountAmount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(105000))
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(testCart));
        when(addressRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(testAddress));
        when(priceCalculator.calculate(eq(testCart), any(), any(), any())).thenReturn(breakdown);
        when(orderCodeGenerator.generateUniqueCode(any(), anyInt())).thenReturn("BMK-20261002-COMBO");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CreateOrderRequest codRequest() {
        return CreateOrderRequest.builder().addressId(200L).paymentMethod(PaymentMethod.COD).build();
    }

    @Test
    @DisplayName("Combo: dòng đơn chụp giá combo, giá gốc = tổng giá lẻ và thành phần; đơn có tiền tiết kiệm")
    void createFromCart_comboSnapshotsOriginalPriceAndComponents() {
        replaceCartWith(combo(coffee()), 2);
        stubSuccessfulCreate();

        OrderResponse response = orderService.createFromCart(1L, codRequest());

        var item = response.getItems().get(0);
        assertThat(item.getProductName()).isEqualTo("Combo Sáng no nê");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("45000");
        assertThat(item.getOriginalUnitPrice()).isEqualByComparingTo("55000");
        assertThat(item.getLineTotal()).isEqualByComparingTo("90000");
        assertThat(item.getComponents())
                .extracting(OrderItemComponentResponse::productName, OrderItemComponentResponse::quantity)
                .containsExactlyInAnyOrder(tuple("Bánh mì Đặc Biệt", 1), tuple("Cà phê sữa đá", 1));
        assertThat(response.getSavingsAmount()).isEqualByComparingTo("20000"); // (55.000 − 45.000) × 2
    }

    @Test
    @DisplayName("Món lẻ đang KM: originalUnitPrice = giá gốc; không KM: originalUnitPrice = unitPrice")
    void createFromCart_saleItemSnapshotsOriginalPrice() {
        testProduct.setSalePrice(BigDecimal.valueOf(30000)); // giá gốc 35.000, dòng có topping 5.000 × 2
        stubSuccessfulCreate();

        OrderResponse response = orderService.createFromCart(1L, codRequest());

        var item = response.getItems().get(0);
        assertThat(item.getUnitPrice()).isEqualByComparingTo("30000");
        assertThat(item.getOriginalUnitPrice()).isEqualByComparingTo("35000");
        assertThat(item.getComponents()).isEmpty();
        assertThat(response.getSavingsAmount()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("Combo có thành phần đã ngừng bán toàn chuỗi → không tạo đơn")
    void createFromCart_comboWithDisabledComponentRejected() {
        Product coffee = coffee();
        coffee.setAvailable(false);
        replaceCartWith(combo(coffee), 1);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(testCart));

        assertThatThrownBy(() -> orderService.createFromCart(1L, codRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hiện không khả dụng");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đơn cũ (originalUnitPrice NULL) hiển thị như không có ưu đãi")
    void legacyOrderHasZeroSavings() {
        Order legacy = new Order();
        legacy.setOrderCode("BMK-OLD");
        legacy.setUser(testUser);
        com.banhmyking.banhmyking.entity.OrderItem old = new com.banhmyking.banhmyking.entity.OrderItem();
        old.setProductName("Bánh mì cũ");
        old.setUnitPrice(BigDecimal.valueOf(30000));
        old.setQuantity(2);
        old.setLineTotal(BigDecimal.valueOf(60000));
        legacy.getItems().add(old);
        testUser.setRole(com.banhmyking.banhmyking.enums.RoleName.CUSTOMER);
        when(orderRepository.findByOrderCodeWithDetails("BMK-OLD")).thenReturn(Optional.of(legacy));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        OrderResponse response = orderService.getOrderByCode(1L, "BMK-OLD");

        assertThat(response.getItems().get(0).getOriginalUnitPrice()).isNull();
        assertThat(response.getSavingsAmount()).isEqualByComparingTo("0");
    }
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=OrderServiceTest`
Expected: COMPILATION ERROR (`OrderItemComponentResponse`, `getOriginalUnitPrice`, `getComponents`, `getSavingsAmount` chưa có).

- [ ] **Step 3: `M/dto/order/OrderItemComponentResponse.java`**

```java
package com.banhmyking.banhmyking.dto.order;

/** Snapshot một thành phần của dòng combo: tên lúc đặt + số lượng trong MỘT combo. */
public record OrderItemComponentResponse(String productName, Integer quantity) {
}
```

- [ ] **Step 4: Sửa `M/dto/order/OrderItemResponse.java`**

Thay:

```java
    @Schema(description = "Đơn giá gốc món ăn snapshot tại thời điểm đặt", example = "30000.00")
    private BigDecimal unitPrice;
```

bằng:

```java
    @Schema(description = "Giá bán 1 phần chưa gồm topping lúc đặt (giá KM / giá combo nếu có)", example = "25000.00")
    private BigDecimal unitPrice;

    @Schema(description = "Giá gốc 1 phần chưa gồm topping lúc đặt; null = đơn cũ, coi như không có ưu đãi",
            example = "30000.00")
    private BigDecimal originalUnitPrice;
```

và ngay sau khối `options` thêm:

```java

    @Schema(description = "Thành phần combo (snapshot); rỗng với món lẻ")
    @Builder.Default
    private List<OrderItemComponentResponse> components = new ArrayList<>();
```

- [ ] **Step 5: Sửa `M/dto/order/OrderResponse.java`**

Ngay sau field `discountAmount` thêm:

```java

    @Schema(description = "Tiền khách tiết kiệm nhờ giá KM và combo (tính từ snapshot các dòng)", example = "10000.00")
    private BigDecimal savingsAmount;
```

- [ ] **Step 6: Sửa `M/service/impl/OrderServiceImpl.java`**

Thêm import:

```java
import com.banhmyking.banhmyking.dto.order.OrderItemComponentResponse;
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.OrderItemComponent;
import com.banhmyking.banhmyking.service.ComboExpander;
```

Trong `createFromCart`, thay:

```java
            if (!product.isAvailable()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Món ăn '" + product.getName() + "' hiện không khả dụng (hết hàng hoặc tạm ngưng bán)");
            }
```

bằng:

```java
            // Combo còn cần mọi thành phần đang bán toàn chuỗi (spec combo-sale §4.3)
            if (!ComboExpander.isChainAvailable(product)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Món ăn '" + product.getName() + "' hiện không khả dụng (hết hàng hoặc tạm ngưng bán)");
            }
```

Thay (dòng đã sửa ở Task 3):

```java
            orderItem.setUnitPrice(productPricing.effectivePrice(product, pricedAt)); // Snapshot giá hiệu lực (giá KM nếu đang KM)
            orderItem.setQuantity(cartItem.getQuantity());
```

bằng:

```java
            BigDecimal unitPrice = productPricing.effectivePrice(product, pricedAt);
            orderItem.setUnitPrice(unitPrice); // Snapshot giá hiệu lực (giá KM / giá combo)
            // Giá gốc lúc đặt: món lẻ = price, combo = Σ giá lẻ thành phần; không thấp hơn giá bán → tiết kiệm ≥ 0
            orderItem.setOriginalUnitPrice(productPricing.originalPrice(product).max(unitPrice));
            orderItem.setQuantity(cartItem.getQuantity());
            if (product.isCombo()) {
                for (ComboItem comboItem : product.getComboItems()) {
                    OrderItemComponent component = new OrderItemComponent();
                    component.setOrderItem(orderItem);
                    component.setProduct(comboItem.getComponent());
                    component.setProductName(comboItem.getComponent().getName()); // Snapshot tên thành phần
                    component.setQuantity(comboItem.getQuantity());
                    orderItem.getComponents().add(component);
                }
            }
```

Thay toàn bộ phần đầu của `toOrderResponse` (từ `private OrderResponse toOrderResponse(Order order) {` tới hết vòng `for` dựng `itemResponses`):

```java
    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                List<OrderItemOptionResponse> optionResponses = new ArrayList<>();
                if (item.getOptions() != null) {
                    for (OrderItemOption opt : item.getOptions()) {
                        optionResponses.add(OrderItemOptionResponse.builder()
                                .id(opt.getId())
                                .optionName(opt.getOptionName())
                                .optionPrice(opt.getOptionPrice())
                                .build());
                    }
                }

                itemResponses.add(OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getLineTotal())
                        .options(optionResponses)
                        .build());
            }
        }
```

bằng:

```java
    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        BigDecimal savings = BigDecimal.ZERO;
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                List<OrderItemOptionResponse> optionResponses = new ArrayList<>();
                if (item.getOptions() != null) {
                    for (OrderItemOption opt : item.getOptions()) {
                        optionResponses.add(OrderItemOptionResponse.builder()
                                .id(opt.getId())
                                .optionName(opt.getOptionName())
                                .optionPrice(opt.getOptionPrice())
                                .build());
                    }
                }
                List<OrderItemComponentResponse> componentResponses = new ArrayList<>();
                if (item.getComponents() != null) {
                    for (OrderItemComponent component : item.getComponents()) {
                        componentResponses.add(new OrderItemComponentResponse(
                                component.getProductName(), component.getQuantity()));
                    }
                }
                savings = savings.add(savingsOf(item));

                itemResponses.add(OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .originalUnitPrice(item.getOriginalUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getLineTotal())
                        .options(optionResponses)
                        .components(componentResponses)
                        .build());
            }
        }
```

Trong builder `OrderResponse` của cùng hàm, thay:

```java
                .discountAmount(order.getDiscountAmount())
```

bằng:

```java
                .discountAmount(order.getDiscountAmount())
                .savingsAmount(savings.setScale(2, RoundingMode.HALF_UP))
```

Thêm hàm tĩnh ngay sau `toOrderResponse`:

```java
    /** Tiết kiệm của dòng đơn = (giá gốc − giá bán) × số lượng; đơn cũ (originalUnitPrice NULL) = 0. */
    private static BigDecimal savingsOf(OrderItem item) {
        if (item.getOriginalUnitPrice() == null || item.getUnitPrice() == null || item.getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal perUnit = item.getOriginalUnitPrice().subtract(item.getUnitPrice());
        return perUnit.signum() > 0 ? perUnit.multiply(BigDecimal.valueOf(item.getQuantity())) : BigDecimal.ZERO;
    }
```

- [ ] **Step 7: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=OrderServiceTest,OrderPriceSnapshotTest,OrderStoreScopeTest,OrderServiceStateMachineTest,OrderOwnershipTest,OrderServiceApisTest,PaymentServiceTest`
Expected: PASS (4 test mới, test cũ không đổi).

- [ ] **Step 8: Chạy toàn bộ test**

Run: `./mvnw -B clean test`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/dto/order/OrderItemComponentResponse.java src/main/java/com/banhmyking/banhmyking/dto/order/OrderItemResponse.java src/main/java/com/banhmyking/banhmyking/dto/order/OrderResponse.java src/main/java/com/banhmyking/banhmyking/service/impl/OrderServiceImpl.java src/test/java/com/banhmyking/banhmyking/service/OrderServiceTest.java
git commit -m "feat(don-hang): chụp giá gốc và thành phần combo khi đặt, trả tiền tiết kiệm của đơn"
```

---

## Task 10: Báo cáo "Tiền ưu đãi từ giá KM và combo" (ADMIN + MANAGER)

**Files:**
- Create: `M/dto/report/PriceSavingsResponse.java`
- Modify: `M/repository/OrderItemRepository.java`, `M/service/AdminReportService.java`, `M/service/impl/AdminReportServiceImpl.java`, `M/controller/AdminReportController.java`, `M/controller/ManagerController.java`
- Test: `T/service/PriceSavingsReportTest.java` (mới)

**Interfaces:**
- Consumes: `OrderItem.originalUnitPrice` (Task 1), `StoreAccessGuard.scopedStoreId` qua `ManagerController.ownStore` (A).
- Produces:
  - `PriceSavingsResponse { BigDecimal amount; long orderCount; }` — `amount` = Σ (original_unit_price − unit_price) × quantity trên đơn `DELIVERED` tạo trong khoảng; dòng `original_unit_price` NULL hoặc không rẻ hơn bị bỏ; `orderCount` = số đơn `DELIVERED` có ít nhất một dòng ưu đãi.
  - `AdminReportService.getPriceSavings(LocalDate fromDate, LocalDate toDate, Long storeId)` (null ngày = 30 ngày gần nhất như các báo cáo khác).
  - `GET /api/v1/admin/reports/price-savings?fromDate&toDate&storeId` (ADMIN); `GET /api/v1/manager/reports/price-savings?fromDate&toDate` (MANAGER khoá về cơ sở mình; ADMIN gọi = toàn chuỗi).

- [ ] **Step 1: Viết test đỏ — `T/service/PriceSavingsReportTest.java`**

```java
package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banhmyking.banhmyking.dto.report.PriceSavingsResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.JwtTokenProvider;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Tiền ưu đãi từ giá KM và combo (spec combo-sale §6.5) trên DB dev thật: tự tạo cơ sở + đơn trong
 * transaction của test rồi rollback. MockMvc chạy cùng luồng nên dùng chung transaction.
 */
@SpringBootTest
@Transactional
class PriceSavingsReportTest {

    @Autowired private AdminReportService reportService;
    @Autowired private StoreRepository storeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;
    private Store storeX;
    private Store storeY;
    private User managerX;
    private User staffX;
    private User admin;

    @BeforeEach
    void setUp() {
        Filter securityChain = context.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityChain).build();

        String tag = Long.toString(System.nanoTime(), 36).toUpperCase();
        storeX = storeRepository.save(store("PX" + tag));
        storeY = storeRepository.save(store("PY" + tag));
        User customer = userRepository.save(user(tag, "customer", RoleName.CUSTOMER, null));
        managerX = userRepository.save(user(tag, "manager", RoleName.MANAGER, storeX));
        staffX = userRepository.save(user(tag, "staff", RoleName.STAFF, storeX));
        admin = userRepository.save(user(tag, "admin", RoleName.ADMIN, null));

        // X: 1 đơn giao có ưu đãi (2 × 5.000) + 1 dòng đơn cũ NULL; 1 đơn giao không ưu đãi; 1 đơn huỷ có ưu đãi
        Order delivered = order(customer, storeX, tag + "1", OrderStatus.DELIVERED);
        delivered.getItems().add(item(delivered, "30000", "25000", 2));
        delivered.getItems().add(item(delivered, null, "20000", 1));
        orderRepository.save(delivered);
        Order noSaving = order(customer, storeX, tag + "2", OrderStatus.DELIVERED);
        noSaving.getItems().add(item(noSaving, "20000", "20000", 3));
        orderRepository.save(noSaving);
        Order cancelled = order(customer, storeX, tag + "3", OrderStatus.CANCELLED);
        cancelled.getItems().add(item(cancelled, "50000", "40000", 1));
        orderRepository.save(cancelled);
        // Y: 2 đơn giao có ưu đãi (15.000 + 3.000)
        Order comboOrder = order(customer, storeY, tag + "4", OrderStatus.DELIVERED);
        comboOrder.getItems().add(item(comboOrder, "70000", "55000", 1));
        orderRepository.save(comboOrder);
        Order saleOrder = order(customer, storeY, tag + "5", OrderStatus.DELIVERED);
        saleOrder.getItems().add(item(saleOrder, "33000", "30000", 1));
        orderRepository.save(saleOrder);
        orderRepository.flush();
    }

    @Test
    void sumsOnlyDeliveredDiscountedLinesPerStore() {
        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now().plusDays(1);

        PriceSavingsResponse x = reportService.getPriceSavings(from, to, storeX.getId());
        assertThat(x.getAmount()).isEqualByComparingTo("10000");
        assertThat(x.getOrderCount()).isEqualTo(1L);

        PriceSavingsResponse y = reportService.getPriceSavings(from, to, storeY.getId());
        assertThat(y.getAmount()).isEqualByComparingTo("18000");
        assertThat(y.getOrderCount()).isEqualTo(2L);

        PriceSavingsResponse chain = reportService.getPriceSavings(from, to, null);
        assertThat(chain.getAmount()).isGreaterThanOrEqualTo(new BigDecimal("28000"));
    }

    @Test
    void emptyRangeReturnsZero() {
        PriceSavingsResponse result = reportService.getPriceSavings(
                LocalDate.of(2000, 1, 1), LocalDate.of(2000, 1, 2), storeX.getId());
        assertThat(result.getAmount()).isEqualByComparingTo("0");
        assertThat(result.getOrderCount()).isZero();
    }

    @Test
    void managerSeesOwnStoreAdminFiltersStaffForbidden() throws Exception {
        mockMvc.perform(as(managerX, get("/api/v1/manager/reports/price-savings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(1));
        mockMvc.perform(as(admin, get("/api/v1/admin/reports/price-savings").param("storeId", storeY.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(2));
        mockMvc.perform(as(staffX, get("/api/v1/manager/reports/price-savings")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(managerX, get("/api/v1/admin/reports/price-savings")))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(user));
    }

    private static Store store(String code) {
        Store store = new Store();
        store.setCode(code);
        store.setName("Cơ sở " + code);
        store.setAddress("Địa chỉ thử nghiệm");
        return store;
    }

    private static User user(String tag, String kind, RoleName role, Store store) {
        User user = new User();
        user.setEmail("ps-" + kind + "-" + tag.toLowerCase() + "@test.local");
        user.setPassword("not-used");
        user.setFullName("PS " + kind);
        user.setRole(role);
        user.setStore(store);
        return user;
    }

    private static Order order(User customer, Store store, String suffix, OrderStatus status) {
        Order order = new Order();
        order.setOrderCode("PS-" + suffix);
        order.setUser(customer);
        order.setStore(store);
        order.setStatus(status);
        order.setReceiverName("Khách thử");
        order.setReceiverPhone("0900000000");
        order.setShippingAddress("1 Đường Thử");
        order.setSubtotal(new BigDecimal("100000"));
        order.setTotal(new BigDecimal("100000"));
        return order;
    }

    private static OrderItem item(Order order, String original, String unit, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductName("Món thử");
        item.setUnitPrice(new BigDecimal(unit));
        item.setOriginalUnitPrice(original == null ? null : new BigDecimal(original));
        item.setQuantity(quantity);
        item.setLineTotal(new BigDecimal(unit).multiply(BigDecimal.valueOf(quantity)));
        return item;
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=PriceSavingsReportTest`
Expected: COMPILATION ERROR (`PriceSavingsResponse`, `getPriceSavings` chưa có).

- [ ] **Step 3: `M/dto/report/PriceSavingsResponse.java`**

```java
package com.banhmyking.banhmyking.dto.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Getter;

/** Tiền ưu đãi từ giá KM và combo trên đơn đã giao (spec combo-sale §6.5). */
@Getter
public class PriceSavingsResponse {

    /** Σ (giá gốc − giá bán) × số lượng; 0 khi không có dòng nào. */
    private final BigDecimal amount;

    /** Số đơn đã giao có ít nhất một dòng ưu đãi. */
    private final long orderCount;

    /** Constructor cho JPQL `SELECT new` — SUM rỗng trả NULL. */
    public PriceSavingsResponse(BigDecimal amount, Long orderCount) {
        this.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        this.orderCount = orderCount == null ? 0L : orderCount;
    }
}
```

- [ ] **Step 4: Sửa `M/repository/OrderItemRepository.java`**

Thêm import `com.banhmyking.banhmyking.dto.report.PriceSavingsResponse;` và query:

```java

    /**
     * Tiền ưu đãi trong [from, to): chỉ đơn DELIVERED; dòng original_unit_price NULL (đơn cũ) hoặc không
     * rẻ hơn bị bỏ. orderCount = số đơn có ít nhất một dòng ưu đãi.
     */
    @Query("SELECT new com.banhmyking.banhmyking.dto.report.PriceSavingsResponse("
            + "SUM((oi.originalUnitPrice - oi.unitPrice) * oi.quantity), COUNT(DISTINCT oi.order.id)) "
            + "FROM OrderItem oi "
            + "WHERE oi.order.status = com.banhmyking.banhmyking.enums.OrderStatus.DELIVERED "
            + "AND oi.originalUnitPrice IS NOT NULL AND oi.originalUnitPrice > oi.unitPrice "
            + "AND oi.order.createdAt >= :from AND oi.order.createdAt < :to "
            + "AND (:storeId IS NULL OR oi.order.store.id = :storeId)")
    PriceSavingsResponse sumPriceSavings(@Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         @Param("storeId") Long storeId);
```

- [ ] **Step 5: Sửa `AdminReportService` + `AdminReportServiceImpl`**

`M/service/AdminReportService.java` — thêm:

```java

    /** Tiền ưu đãi từ giá KM và combo trên đơn đã giao trong khoảng ngày. storeId null = toàn chuỗi. */
    com.banhmyking.banhmyking.dto.report.PriceSavingsResponse getPriceSavings(
            LocalDate fromDate, LocalDate toDate, Long storeId);
```

`M/service/impl/AdminReportServiceImpl.java` — thêm import `com.banhmyking.banhmyking.dto.report.PriceSavingsResponse;` và (ngay sau `getRevenueByStore`):

```java

    @Override
    @Transactional(readOnly = true)
    public PriceSavingsResponse getPriceSavings(LocalDate fromDate, LocalDate toDate, Long storeId) {
        LocalDate from = resolveFrom(fromDate, toDate);
        LocalDate to = resolveTo(toDate);
        assertValidRange(from, to);
        return orderItemRepository.sumPriceSavings(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), storeId);
    }
```

- [ ] **Step 6: Endpoint ADMIN — `M/controller/AdminReportController.java`**

Thêm import `com.banhmyking.banhmyking.dto.report.PriceSavingsResponse;` và (sau `revenueByStore`):

```java

    @GetMapping("/price-savings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tiền ưu đãi từ giá KM và combo",
            description = "Σ (giá gốc − giá bán) × số lượng trên đơn đã giao. Không truyền ngày = 30 ngày gần nhất.")
    public ResponseEntity<ApiResponse<PriceSavingsResponse>> priceSavings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy tiền ưu đãi thành công",
                adminReportService.getPriceSavings(fromDate, toDate, storeId)));
    }
```

- [ ] **Step 7: Endpoint MANAGER — `M/controller/ManagerController.java`**

Thêm import `com.banhmyking.banhmyking.dto.report.PriceSavingsResponse;` và (sau `topProducts`):

```java

    @GetMapping("/reports/price-savings")
    @Operation(summary = "Tiền ưu đãi từ giá KM và combo của cơ sở mình")
    public ResponseEntity<ApiResponse<PriceSavingsResponse>> priceSavings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy tiền ưu đãi thành công",
                reportService.getPriceSavings(fromDate, toDate, ownStore(principal))));
    }
```

- [ ] **Step 8: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=PriceSavingsReportTest,AdminReportServiceImplTest,AdminReportServiceStoreTest,AdminReportControllerTest,StoreScopedEndpointsSecurityTest`
Expected: PASS.

- [ ] **Step 9: Chạy toàn bộ test**

Run: `./mvnw -B clean test`
Expected: BUILD SUCCESS — backend B hoàn tất.

- [ ] **Step 10: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/dto/report/PriceSavingsResponse.java src/main/java/com/banhmyking/banhmyking/repository/OrderItemRepository.java src/main/java/com/banhmyking/banhmyking/service/AdminReportService.java src/main/java/com/banhmyking/banhmyking/service/impl/AdminReportServiceImpl.java src/main/java/com/banhmyking/banhmyking/controller/AdminReportController.java src/main/java/com/banhmyking/banhmyking/controller/ManagerController.java src/test/java/com/banhmyking/banhmyking/service/PriceSavingsReportTest.java
git commit -m "feat(bao-cao): tiền ưu đãi từ giá khuyến mãi và combo theo cơ sở (admin + quản lý)"
```

---

## Task 11: Frontend — types & API client

**Files:**
- Modify: `frontend/src/types/staff.ts`, `frontend/src/types/cart.ts`, `frontend/src/types/order.ts`, `frontend/src/types/store.ts`, `frontend/src/types/admin.ts`, `frontend/src/api/catalogApi.ts`, `frontend/src/api/staffCatalogApi.ts`, `frontend/src/api/adminReportsApi.ts`, `frontend/src/api/managerApi.ts`

**Interfaces:**
- Consumes: JSON của Task 6–10.
- Produces (TypeScript, dùng ở Task 12–15; mọi field mới là optional để dữ liệu cũ/không đổi vẫn hợp lệ):
  - `types/staff.ts`: `type ProductType = 'SINGLE' | 'COMBO'`; `ComboItemInfo { productId; name; imageUrl?; price; quantity }`; `ComboItemPayload { productId; quantity }`; `ProductPricingPayload { salePrice?; saleStartsAt?; saleEndsAt?; comboItems? }`; `ProductItem` thêm `enabled?`, `productType?`, `salePrice?`, `saleStartsAt?`, `saleEndsAt?`, `effectivePrice?`, `compareAtPrice?`, `discountPercent?`, `onSale?`, `comboItems?`; `ProductCreatePayload extends ProductPricingPayload` (+ `productType?`), `ProductUpdatePayload extends ProductPricingPayload`.
  - `types/cart.ts`: `CartItem` thêm `productType?`, `originalUnitPrice?`, `comboItems?`; `Cart.savingsAmount?`.
  - `types/order.ts`: `OrderItemComponent { productName; quantity }`; `OrderItemResponse` thêm `originalUnitPrice?`, `components?`; `OrderResponse.savingsAmount?`.
  - `types/store.ts`: `StoreStockItem` thêm `productType?`, `blockedBy?`.
  - `types/admin.ts`: `PriceSavings { amount: number; orderCount: number }`.
  - `catalogApi.getProducts` nhận `onSale?: boolean`, `type?: ProductType`.
  - `staffCatalogApi.toggleProductAvailability` giữ nguyên giá KM và đảo cờ `enabled` (không phải `available` suy diễn của combo).
  - `adminReportsApi.getPriceSavings(params: ReportFilterParams): Promise<PriceSavings>`; `managerApi.priceSavings(params?: ReportFilterParams): Promise<PriceSavings>`.

- [ ] **Step 1: `types/staff.ts`**

Ngay trước `export interface CategoryItem {` thêm:

```ts
/** SINGLE = món lẻ, COMBO = combo cố định gồm nhiều món lẻ (ProductType backend). */
export type ProductType = 'SINGLE' | 'COMBO';

/** Một món lẻ trong combo — ComboItemResponse. */
export interface ComboItemInfo {
  productId: number;
  name: string;
  imageUrl?: string | null;
  /** Giá gốc của món lẻ */
  price: number;
  /** Số phần trong một combo */
  quantity: number;
}

/** Dòng thành phần gửi lên khi tạo/sửa combo — ComboItemRequest. */
export interface ComboItemPayload {
  productId: number;
  quantity: number;
}

/** Giá KM + thành phần combo gửi kèm món (ProductRequest). */
export interface ProductPricingPayload {
  /** Chỉ món lẻ. null/bỏ trống = không khuyến mãi (xoá KM đang có). */
  salePrice?: number | null;
  /** 'yyyy-MM-ddTHH:mm' giờ Việt Nam; null = áp dụng ngay */
  saleStartsAt?: string | null;
  /** null = không hết hạn */
  saleEndsAt?: string | null;
  /** Chỉ combo. Sửa combo mà bỏ trống = giữ nguyên thành phần. */
  comboItems?: ComboItemPayload[];
}

```

Trong `ProductItem`, thay:

```ts
  available: boolean;
  featured: boolean;
```

bằng:

```ts
  /** Khách đặt được (combo: còn xét mọi thành phần đang bán) */
  available: boolean;
  /** Cờ bật/tắt của chính món — nút bật/tắt của admin dùng cờ này */
  enabled?: boolean;
  featured: boolean;
  productType?: ProductType;
  /** Giá KM đã cấu hình (kể cả chưa tới hạn / đã hết) */
  salePrice?: number | null;
  /** 'yyyy-MM-ddTHH:mm:ss' giờ Việt Nam */
  saleStartsAt?: string | null;
  saleEndsAt?: string | null;
  /** Giá đang bán do server tính — client chỉ hiển thị */
  effectivePrice?: number;
  /** Giá gạch; null = không gạch */
  compareAtPrice?: number | null;
  discountPercent?: number | null;
  /** Món lẻ đang trong thời gian KM */
  onSale?: boolean;
  /** Thành phần combo; rỗng với món lẻ */
  comboItems?: ComboItemInfo[];
```

Thay `export interface ProductCreatePayload {` bằng:

```ts
export interface ProductCreatePayload extends ProductPricingPayload {
  /** Chỉ khi tạo; bỏ trống = SINGLE */
  productType?: ProductType;
```

Thay `export interface ProductUpdatePayload {` bằng:

```ts
export interface ProductUpdatePayload extends ProductPricingPayload {
```

- [ ] **Step 2: `types/cart.ts`**

Thêm ở đầu file (sau comment khối đầu):

```ts
import type { ComboItemInfo, ProductType } from './staff';
```

Trong `CartItem`, thay:

```ts
  basePrice: number;
```

bằng:

```ts
  productType?: ProductType;
  /** Giá đang áp dụng của 1 phần (giá KM / giá combo), chưa gồm topping */
  basePrice: number;
  /** Giá gốc 1 phần chưa gồm topping (combo = tổng giá lẻ) */
  originalUnitPrice?: number;
  comboItems?: ComboItemInfo[];
```

Trong `Cart`, thay:

```ts
  subtotal: number;
}
```

(khối đầu tiên — của `interface Cart`) bằng:

```ts
  subtotal: number;
  /** Tiền tiết kiệm nhờ giá KM và combo */
  savingsAmount?: number;
}
```

- [ ] **Step 3: `types/order.ts`**

Ngay trước `export interface OrderItemResponse {` thêm:

```ts
/** Snapshot thành phần của dòng combo */
export interface OrderItemComponent {
  productName: string;
  /** Số lượng trong MỘT combo */
  quantity: number;
}

```

Trong `OrderItemResponse`, thay:

```ts
  unitPrice: number;
  quantity: number;
  lineTotal: number;
```

bằng:

```ts
  /** Giá bán 1 phần chưa gồm topping lúc đặt */
  unitPrice: number;
  /** Giá gốc 1 phần lúc đặt; null = đơn cũ (không có ưu đãi) */
  originalUnitPrice?: number | null;
  quantity: number;
  lineTotal: number;
  components?: OrderItemComponent[];
```

Trong `OrderResponse`, thay:

```ts
  discountAmount: number;
  total: number;
```

bằng:

```ts
  discountAmount: number;
  /** Tiền tiết kiệm nhờ giá KM và combo (đã nằm trong tạm tính) */
  savingsAmount?: number;
  total: number;
```

- [ ] **Step 4: `types/store.ts`, `types/admin.ts`**

`types/store.ts` — thêm dòng đầu file `import type { ProductType } from './staff';`; trong `StoreStockItem` thay:

```ts
  lowStock: boolean;
}
```

(khối thuộc `StoreStockItem`) bằng:

```ts
  lowStock: boolean;
  productType?: ProductType;
  /** Thành phần đang làm combo không bán được tại cơ sở */
  blockedBy?: string[];
}
```

`types/admin.ts` — thêm cuối file:

```ts

/** Tiền ưu đãi từ giá KM và combo trên đơn đã giao */
export interface PriceSavings {
  amount: number;
  orderCount: number;
}
```

- [ ] **Step 5: API**

`api/catalogApi.ts` — đổi import thành `import type { CategoryItem, ProductItem, ProductType } from '../types/staff';` và trong `ProductSearchParams`, ngay sau `maxPrice?: number;` thêm:

```ts
  /** Chỉ món lẻ đang khuyến mãi */
  onSale?: boolean;
  /** SINGLE | COMBO */
  type?: ProductType;
```

`api/staffCatalogApi.ts` — trong `toggleProductAvailability`, thay:

```ts
      price: product.price,
      available: !product.available,
      featured: product.featured,
    };
```

bằng:

```ts
      price: product.price,
      // Đảo cờ của CHÍNH món: `available` của combo còn tính thành phần nên không dùng được ở đây
      available: !(product.enabled ?? product.available),
      featured: product.featured,
      // Bỏ trống salePrice nghĩa là xoá KM — phải gửi lại để bật/tắt không làm mất khuyến mãi
      salePrice: product.salePrice ?? null,
      saleStartsAt: product.saleStartsAt ?? null,
      saleEndsAt: product.saleEndsAt ?? null,
    };
```

`api/adminReportsApi.ts` — đổi import `import type { PriceSavings, ReportFilterParams, ReportType, TopProduct } from '../types/admin';` và thêm vào object (sau `getRevenueByStore`):

```ts

  /** Tiền ưu đãi từ giá KM và combo (đơn đã giao) */
  async getPriceSavings(params: ReportFilterParams = {}): Promise<PriceSavings> {
    const res = await axiosClient.get<ApiResponse<PriceSavings>>('/admin/reports/price-savings', { params });
    return res.data.data;
  },
```

`api/managerApi.ts` — đổi import `import type { DailyRevenue, DashboardMetrics, PriceSavings, ReportFilterParams, TopProduct } from '../types/admin';` và thêm vào object (sau `topProducts`):

```ts
  async priceSavings(params: ReportFilterParams = {}): Promise<PriceSavings> {
    const res = await axiosClient.get<ApiResponse<PriceSavings>>('/manager/reports/price-savings', { params });
    return res.data.data;
  },
```

- [ ] **Step 6: Kiểm**

Run (`frontend`): `npx tsc -b`
Expected: 0 lỗi.
Run: `npx oxlint src`
Expected: không thêm cảnh báo so với trước Task 11.

- [ ] **Step 7: Commit (người dùng tự chạy)**

```
git add frontend/src/types frontend/src/api/catalogApi.ts frontend/src/api/staffCatalogApi.ts frontend/src/api/adminReportsApi.ts frontend/src/api/managerApi.ts
git commit -m "feat(frontend): kiểu dữ liệu giá khuyến mãi, combo, tiền tiết kiệm và API báo cáo ưu đãi"
```

---

## Task 12: Frontend — gạch giá trên thẻ món, chip "Đang khuyến mãi", trang chi tiết món/combo

**Files:**
- Create: `frontend/src/utils/pricing.ts`, `frontend/src/components/product/PriceTag.tsx`, `frontend/src/components/product/ComboContents.tsx`, `frontend/src/styles/components/pricing.css`
- Modify: `frontend/src/components/product/ProductCard.tsx`, `frontend/src/pages/MenuPage.tsx`, `frontend/src/pages/ProductDetailPage.tsx`

**Interfaces:**
- Consumes: kiểu của Task 11.
- Produces (dùng ở Task 13–15):
  - `utils/pricing.ts`: `priceNow(product): number`; `cartUnitCompareAt(item: CartItem): number | null`; `cartSavings(items: CartItem[]): number`; `orderUnitCompareAt(item: OrderItemResponse): number | null`; `orderComponentsText(item: OrderItemResponse): string` ("Gồm: 1× A · 2× B" hoặc ""); `type SaleState = 'ACTIVE' | 'UPCOMING' | 'ENDED' | null`; `saleState(product, now?): SaleState`; `formatSaleEnd(iso: string): string`; `toDateTimeLocal(iso?: string | null): string`.
  - `<PriceTag price compareAt? className? />` — giá đang bán + giá gốc gạch khi `compareAt > price`.
  - `<ComboContents items={{ name, quantity }[]} prefix? />` — dòng "Gồm: …"; rỗng → không render.
  - CSS: `.price-tag`, `.price-tag__was`, `.sale-flag`, `.combo-flag`, `.combo-contents`, `.pcard__flag--sale`, `.menu__sale-chip`, `.pdetail__sale-end`, `.pdetail__combo`, `.ord-card__saving`.

- [ ] **Step 1: `utils/pricing.ts`**

```ts
import type { CartItem } from '../types/cart';
import type { OrderItemResponse } from '../types/order';
import type { ProductItem } from '../types/staff';

/** Giá đang bán của món — server tính (effectivePrice); dữ liệu thiếu field thì dùng giá gốc. */
export const priceNow = (product: Pick<ProductItem, 'price' | 'effectivePrice'>): number =>
  product.effectivePrice ?? product.price;

/** Giá gạch của 1 đơn vị trong giỏ (đã gồm topping); null khi dòng không có ưu đãi. */
export const cartUnitCompareAt = (item: CartItem): number | null => {
  if (item.originalUnitPrice == null || item.originalUnitPrice <= item.basePrice) return null;
  return item.unitPrice + (item.originalUnitPrice - item.basePrice);
};

/**
 * Tiền tiết kiệm của giỏ, tính lại từ từng dòng — khớp cả khi CartProvider cập nhật số lượng
 * lạc quan (chưa có phản hồi server).
 */
export const cartSavings = (items: CartItem[]): number =>
  items.reduce((sum, item) => {
    const perUnit = (item.originalUnitPrice ?? item.basePrice) - item.basePrice;
    return perUnit > 0 ? sum + perUnit * item.quantity : sum;
  }, 0);

/** Giá gạch 1 đơn vị của dòng đơn (chưa topping, theo snapshot); đơn cũ → null. */
export const orderUnitCompareAt = (item: OrderItemResponse): number | null =>
  item.originalUnitPrice != null && item.originalUnitPrice > item.unitPrice ? item.originalUnitPrice : null;

/** "Gồm: 1× Bánh mì · 2× Cà phê" cho dòng combo trong đơn; món lẻ → "". */
export const orderComponentsText = (item: OrderItemResponse): string => {
  const components = item.components ?? [];
  if (components.length === 0) return '';
  return `Gồm: ${components.map((component) => `${component.quantity}× ${component.productName}`).join(' · ')}`;
};

export type SaleState = 'ACTIVE' | 'UPCOMING' | 'ENDED' | null;

/** Nhãn KM cho admin — chỉ hiển thị; server mới là nguồn sự thật của giá. */
export const saleState = (product: ProductItem, now: Date = new Date()): SaleState => {
  if (product.productType === 'COMBO' || product.salePrice == null) return null;
  if (product.onSale) return 'ACTIVE';
  if (product.saleStartsAt && new Date(product.saleStartsAt) > now) return 'UPCOMING';
  if (product.saleEndsAt && new Date(product.saleEndsAt) <= now) return 'ENDED';
  return null;
};

/** Hạn KM dạng "dd/MM HH:mm" (chuỗi server là giờ Việt Nam, không kèm múi giờ). */
export const formatSaleEnd = (iso: string): string =>
  new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

/** Giá trị cho `<input type="datetime-local">` từ chuỗi server (bỏ phần giây). */
export const toDateTimeLocal = (iso?: string | null): string => (iso ? iso.slice(0, 16) : '');
```

- [ ] **Step 2: `components/product/PriceTag.tsx` và `components/product/ComboContents.tsx`**

```tsx
import { formatCurrency } from '../../utils/formatters';
import '../../styles/components/pricing.css';

export interface PriceTagProps {
  /** Giá đang bán */
  price: number;
  /** Giá gạch — chỉ hiện khi lớn hơn giá đang bán */
  compareAt?: number | null;
  /** Lớp cho số tiền chính, để giữ cỡ chữ riêng của từng chỗ dùng */
  className?: string;
}

/** Giá bán kèm giá gốc gạch ngang (giá KM / combo). Server quyết định có gạch hay không. */
export const PriceTag = ({ price, compareAt, className = '' }: PriceTagProps) => (
  <span className="price-tag">
    <span className={className}>{formatCurrency(price)}</span>
    {compareAt != null && compareAt > price && (
      <s className="price-tag__was" aria-label={`Giá gốc ${formatCurrency(compareAt)}`}>
        {formatCurrency(compareAt)}
      </s>
    )}
  </span>
);
```

```tsx
import '../../styles/components/pricing.css';

export interface ComboContentsProps {
  items: Array<{ name: string; quantity: number }>;
  prefix?: string;
}

/** Dòng "Gồm: 1× Bánh mì · 1× Cà phê" của combo; không có thành phần thì không hiện gì. */
export const ComboContents = ({ items, prefix = 'Gồm' }: ComboContentsProps) =>
  items.length === 0 ? null : (
    <span className="combo-contents">
      {prefix}: {items.map((item) => `${item.quantity}× ${item.name}`).join(' · ')}
    </span>
  );
```

- [ ] **Step 3: `styles/components/pricing.css`**

```css
/* Giá khuyến mãi gạch giá + combo (spec combo-sale §7) */
.price-tag {
  display: inline-flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.price-tag__was {
  color: var(--stone-500);
  font-size: .85em;
  font-weight: 600;
}

.sale-flag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  margin-left: var(--space-2);
  padding: 0 8px;
  border-radius: var(--radius-full);
  background: var(--danger);
  color: #fff;
  font-size: var(--text-xs);
  font-weight: 800;
  vertical-align: middle;
}

.combo-flag {
  display: inline-flex;
  align-items: center;
  height: 20px;
  margin-right: 6px;
  padding: 0 8px;
  border-radius: var(--radius-full);
  background: var(--primary-100);
  color: var(--primary-800);
  font-size: 11px;
  font-weight: 800;
  vertical-align: middle;
}

.combo-contents {
  display: block;
  margin-top: 2px;
  font-size: var(--text-xs);
  color: var(--stone-600);
}

/* Nhãn −x% trên ảnh thẻ món: góc phải trên (rating nằm góc phải dưới) */
.pcard__flag--sale {
  left: auto;
  right: 10px;
  background: var(--danger);
  color: #fff;
}

.menu__sale-chip {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.pdetail__sale-end {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--danger);
}

.pdetail__combo {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.pdetail__combo a {
  color: var(--primary-700);
  font-weight: 700;
}

.ord-card__saving {
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--success);
}
```

- [ ] **Step 4: `components/product/ProductCard.tsx`**

Thêm import:

```tsx
import { PriceTag } from './PriceTag';
import { ComboContents } from './ComboContents';
import { priceNow } from '../../utils/pricing';
```

và bỏ `import { formatCurrency } from '../../utils/formatters';` (không còn dùng).

Ngay sau `const detailPath = \`/products/${product.id}\`;` thêm:

```tsx
  const isCombo = product.productType === 'COMBO';
  const discount = product.discountPercent ?? 0;
```

Ngay sau khối `{!disabled && product.featured && ( ... )}` thêm:

```tsx
        {!disabled && discount > 0 && <span className="pcard__flag pcard__flag--sale">−{discount}%</span>}
```

Thay:

```tsx
          <Link className="pcard__name-link" to={detailPath}>
            {product.name}
          </Link>
        </h3>
        {product.description && <p className="pcard__desc">{product.description}</p>}
```

bằng:

```tsx
          <Link className="pcard__name-link" to={detailPath}>
            {isCombo && <span className="combo-flag">Combo</span>}
            {product.name}
          </Link>
        </h3>
        {isCombo && (
          <ComboContents
            items={(product.comboItems ?? []).map((item) => ({ name: item.name, quantity: item.quantity }))}
          />
        )}
        {product.description && <p className="pcard__desc">{product.description}</p>}
```

Thay:

```tsx
          <span className="pcard__price">{formatCurrency(product.price)}</span>
```

bằng:

```tsx
          <PriceTag className="pcard__price" price={priceNow(product)} compareAt={product.compareAtPrice} />
```

- [ ] **Step 5: `pages/MenuPage.tsx` — chip "Đang khuyến mãi"**

Đổi import icon: `import { Percent, SearchX, SlidersHorizontal } from 'lucide-react';` và thêm `import '../styles/components/pricing.css';` dưới dòng import `menu.css`.

Ngay sau `const maxPrice = parsePrice(maxPriceParam);` thêm:

```tsx
  // Lọc "Đang khuyến mãi" ở server (onSale=true): món lẻ có giá KM đang hiệu lực
  const onSale = searchParams.get('onSale') === '1';
```

Trong lời gọi `catalogApi.getProducts({...})`, ngay sau `maxPrice: maxPrice ?? undefined,` thêm `onSale: onSale || undefined,`; đổi dependency:

```tsx
  }, [keyword, categoryId, minPrice, maxPrice, sort, page, reloadKey]);
```

thành:

```tsx
  }, [keyword, categoryId, minPrice, maxPrice, onSale, sort, page, reloadKey]);
```

Trong `updateParams`: thêm vào kiểu tham số `onSale?: boolean;` (sau `maxPrice?: number | null;`); ngay sau `const nextMaxPrice = ...;` thêm `const nextOnSale = next.onSale !== undefined ? next.onSale : onSale;`; ngay sau `if (nextMaxPrice) params.set('maxPrice', String(nextMaxPrice));` thêm `if (nextOnSale) params.set('onSale', '1');`.

Thay:

```tsx
  const hasFilter = Boolean(keyword) || categoryId !== null || hasPriceFilter;

  const clearFilters = () => updateParams({ keyword: '', categoryId: null, minPrice: null, maxPrice: null });
```

bằng:

```tsx
  const hasFilter = Boolean(keyword) || categoryId !== null || hasPriceFilter || onSale;

  const clearFilters = () =>
    updateParams({ keyword: '', categoryId: null, minPrice: null, maxPrice: null, onSale: false });
```

Ngay sau thẻ đóng của `<ChipGroup ... />` danh mục trong `.menu__filterbar` (trước comment `{/* Khoảng giá ... */}`) thêm:

```tsx
          <button
            type="button"
            className={`ui-chip menu__sale-chip${onSale ? ' ui-chip--active' : ''}`}
            aria-pressed={onSale}
            onClick={() => updateParams({ onSale: !onSale })}
          >
            <Percent size={14} /> Đang khuyến mãi
          </button>
```

- [ ] **Step 6: `pages/ProductDetailPage.tsx`**

Thêm import:

```tsx
import { PriceTag } from '../components/product/PriceTag';
import { formatSaleEnd, priceNow } from '../utils/pricing';
import '../styles/components/pricing.css';
```

Thay:

```tsx
  const unitPrice = (product?.price ?? 0) + extraPerUnit;
```

bằng:

```tsx
  // Giá đang bán do server tính (giá KM / giá combo); topping cộng thêm như cũ
  const unitPrice = (product ? priceNow(product) : 0) + extraPerUnit;
  const isCombo = product?.productType === 'COMBO';
```

Thay:

```tsx
          <p className="pdetail__price">{formatCurrency(product.price)}</p>
```

bằng:

```tsx
          <p className="pdetail__price">
            <PriceTag price={priceNow(product)} compareAt={product.compareAtPrice} />
            {(product.discountPercent ?? 0) > 0 && <span className="sale-flag">−{product.discountPercent}%</span>}
          </p>
          {product.onSale && product.saleEndsAt && (
            <p className="pdetail__sale-end">KM đến {formatSaleEnd(product.saleEndsAt)}</p>
          )}
          {isCombo && (product.comboItems ?? []).length > 0 && (
            <div className="pdetail__opts-group">
              <div className="pdetail__group-head">
                <span className="pdetail__group-title">Combo gồm</span>
                {product.compareAtPrice != null && (
                  <span className="pdetail__group-hint">Mua lẻ {formatCurrency(product.compareAtPrice)}</span>
                )}
              </div>
              <ul className="pdetail__combo">
                {(product.comboItems ?? []).map((item) => (
                  <li key={item.productId}>
                    {item.quantity} × <Link to={`/products/${item.productId}`}>{item.name}</Link>{' '}
                    <span className="pdetail__group-hint">({formatCurrency(item.price)}/phần)</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
```

Thay `{optionGroups.map((group) => (` bằng `{!isCombo && optionGroups.map((group) => (` và `{flatOptions.length > 0 && (` bằng `{!isCombo && flatOptions.length > 0 && (` (combo không có khối topping — spec §7).

- [ ] **Step 7: Kiểm**

Run (`frontend`): `npx tsc -b` → 0 lỗi; `npm run build` → `✓ built`; `npx oxlint src` → không thêm cảnh báo.

- [ ] **Step 8: Commit (người dùng tự chạy)**

```
git add frontend/src/utils/pricing.ts frontend/src/components/product frontend/src/styles/components/pricing.css frontend/src/pages/MenuPage.tsx frontend/src/pages/ProductDetailPage.tsx
git commit -m "feat(frontend): gạch giá, nhãn -x%, nhãn combo, lọc Đang khuyến mãi, trang chi tiết combo"
```

---

## Task 13: Frontend — giỏ, thanh toán, theo dõi đơn, danh sách đơn, bếp, shipper

**Files:**
- Modify: `frontend/src/context/cartContextDef.ts`, `frontend/src/context/CartProvider.tsx`, `frontend/src/pages/CartPage.tsx`, `frontend/src/pages/CheckoutPage.tsx`, `frontend/src/pages/OrderTrackingPage.tsx`, `frontend/src/pages/OrdersPage.tsx`, `frontend/src/pages/staff/StaffOrderQueuePage.tsx`, `frontend/src/pages/shipper/ShipperOrdersPage.tsx`

**Interfaces:**
- Consumes: `PriceTag`, `ComboContents`, `cartUnitCompareAt`, `cartSavings`, `orderUnitCompareAt`, `orderComponentsText` (Task 12).
- Produces: `CartContextType.savingsAmount: number` (tính từ dòng giỏ, khớp cập nhật lạc quan).

- [ ] **Step 1: Context giỏ**

`context/cartContextDef.ts` — ngay sau `  subtotal: number;` thêm:

```ts
  /** Tiền tiết kiệm nhờ giá KM/combo — tính lại từ các dòng giỏ */
  savingsAmount: number;
```

`context/CartProvider.tsx` — thêm `import { cartSavings } from '../utils/pricing';`. Thay `return { totalQuantity: 0, subtotal: 0 };` bằng `return { totalQuantity: 0, subtotal: 0, savingsAmount: 0 };` và `return { totalQuantity, subtotal };` bằng `return { totalQuantity, subtotal, savingsAmount: cartSavings(cart.items) };`. Trong `contextValue`, ngay sau `      subtotal: computedTotals.subtotal,` thêm `      savingsAmount: computedTotals.savingsAmount,`; trong mảng dependency, ngay sau dòng `      computedTotals.subtotal,` thêm `      computedTotals.savingsAmount,`.

- [ ] **Step 2: `pages/CartPage.tsx`**

Thêm import:

```tsx
import { PriceTag } from '../components/product/PriceTag';
import { ComboContents } from '../components/product/ComboContents';
import { cartUnitCompareAt } from '../utils/pricing';
```

Thay dòng khai báo `useCart()`:

```tsx
  const { cart, isLoading, isUpdating, error, subtotal, totalQuantity, updateQuantity, removeItem, clearCart } =
    useCart();
```

bằng:

```tsx
  const {
    cart,
    isLoading,
    isUpdating,
    error,
    subtotal,
    savingsAmount,
    totalQuantity,
    updateQuantity,
    removeItem,
    clearCart,
  } = useCart();
```

Thay:

```tsx
                    <p className="cart-row__unit">Đơn giá {formatCurrency(item.unitPrice)}</p>
```

bằng:

```tsx
                    {item.productType === 'COMBO' && (
                      <ComboContents
                        items={(item.comboItems ?? []).map((c) => ({ name: c.name, quantity: c.quantity }))}
                      />
                    )}
                    <p className="cart-row__unit">
                      Đơn giá <PriceTag price={item.unitPrice} compareAt={cartUnitCompareAt(item)} />
                    </p>
```

Ngay sau khối:

```tsx
              <div className="summary__row">
                <span>Tạm tính ({totalQuantity} món)</span>
                <span>{formatCurrency(subtotal)}</span>
              </div>
```

thêm:

```tsx
              {savingsAmount > 0 && (
                <div className="summary__row summary__row--free">
                  <span>Bạn tiết kiệm được</span>
                  <span>{formatCurrency(savingsAmount)}</span>
                </div>
              )}
```

- [ ] **Step 3: `pages/CheckoutPage.tsx`**

Thêm import (sau dòng `import { describePromotionValue } from '../utils/promotion';`):

```tsx
import { PriceTag } from '../components/product/PriceTag';
import { ComboContents } from '../components/product/ComboContents';
import { cartUnitCompareAt } from '../utils/pricing';
```

Thay `const { cart, isLoading: isCartLoading, refreshCart, subtotal, totalQuantity } = useCart();` bằng:

```tsx
  const { cart, isLoading: isCartLoading, refreshCart, subtotal, savingsAmount, totalQuantity } = useCart();
```

Thay:

```tsx
                    <p className="ck__item-name">{item.productName}</p>
                    <p className="ck__item-meta">
                      {item.quantity} × {formatCurrency(item.unitPrice)}
                    </p>
```

bằng:

```tsx
                    <p className="ck__item-name">{item.productName}</p>
                    {item.productType === 'COMBO' && (
                      <ComboContents
                        items={(item.comboItems ?? []).map((c) => ({ name: c.name, quantity: c.quantity }))}
                      />
                    )}
                    <p className="ck__item-meta">
                      {item.quantity} × <PriceTag price={item.unitPrice} compareAt={cartUnitCompareAt(item)} />
                    </p>
```

Ngay sau khối:

```tsx
            <div className="summary__row">
              <span>Tạm tính</span>
              <span>{formatCurrency(subtotal)}</span>
            </div>
```

thêm:

```tsx
            {savingsAmount > 0 && (
              <div className="summary__row summary__row--free">
                <span>Bạn tiết kiệm được</span>
                <span>{formatCurrency(savingsAmount)}</span>
              </div>
            )}
```

(Mã giảm giá vẫn tính trên `subtotal` đã theo giá KM — B3 — nên không đổi `orderAmount: subtotal`.)

- [ ] **Step 4: `pages/OrderTrackingPage.tsx`**

Thêm import:

```tsx
import { PriceTag } from '../components/product/PriceTag';
import { ComboContents } from '../components/product/ComboContents';
import { orderUnitCompareAt } from '../utils/pricing';
```

Thay:

```tsx
                    <p className="cart-row__unit">
                      {formatCurrency(item.unitPrice)} × {item.quantity}
                    </p>
```

bằng:

```tsx
                    {(item.components ?? []).length > 0 && (
                      <ComboContents
                        items={(item.components ?? []).map((c) => ({ name: c.productName, quantity: c.quantity }))}
                      />
                    )}
                    <p className="cart-row__unit">
                      <PriceTag price={item.unitPrice} compareAt={orderUnitCompareAt(item)} /> × {item.quantity}
                    </p>
```

Ngay sau khối giảm giá:

```tsx
              {order.discountAmount > 0 && (
                <div className="summary__row summary__row--free">
                  <span>Giảm giá{order.promotionCode ? ` (${order.promotionCode})` : ''}</span>
                  <span>-{formatCurrency(order.discountAmount)}</span>
                </div>
              )}
```

thêm:

```tsx
              {(order.savingsAmount ?? 0) > 0 && (
                <p className="summary__row summary__row--note summary__row--free">
                  Bạn đã tiết kiệm {formatCurrency(order.savingsAmount)} nhờ giá khuyến mãi và combo (đã tính trong tạm tính)
                </p>
              )}
```

- [ ] **Step 5: `pages/OrdersPage.tsx`**

Thêm import:

```tsx
import { PriceTag } from '../components/product/PriceTag';
import '../styles/components/pricing.css';
```

Thay:

```tsx
                    {order.items.map((item) => `${item.quantity}× ${item.productName}`).join(' · ')}
```

bằng:

```tsx
                    {order.items
                      .map((item) => {
                        const parts = (item.components ?? []).map((c) => `${c.quantity}× ${c.productName}`);
                        return `${item.quantity}× ${item.productName}${parts.length > 0 ? ` (${parts.join(', ')})` : ''}`;
                      })
                      .join(' · ')}
```

Thay:

```tsx
                  <span className="ord-card__total">{formatCurrency(order.total)}</span>
```

bằng:

```tsx
                  <PriceTag
                    className="ord-card__total"
                    price={order.total}
                    compareAt={(order.savingsAmount ?? 0) > 0 ? order.total + (order.savingsAmount ?? 0) : null}
                  />
                  {(order.savingsAmount ?? 0) > 0 && (
                    <span className="ord-card__saving">Tiết kiệm {formatCurrency(order.savingsAmount)}</span>
                  )}
```

- [ ] **Step 6: Bếp và shipper — dòng combo kèm thành phần**

`pages/staff/StaffOrderQueuePage.tsx` — thêm `import { orderComponentsText } from '../../utils/pricing';`; thay:

```tsx
                          <strong>{item.productName}</strong>
                          {item.options && item.options.length > 0 && (
                            <em>
```

bằng:

```tsx
                          <strong>{item.productName}</strong>
                          {orderComponentsText(item) && <em>{orderComponentsText(item)}</em>}
                          {item.options && item.options.length > 0 && (
                            <em>
```

`pages/shipper/ShipperOrdersPage.tsx` — thêm `import { orderComponentsText } from '../../utils/pricing';`; thay:

```tsx
                  <strong>{item.productName}</strong>
                  {item.options && item.options.length > 0 && (
                    <em>{item.options.map((option) => `+ ${option.optionName}`).join(' · ')}</em>
```

bằng:

```tsx
                  <strong>{item.productName}</strong>
                  {orderComponentsText(item) && <em>{orderComponentsText(item)}</em>}
                  {item.options && item.options.length > 0 && (
                    <em>{item.options.map((option) => `+ ${option.optionName}`).join(' · ')}</em>
```

- [ ] **Step 7: Kiểm**

Run (`frontend`): `npx tsc -b` → 0 lỗi; `npm run build` → `✓ built`; `npx oxlint src` → không thêm cảnh báo.

- [ ] **Step 8: Commit (người dùng tự chạy)**

```
git add frontend/src/context/cartContextDef.ts frontend/src/context/CartProvider.tsx frontend/src/pages/CartPage.tsx frontend/src/pages/CheckoutPage.tsx frontend/src/pages/OrderTrackingPage.tsx frontend/src/pages/OrdersPage.tsx frontend/src/pages/staff/StaffOrderQueuePage.tsx frontend/src/pages/shipper/ShipperOrdersPage.tsx
git commit -m "feat(frontend): gạch giá và tiền tiết kiệm ở giỏ, thanh toán, đơn hàng; thành phần combo cho bếp và shipper"
```

---

## Task 14: Frontend — Admin `/admin/menu`: giá KM, nhãn KM, tab Combo

**Files:**
- Modify: `frontend/src/pages/staff/StaffMenuPage.tsx`, `frontend/src/styles/components/staff-menu.css`

**Interfaces:**
- Consumes: `ProductType`, `ComboItemPayload`, `ProductCreatePayload` (Task 11); `PriceTag`, `ComboContents`, `saleState`, `toDateTimeLocal` (Task 12); `staffCatalogApi.createProduct/updateProduct/toggleProductAvailability` (có sẵn / Task 11).
- Produces: UI — tab **Món lẻ / Combo**; form món lẻ có *Giá khuyến mãi*, *Bắt đầu*, *Kết thúc* (`datetime-local`); nhãn **Đang KM −x%** / **Sắp KM** / **KM đã hết**; form combo (tên, danh mục, ảnh, mô tả, nổi bật, còn bán, giá combo, bảng thành phần 1–20, dòng "Tổng giá lẻ X → Giá combo Y (tiết kiệm Z%)", chặn lưu khi Y ≥ X).

- [ ] **Step 1: Import**

Thay khối import UI:

```tsx
import {
  Button,
  ChipGroup,
  EmptyState,
  Input,
  Modal,
  PageHeader,
  Select,
  Skeleton,
  Spinner,
  Textarea,
  useConfirm,
  useToast,
} from '../../components/ui';
```

bằng:

```tsx
import {
  Badge,
  Button,
  ChipGroup,
  EmptyState,
  Input,
  Modal,
  PageHeader,
  Select,
  Skeleton,
  Spinner,
  Tabs,
  Textarea,
  useConfirm,
  useToast,
} from '../../components/ui';
import { PriceTag } from '../../components/product/PriceTag';
import { ComboContents } from '../../components/product/ComboContents';
import { saleState, toDateTimeLocal } from '../../utils/pricing';
```

Thay khối import type:

```tsx
import type {
  CategoryItem,
  OptionGroupPayload,
  ProductCreatePayload,
  ProductItem,
} from '../../types/staff';
```

bằng:

```tsx
import type {
  CategoryItem,
  ComboItemPayload,
  OptionGroupPayload,
  ProductCreatePayload,
  ProductItem,
  ProductType,
} from '../../types/staff';
```

- [ ] **Step 2: Form mặc định, form combo, nhãn KM**

Thay `emptyForm`:

```tsx
const emptyForm = (categoryId: number): ProductCreatePayload => ({
  categoryId,
  name: '',
  description: '',
  imageUrl: '',
  images: [],
  price: DEFAULT_PRICE,
  available: true,
  featured: false,
  optionGroups: [],
});
```

bằng:

```tsx
const emptyForm = (categoryId: number): ProductCreatePayload => ({
  categoryId,
  name: '',
  description: '',
  imageUrl: '',
  images: [],
  price: DEFAULT_PRICE,
  available: true,
  featured: false,
  optionGroups: [],
  productType: 'SINGLE',
  salePrice: null,
  saleStartsAt: null,
  saleEndsAt: null,
});

const emptyCombo = (categoryId: number): ProductCreatePayload => ({
  categoryId,
  name: '',
  description: '',
  imageUrl: '',
  price: 0,
  available: true,
  featured: false,
  productType: 'COMBO',
  comboItems: [
    { productId: 0, quantity: 1 },
    { productId: 0, quantity: 1 },
  ],
});

/** Payload sửa combo từ dữ liệu đang có — giữ cờ bật/tắt của CHÍNH combo (`enabled`). */
const comboFormOf = (product: ProductItem): ProductCreatePayload => ({
  categoryId: product.categoryId,
  name: product.name,
  description: product.description ?? '',
  imageUrl: product.imageUrl ?? '',
  price: product.price,
  available: product.enabled ?? product.available,
  featured: product.featured,
  productType: 'COMBO',
  comboItems: (product.comboItems ?? []).map((item) => ({ productId: item.productId, quantity: item.quantity })),
});

/** Nhãn KM trên thẻ món (spec §7): Đang KM −x% / Sắp KM / KM đã hết. */
const SaleBadge = ({ product }: { product: ProductItem }) => {
  const state = saleState(product);
  if (state === 'ACTIVE') return <Badge tone="danger">Đang KM −{product.discountPercent ?? 0}%</Badge>;
  if (state === 'UPCOMING') return <Badge tone="info">Sắp KM</Badge>;
  if (state === 'ENDED') return <Badge tone="neutral">KM đã hết</Badge>;
  return null;
};
```

- [ ] **Step 3: Tab Món lẻ / Combo + danh sách**

Ngay sau `  const [editingProduct, setEditingProduct] = useState<ProductItem | null>(null);` thêm:

```tsx
  const [tab, setTab] = useState<ProductType>('SINGLE');
  /** `product: null` = tạo combo mới */
  const [comboForm, setComboForm] = useState<{ product: ProductItem | null } | null>(null);
```

Thay:

```tsx
    return products.filter((product) => {
      if (selectedCategoryId && product.categoryId !== selectedCategoryId) return false;
```

bằng:

```tsx
    return products.filter((product) => {
      if ((product.productType ?? 'SINGLE') !== tab) return false;
      if (selectedCategoryId && product.categoryId !== selectedCategoryId) return false;
```

Thay `  }, [products, selectedCategoryId, searchQuery]);` bằng:

```tsx
  }, [products, selectedCategoryId, searchQuery, tab]);

  const singles = useMemo(
    () => products.filter((product) => (product.productType ?? 'SINGLE') === 'SINGLE'),
    [products],
  );
  const comboCount = products.length - singles.length;
```

Trong `handleToggleAvailable`, thay:

```tsx
      toast.success(`${updated.name}: ${updated.available ? 'bán lại toàn chuỗi' : 'đã ngừng bán toàn chuỗi'}`);
```

bằng:

```tsx
      const enabled = updated.enabled ?? updated.available;
      toast.success(`${updated.name}: ${enabled ? 'bán lại toàn chuỗi' : 'đã ngừng bán toàn chuỗi'}`);
```

Thay:

```tsx
      <section className="card">
        <div className="smenu__controls">
          <ChipGroup
```

bằng:

```tsx
      <Tabs<ProductType>
        tabs={[
          { key: 'SINGLE', label: 'Món lẻ', count: singles.length },
          { key: 'COMBO', label: 'Combo', count: comboCount },
        ]}
        value={tab}
        onChange={setTab}
      />

      <section className="card">
        <div className="smenu__controls">
          <ChipGroup
```

Thay:

```tsx
            <Button variant="primary" icon={<Plus size={17} />} onClick={() => setShowCreateModal(true)}>
              Thêm món mới
            </Button>
```

bằng:

```tsx
            <Button
              variant="primary"
              icon={<Plus size={17} />}
              onClick={() => {
                if (tab === 'COMBO') {
                  setComboForm({ product: null });
                } else {
                  setShowCreateModal(true);
                }
              }}
            >
              {tab === 'COMBO' ? 'Thêm combo' : 'Thêm món mới'}
            </Button>
```

Thay `            const busy = busyProductId === product.id;` bằng:

```tsx
            const busy = busyProductId === product.id;
            const isCombo = product.productType === 'COMBO';
            // Nút bật/tắt dùng cờ của chính món; `available` của combo còn tính thành phần
            const isEnabled = product.enabled ?? product.available;
```

Thay:

```tsx
                <div className="smenu__body">
                  <div className="smenu__head">
                    <h3 className="smenu__name">{product.name}</h3>
                    <span className="smenu__price">{formatCurrency(product.price)}</span>
                  </div>
                  <p className="smenu__desc">{product.description || 'Chưa có mô tả cho món này.'}</p>

                  <Button
                    size="sm"
                    variant={product.available ? 'secondary' : 'danger'}
                    loading={busy}
                    onClick={() => void handleToggleAvailable(product)}
                  >
                    {product.available ? 'Đang bán toàn chuỗi — ngừng bán' : 'Đã ngừng bán — bán lại toàn chuỗi'}
                  </Button>
                </div>

                <footer className="smenu__foot">
                  <Button size="sm" variant="secondary" onClick={() => setEditingProduct(product)}>
                    Sửa món / giá
                  </Button>
```

bằng:

```tsx
                <div className="smenu__body">
                  <div className="smenu__head">
                    <h3 className="smenu__name">{product.name}</h3>
                    <PriceTag
                      className="smenu__price"
                      price={product.effectivePrice ?? product.price}
                      compareAt={product.compareAtPrice}
                    />
                  </div>
                  <div className="smenu__badges">
                    <SaleBadge product={product} />
                    {isCombo && isEnabled && !product.available && (
                      <Badge tone="warning">Tạm hết — có món thành phần đang ngừng bán</Badge>
                    )}
                  </div>
                  {isCombo && (
                    <ComboContents
                      items={(product.comboItems ?? []).map((item) => ({ name: item.name, quantity: item.quantity }))}
                    />
                  )}
                  <p className="smenu__desc">{product.description || 'Chưa có mô tả cho món này.'}</p>

                  <Button
                    size="sm"
                    variant={isEnabled ? 'secondary' : 'danger'}
                    loading={busy}
                    onClick={() => void handleToggleAvailable(product)}
                  >
                    {isEnabled ? 'Đang bán toàn chuỗi — ngừng bán' : 'Đã ngừng bán — bán lại toàn chuỗi'}
                  </Button>
                </div>

                <footer className="smenu__foot">
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => (isCombo ? setComboForm({ product }) : setEditingProduct(product))}
                  >
                    {isCombo ? 'Sửa combo' : 'Sửa món / giá'}
                  </Button>
```

- [ ] **Step 4: Form món lẻ — giá KM**

Trong `initial` của modal sửa món, thay `            available: editingProduct.available,` bằng:

```tsx
            available: editingProduct.enabled ?? editingProduct.available,
            salePrice: editingProduct.salePrice ?? null,
            saleStartsAt: toDateTimeLocal(editingProduct.saleStartsAt) || null,
            saleEndsAt: toDateTimeLocal(editingProduct.saleEndsAt) || null,
```

Trong `ProductFormModal`, ngay sau khối `const groupsAreValid = ...;` thêm:

```tsx
  // Kiểm sớm ở client cho dễ sửa — server vẫn kiểm lại và trả lỗi tiếng Việt (spec §3)
  const saleError =
    form.salePrice == null
      ? null
      : form.salePrice <= 0
        ? 'Giá khuyến mãi phải lớn hơn 0'
        : form.salePrice >= form.price
          ? 'Giá khuyến mãi phải nhỏ hơn giá gốc'
          : form.saleStartsAt && form.saleEndsAt && form.saleEndsAt <= form.saleStartsAt
            ? 'Thời điểm kết thúc phải sau thời điểm bắt đầu'
            : null;
```

Thay `            disabled={!form.name.trim() || form.price < 0 || !groupsAreValid}` bằng:

```tsx
            disabled={!form.name.trim() || form.price < 0 || !groupsAreValid || saleError !== null}
```

Thay khối ô giá:

```tsx
        <Input
          label="Giá bán (VNĐ)"
          type="number"
          required
          min={0}
          step={1000}
          value={form.price}
          onChange={(event) => setForm({ ...form, price: Number(event.target.value) })}
        />
```

bằng:

```tsx
        <Input
          label="Giá gốc (VNĐ)"
          type="number"
          required
          min={0}
          step={1000}
          value={form.price}
          onChange={(event) => setForm({ ...form, price: Number(event.target.value) })}
        />

        <div className="smenu__sale">
          <Input
            label="Giá khuyến mãi (VNĐ)"
            type="number"
            min={0}
            step={1000}
            placeholder="Bỏ trống = không KM"
            value={form.salePrice ?? ''}
            error={saleError ?? undefined}
            onChange={(event) =>
              setForm({ ...form, salePrice: event.target.value === '' ? null : Number(event.target.value) })
            }
          />
          <Input
            label="Bắt đầu"
            type="datetime-local"
            hint="Bỏ trống = áp dụng ngay"
            disabled={form.salePrice == null}
            value={toDateTimeLocal(form.saleStartsAt)}
            onChange={(event) => setForm({ ...form, saleStartsAt: event.target.value || null })}
          />
          <Input
            label="Kết thúc"
            type="datetime-local"
            hint="Bỏ trống = không hết hạn"
            disabled={form.salePrice == null}
            value={toDateTimeLocal(form.saleEndsAt)}
            onChange={(event) => setForm({ ...form, saleEndsAt: event.target.value || null })}
          />
        </div>
```

- [ ] **Step 5: Modal combo**

Ngay sau khối:

```tsx
            toast.success(`Đã cập nhật món ${updated.name}`);
          }}
        />
      )}
```

thêm:

```tsx

      {comboForm && (
        <ComboFormModal
          title={comboForm.product ? `Sửa combo — ${comboForm.product.name}` : 'Thêm combo mới'}
          categories={categories}
          singles={singles}
          initial={comboForm.product ? comboFormOf(comboForm.product) : emptyCombo(categories[0]?.id ?? 1)}
          submitLabel={comboForm.product ? 'Lưu combo' : 'Thêm combo'}
          onClose={() => setComboForm(null)}
          onSubmit={async (payload) => {
            const editing = comboForm.product;
            if (editing) {
              const updated = await staffCatalogApi.updateProduct(editing.id, payload);
              replaceProduct(updated);
            } else {
              const created = await staffCatalogApi.createProduct(payload);
              setProducts((prev) => [created, ...prev]);
            }
            setComboForm(null);
            toast.success(editing ? `Đã cập nhật combo ${payload.name}` : `Đã thêm combo ${payload.name}`);
          }}
        />
      )}
```

Thêm vào **cuối file**:

```tsx

interface ComboFormModalProps {
  title: string;
  categories: CategoryItem[];
  /** Món lẻ chưa xoá — nguồn chọn thành phần */
  singles: ProductItem[];
  initial: ProductCreatePayload;
  submitLabel: string;
  onClose: () => void;
  onSubmit: (payload: ProductCreatePayload) => Promise<void>;
}

/**
 * Form tạo/sửa combo cố định (spec §7): bảng thành phần + dòng tóm tắt "Tổng giá lẻ X → Giá combo Y".
 * Chặn lưu ở client khi Y ≥ X hoặc thành phần sai luật; server vẫn kiểm lại.
 */
const ComboFormModal = ({
  title,
  categories,
  singles,
  initial,
  submitLabel,
  onClose,
  onSubmit,
}: ComboFormModalProps) => {
  const [form, setForm] = useState(initial);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const toast = useToast();

  const lines: ComboItemPayload[] = form.comboItems ?? [];
  const priceOf = (productId: number) => singles.find((product) => product.id === productId)?.price ?? 0;
  const originalTotal = lines.reduce((sum, line) => sum + priceOf(line.productId) * line.quantity, 0);
  const portions = lines.reduce((sum, line) => sum + line.quantity, 0);
  const savingPercent = originalTotal > 0 ? Math.floor(((originalTotal - form.price) / originalTotal) * 100) : 0;
  const hasDuplicate = new Set(lines.map((line) => line.productId)).size !== lines.length;

  const problem =
    lines.length === 0
      ? 'Thêm ít nhất một món vào combo'
      : lines.some((line) => !line.productId)
        ? 'Chọn món cho mọi dòng'
        : hasDuplicate
          ? 'Mỗi món chỉ một dòng — hãy tăng số lượng thay vì thêm dòng'
          : lines.some((line) => !Number.isInteger(line.quantity) || line.quantity < 1 || line.quantity > 20)
            ? 'Số lượng mỗi món từ 1 đến 20'
            : portions < 2
              ? 'Combo cần tổng ít nhất 2 phần món'
              : form.price <= 0 || form.price >= originalTotal
                ? 'Giá combo phải lớn hơn 0 và thấp hơn tổng giá lẻ'
                : null;

  const setLines = (comboItems: ComboItemPayload[]) => setForm({ ...form, comboItems });
  const patchLine = (index: number, patch: Partial<ComboItemPayload>) =>
    setLines(lines.map((line, i) => (i === index ? { ...line, ...patch } : line)));

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      await onSubmit(form);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Lưu combo thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      title={title}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button
            variant="primary"
            loading={isSubmitting}
            disabled={!form.name.trim() || problem !== null}
            onClick={() => void handleSubmit()}
          >
            {submitLabel}
          </Button>
        </>
      }
    >
      {errorMsg && (
        <div className="alert-banner alert-error" role="alert">
          <XCircle size={17} />
          <div>{errorMsg}</div>
        </div>
      )}

      <div className="smenu__form">
        <Select
          label="Danh mục"
          required
          value={form.categoryId}
          onChange={(event) => setForm({ ...form, categoryId: Number(event.target.value) })}
        >
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </Select>

        <Input
          label="Tên combo"
          required
          placeholder="Ví dụ: Combo Sáng no nê"
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
        />

        <Input
          label="Giá combo (VNĐ)"
          type="number"
          required
          min={0}
          step={1000}
          value={form.price}
          onChange={(event) => setForm({ ...form, price: Number(event.target.value) })}
        />

        <Textarea
          label="Mô tả"
          rows={2}
          value={form.description}
          onChange={(event) => setForm({ ...form, description: event.target.value })}
        />

        <ImageField
          imageUrl={form.imageUrl ?? ''}
          onChange={(imageUrl) => setForm({ ...form, imageUrl })}
          onError={(message) => toast.error(message)}
        />

        <div className="smenu__groups">
          <div className="smenu__groups-head">
            <span className="ui-field__label">Món trong combo</span>
            <Button size="sm" variant="secondary" onClick={() => setLines([...lines, { productId: 0, quantity: 1 }])}>
              <Plus size={15} /> Thêm món
            </Button>
          </div>

          {lines.map((line, index) => (
            <div key={index} className="smenu__group-row smenu__combo-row">
              <Select
                aria-label={`Món thứ ${index + 1}`}
                value={line.productId}
                onChange={(event) => patchLine(index, { productId: Number(event.target.value) })}
              >
                <option value={0}>— Chọn món lẻ —</option>
                {singles.map((product) => (
                  <option key={product.id} value={product.id}>
                    {product.name} · {formatCurrency(product.price)}
                  </option>
                ))}
              </Select>
              <Input
                aria-label={`Số lượng món thứ ${index + 1}`}
                type="number"
                min={1}
                max={20}
                value={line.quantity}
                onChange={(event) => patchLine(index, { quantity: Number(event.target.value) })}
              />
              <Button
                size="sm"
                variant="ghost"
                title="Xoá dòng"
                onClick={() => setLines(lines.filter((_, i) => i !== index))}
              >
                <Trash2 size={15} />
              </Button>
            </div>
          ))}

          <p className="smenu__combo-summary">
            Tổng giá lẻ {formatCurrency(originalTotal)} → Giá combo {formatCurrency(form.price)}
            {originalTotal > form.price && form.price > 0 ? ` (tiết kiệm ${savingPercent}%)` : ''}
          </p>
          {problem && (
            <p className="smenu__combo-problem" role="alert">
              {problem}
            </p>
          )}
        </div>

        <label className="smenu__check">
          <input
            type="checkbox"
            checked={form.featured ?? false}
            onChange={(event) => setForm({ ...form, featured: event.target.checked })}
          />
          <span>Nổi bật (hiện ở trang chủ)</span>
        </label>

        <label className="smenu__check">
          <input
            type="checkbox"
            checked={form.available}
            onChange={(event) => setForm({ ...form, available: event.target.checked })}
          />
          <span>Đang bán toàn chuỗi</span>
        </label>
      </div>
    </Modal>
  );
};
```

- [ ] **Step 6: CSS — thêm cuối `styles/components/staff-menu.css`**

```css

/* Giá khuyến mãi + combo (spec combo-sale §7) */
.smenu__sale {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
}

@media (max-width: 640px) {
  .smenu__sale {
    grid-template-columns: 1fr;
  }
}

.smenu__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.smenu__badges:empty {
  display: none;
}

.smenu__combo-row .ui-field:nth-child(2) {
  flex: 0 0 90px;
}

.smenu__combo-summary {
  margin: var(--space-2) 0 0;
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--stone-700);
}

.smenu__combo-problem {
  margin: 4px 0 0;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--danger);
}
```

- [ ] **Step 7: Kiểm**

Run (`frontend`): `npx tsc -b` → 0 lỗi; `npm run build` → `✓ built`; `npx oxlint src` → không thêm cảnh báo.

- [ ] **Step 8: Commit (người dùng tự chạy)**

```
git add frontend/src/pages/staff/StaffMenuPage.tsx frontend/src/styles/components/staff-menu.css
git commit -m "feat(frontend): admin đặt giá khuyến mãi theo thời gian, nhãn KM, tab và form combo"
```

---

## Task 15: Frontend — Tình trạng món cho combo + ô "Tiền ưu đãi" ở báo cáo

**Files:**
- Modify: `frontend/src/pages/staff/StoreStockPage.tsx`, `frontend/src/pages/admin/AdminReportsPage.tsx`, `frontend/src/pages/manager/ManagerReportsPage.tsx`

**Interfaces:**
- Consumes: `StoreStockItem.productType/blockedBy` (Task 11), `adminReportsApi.getPriceSavings`, `managerApi.priceSavings`, `PriceSavings` (Task 11).
- Produces: UI — combo chỉ có nút bật/tắt, hiện "Tạm hết do: …"; ô **Tiền ưu đãi từ giá KM và combo** ở hai trang báo cáo.

- [ ] **Step 1: `pages/staff/StoreStockPage.tsx`**

Trong `toggle`, thay:

```tsx
      replace(updated);
      toast.success(`${updated.productName}: ${updated.available ? 'mở bán lại' : 'đã báo hết món'}`);
```

bằng:

```tsx
      replace(updated);
      toast.success(`${updated.productName}: ${updated.available ? 'mở bán lại' : 'đã báo hết món'}`);
      // Báo hết / mở lại một món lẻ đổi trạng thái "Tạm hết do" của combo chứa nó → tải lại danh sách
      if (updated.productType !== 'COMBO' && items.some((i) => i.productType === 'COMBO')) {
        void load();
      }
```

Thay:

```tsx
            <article key={item.productId} className={`card smenu__card${item.available && item.onChainMenu ? '' : ' smenu__card--out'}`}>
              <div className="card__body">
                <h3 className="smenu__name">{item.productName}</h3>
                <span className="smenu__price">{formatCurrency(item.price)}</span>
                {!item.onChainMenu && <Badge tone="neutral">Đã ngừng bán toàn chuỗi</Badge>}
                <div className="smenu__stock-row">
                  <span className={`smenu__stock${item.lowStock ? ' smenu__stock--low' : ''}`}>
                    {item.stockQuantity == null ? 'Chưa quản tồn' : `Tồn kho: ${item.stockQuantity}`}
                  </span>
                  <Button size="sm" variant="ghost" onClick={() => setStockItem(item)}>
                    Nhập / điều chỉnh
                  </Button>
                </div>
```

bằng:

```tsx
            <article
              key={item.productId}
              className={`card smenu__card${
                item.available && item.onChainMenu && (item.blockedBy ?? []).length === 0 ? '' : ' smenu__card--out'
              }`}
            >
              <div className="card__body">
                <h3 className="smenu__name">{item.productName}</h3>
                <span className="smenu__price">{formatCurrency(item.price)}</span>
                {!item.onChainMenu && <Badge tone="neutral">Đã ngừng bán toàn chuỗi</Badge>}
                {item.productType === 'COMBO' ? (
                  // Combo không có tồn riêng (spec §4.1): chỉ bật/tắt; thiếu thành phần thì báo lý do
                  <div className="smenu__stock-row">
                    <Badge tone="info">Combo</Badge>
                    {(item.blockedBy ?? []).length > 0 && (
                      <span className="smenu__stock smenu__stock--low">
                        Tạm hết do: {(item.blockedBy ?? []).join(', ')}
                      </span>
                    )}
                  </div>
                ) : (
                  <div className="smenu__stock-row">
                    <span className={`smenu__stock${item.lowStock ? ' smenu__stock--low' : ''}`}>
                      {item.stockQuantity == null ? 'Chưa quản tồn' : `Tồn kho: ${item.stockQuantity}`}
                    </span>
                    <Button size="sm" variant="ghost" onClick={() => setStockItem(item)}>
                      Nhập / điều chỉnh
                    </Button>
                  </div>
                )}
```

Trong `onAdjusted` của `StockAdjustModal`, thay:

```tsx
          onAdjusted={(updated) => {
            replace(updated);
            setStockItem(null);
          }}
```

bằng:

```tsx
          onAdjusted={(updated) => {
            replace(updated);
            setStockItem(null);
            // Tồn món lẻ đổi → "Tạm hết do" của combo có thể đổi theo
            if (items.some((i) => i.productType === 'COMBO')) {
              void load();
            }
          }}
```

- [ ] **Step 2: `pages/admin/AdminReportsPage.tsx`**

Đổi import type: `import type { PriceSavings, ReportType, TopProduct } from '../../types/admin';`.

Ngay sau `  const [exportingType, setExportingType] = useState<ReportType | null>(null);` thêm:

```tsx
  const [savings, setSavings] = useState<PriceSavings | null>(null);
```

Ngay trước `  const handleExport = async (type: ReportType) => {` thêm:

```tsx
  useEffect(() => {
    let cancelled = false;
    adminReportsApi
      .getPriceSavings({
        fromDate: fromDate || undefined,
        toDate: toDate || undefined,
        storeId: storeId ?? undefined,
      })
      .then((data) => {
        if (!cancelled) setSavings(data);
      })
      .catch(() => {
        if (!cancelled) setSavings(null);
      });
    return () => {
      cancelled = true;
    };
  }, [fromDate, toDate, storeId, reloadKey]);

```

Thay:

```tsx
      <section className="card">
        <div className="card__head">
          <h2 className="card__title">Món bán chạy</h2>
```

bằng:

```tsx
      <section className="card">
        <div className="card__head">
          <h2 className="card__title">Tiền ưu đãi từ giá KM và combo</h2>
          <span className="arpt__summary">Đơn đã giao trong khoảng ngày đang lọc</span>
        </div>
        <div className="card__body">
          {savings == null ? (
            <Skeleton variant="row" count={1} />
          ) : (
            <p className="arpt__summary">
              <strong>{formatCurrency(savings.amount)}</strong> trên {savings.orderCount} đơn
            </p>
          )}
        </div>
      </section>

      <section className="card">
        <div className="card__head">
          <h2 className="card__title">Món bán chạy</h2>
```

- [ ] **Step 3: `pages/manager/ManagerReportsPage.tsx`**

Đổi import type: `import type { DashboardMetrics, PriceSavings, TopProduct } from '../../types/admin';`. Thêm state `const [savings, setSavings] = useState<PriceSavings | null>(null);` ngay sau state `top`.

Thay:

```tsx
    Promise.all([managerApi.metrics(), managerApi.topProducts({ limit: 10 })])
      .then(([m, t]) => {
        if (!alive) return;
        setMetrics(m);
        setTop(t);
      })
```

bằng:

```tsx
    Promise.all([managerApi.metrics(), managerApi.topProducts({ limit: 10 }), managerApi.priceSavings()])
      .then(([m, t, s]) => {
        if (!alive) return;
        setMetrics(m);
        setTop(t);
        setSavings(s);
      })
```

Thay:

```tsx
    { label: 'Doanh thu tích luỹ', value: formatCurrency(metrics.totalRevenue) },
  ];
```

bằng:

```tsx
    { label: 'Doanh thu tích luỹ', value: formatCurrency(metrics.totalRevenue) },
    {
      label: 'Tiền ưu đãi từ giá KM và combo (30 ngày)',
      value: savings ? `${formatCurrency(savings.amount)} · ${savings.orderCount} đơn` : '—',
    },
  ];
```

- [ ] **Step 4: Kiểm**

Run (`frontend`): `npx tsc -b` → 0 lỗi; `npm run build` → `✓ built`; `npx oxlint src` → không thêm cảnh báo so với trước Task 11.

- [ ] **Step 5: Commit (người dùng tự chạy)**

```
git add frontend/src/pages/staff/StoreStockPage.tsx frontend/src/pages/admin/AdminReportsPage.tsx frontend/src/pages/manager/ManagerReportsPage.tsx
git commit -m "feat(frontend): tình trạng combo theo cơ sở và ô tiền ưu đãi ở báo cáo"
```

---

## Task 16: Kiểm chứng cuối — migration trên bản sao DB, toàn bộ test, smoke HTTP, giao diện, tài liệu

**Files:**
- Modify: `SETUP.md` (thêm mục "Giá khuyến mãi + Combo — khi pull code"), `docs/superpowers/specs/2026-10-02-combo-sale-design.md` (trạng thái → "Đã triển khai")

- [ ] **Step 1: Chạy V14 trên bản sao DB (MariaDB 10.4 XAMPP)**

```
mysqldump -h 127.0.0.1 -P 3307 -u root -p banhmyking > backup_truoc_V14.sql
mysql -h 127.0.0.1 -P 3307 -u root -p -e "DROP DATABASE IF EXISTS banhmyking_copy; CREATE DATABASE banhmyking_copy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -h 127.0.0.1 -P 3307 -u root -p banhmyking_copy < backup_truoc_V14.sql
```

Chạy app trỏ vào bản sao: `./mvnw -B spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:mysql://localhost:3307/banhmyking_copy?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true"`
Expected: log `Successfully applied 1 migration ... now at version v14`, app khởi động (Hibernate validate xanh). Kiểm:

```sql
SELECT product_type, COUNT(*) FROM products GROUP BY product_type;          -- chỉ 'SINGLE'
SELECT COUNT(*) FROM products WHERE sale_price IS NOT NULL;                 -- 0
SELECT COUNT(*) FROM order_items WHERE original_unit_price IS NOT NULL;     -- 0 (đơn cũ không backfill)
SELECT COUNT(*) FROM combo_items;                                           -- 0
SHOW CREATE TABLE order_item_components;                                    -- FK ON DELETE CASCADE / SET NULL
```

Dừng app, xoá bản sao: `mysql -h 127.0.0.1 -P 3307 -u root -p -e "DROP DATABASE banhmyking_copy;"`.

- [ ] **Step 2: Toàn bộ test + build**

Run: `./mvnw -B clean test` → BUILD SUCCESS.
Run (`frontend`): `npx tsc -b` → 0 lỗi; `npm run build` → `✓ built`; `npx oxlint src` → không thêm cảnh báo.

- [ ] **Step 3: Smoke HTTP (DB dev, profile dev có seed)**

Viết script Python tạm trong scratchpad (không đưa vào repo), đăng nhập `admin@gmail.com`, `customer@gmail.com`, `manager@gmail.com`, `staff@gmail.com` rồi kiểm:
1. ADMIN `PUT /api/v1/catalog/products/{bánh mì A}` với `salePrice` = giá − 5.000, `saleStartsAt` = null → `GET /catalog/products?onSale=true` có A, `effectivePrice` = giá KM, `compareAtPrice` = giá gốc, `discountPercent` > 0. `salePrice ≥ price` → 400 "Giá khuyến mãi phải nhỏ hơn giá gốc".
2. ADMIN `POST /catalog/products` combo `{productType:'COMBO', price, comboItems:[A×1, B×1]}` với `price` < tổng giá lẻ → 201; `price` ≥ tổng → 400 "Giá combo phải thấp hơn tổng giá lẻ…"; `GET /catalog/products?type=COMBO` có combo kèm `comboItems`.
3. ADMIN `DELETE /catalog/products/{A}` → 400 "Món đang nằm trong combo: …".
4. CUSTOMER thêm combo × 2 + A × 1 vào giỏ → `GET /cart` có `savingsAmount` > 0, dòng combo có `originalUnitPrice` = tổng giá lẻ; `GET /delivery/quote?...` có `recommendedStoreId`.
5. STAFF/MANAGER cơ sở được đề xuất: `POST /store-inventory/{s}/products/{B}/stock {changeQty: 1}` (tồn B = 1, cần 2) → báo giá mới có `reasons` chứa `ITEM_UNAVAILABLE` và `unavailableItems` dạng "<combo> (hết B)"; nhập thêm tồn B rồi đặt đơn → 201; `GET /orders/{mã}` có `components`, `originalUnitPrice`, `savingsAmount`.
6. STAFF xác nhận đơn → sổ kho `GET /store-inventory/{s}/products/{B}/movements` có dòng ORDER của B (không có dòng cho combo); huỷ đơn → có dòng RESTORE. `POST /store-inventory/{s}/products/{combo}/stock` → 400 "Combo không có tồn kho riêng…".
7. Giao xong một đơn có ưu đãi → `GET /admin/reports/price-savings?storeId={s}` và `manager` `GET /manager/reports/price-savings` trả `amount` > 0, `orderCount` ≥ 1; `staff` gọi `/manager/reports/price-savings` → 403.
Dọn dữ liệu test (xoá combo test, bỏ KM món A, huỷ đơn test chưa giao).

- [ ] **Step 4: Danh sách bấm thử giao diện (chụp bằng Chrome như A, gửi người dùng)**

- `/menu`: thẻ món KM có nhãn −x%, giá gạch; chip **Đang khuyến mãi** lọc đúng; thẻ combo có nhãn **Combo** + "Gồm: …"; combo có thành phần bị tắt hiện "Hết món".
- `/products/{combo}`: gạch giá, danh sách thành phần có link, không có khối topping; món KM có hạn hiện "KM đến dd/MM HH:mm".
- `/cart`, `/checkout`: đơn giá có gạch giá, dòng **Bạn tiết kiệm được**; nhập mã giảm giá % tính trên tạm tính đã giảm.
- `/orders`, `/orders/{mã}`: gạch giá + thành phần combo theo snapshot; dòng "Bạn đã tiết kiệm…".
- `/admin/menu`: tab **Món lẻ / Combo**; form món có 3 ô KM; nhãn Đang KM / Sắp KM / KM đã hết; form combo hiện "Tổng giá lẻ X → Giá combo Y (tiết kiệm Z%)", nút lưu bị khoá khi Y ≥ X.
- `/staff/orders` và `/shipper`: dòng combo có "Gồm: …".
- `/staff/menu`: combo chỉ có nút bật/tắt, hiện "Tạm hết do: …" khi một thành phần hết.
- `/admin/reports`, `/staff/reports` (MANAGER): ô **Tiền ưu đãi từ giá KM và combo**.

- [ ] **Step 5: `SETUP.md` — thêm mục (ngay trước `## LỖI THÌ CHỊU. HỎI CHAT.`)**

```markdown
## Giá khuyến mãi + Combo (từ nhánh feature/combo-sale)

1. **Sao lưu DB trước khi pull**: `mysqldump -h 127.0.0.1 -P 3307 -u root -p banhmyking > backup_truoc_V14.sql`
2. `git pull`, chạy backend → log `now at version v14` (chỉ thêm cột/bảng, món cũ thành "món lẻ", chưa có KM).
3. `cd frontend && npm install && npm run dev`.
4. ADMIN → **Thực đơn** → tab **Món lẻ**: đặt *Giá khuyến mãi* (+ *Bắt đầu*/*Kết thúc* nếu cần, giờ Việt Nam).
5. ADMIN → **Thực đơn** → tab **Combo**: thêm combo từ các món lẻ; giá combo phải thấp hơn tổng giá lẻ.
6. Món đang nằm trong combo không xoá được — sửa/xoá combo trước. Combo không có tồn riêng: nhập tồn cho từng món lẻ ở **Tình trạng món**.
```

Sửa dòng `- **Trạng thái:** Chờ duyệt` trong spec thành `- **Trạng thái:** Đã triển khai`.

- [ ] **Step 6: Commit (người dùng tự chạy)**

```
git add SETUP.md docs/superpowers/specs/2026-10-02-combo-sale-design.md docs/superpowers/plans/2026-10-02-combo-sale.md
git commit -m "docs(combo): hướng dẫn team khi pull giá khuyến mãi và combo, cập nhật trạng thái thiết kế"
```

---

## Self-review (đã chạy khi lập kế hoạch)

**Độ phủ spec:**

| Spec | Task |
|---|---|
| §2.1 cột products, §2.2 combo_items, §2.3 original_unit_price, §2.4 order_item_components (V14) | 1 |
| §3 ProductPricing (isSaleActive, effectivePrice, originalPrice, compareAtPrice, discountPercent, đơn giá dòng, tiết kiệm) | 2 |
| §3 chốt giá lúc tạo đơn; B3 mã giảm giá trên tạm tính đã giảm; bỏ công thức lặp ở PriceCalculator | 3 |
| §3 ràng buộc khi lưu (KM, combo, không đổi loại) | 7 |
| §4.1–4.2 combo bán được tại cơ sở, nhu cầu gộp, trừ/hoàn kho theo món lẻ, API tồn từ chối combo | 4, 5 |
| §4.3 available mức chuỗi; thêm giỏ/tạo đơn từ chối combo không còn bán | 4, 6, 8, 9 |
| §5 snapshot đơn, chặn xoá món trong combo, xoá combo giữ combo_items | 7, 9 |
| §6.1 ProductResponse/Request + onSale/type | 6, 7 |
| §6.2 giỏ (originalUnitPrice, productType, comboItems, savingsAmount) | 8 |
| §6.3 đơn (originalUnitPrice, components, savingsAmount) | 9 |
| §6.4 StoreStockResponse productType/blockedBy, nhập tồn combo → 400 | 5 |
| §6.5 price-savings ADMIN + MANAGER | 10 |
| §7 giao diện admin/khách/nhân viên/quản lý | 11–15 |
| §8 kiểm thử (ProductPricing, ComboExpander, Inventory, Catalog, Order, báo cáo, migration, smoke + Chrome) | 1–10, 16 |
| §9 hiệu năng (`@BatchSize`), lệch giờ (server tính `effectivePrice`), đơn cũ NULL | 1, 6, 9 |

**Nhất quán tên/kiểu:** `ProductPricing.{now, isSaleActive, effectivePrice, originalPrice, compareAtPrice, discountPercent, optionsExtra, unitPrice, lineTotal, lineSavings}`; `ComboExpander.{expand, isChainAvailable}`; `PriceCalculator.{calculateSubtotal(Cart[, LocalDateTime]), calculate(Cart, Promotion[, BigDecimal[, LocalDateTime]])}`; `ComboItemResponse.listOf(Product)`; `ComboItemRepository.findActiveComboNamesContaining(Long)`; `OrderItemRepository.sumPriceSavings(LocalDateTime, LocalDateTime, Long)`; `AdminReportService.getPriceSavings(LocalDate, LocalDate, Long)`; TS `priceNow / cartUnitCompareAt / cartSavings / orderUnitCompareAt / orderComponentsText / saleState / formatSaleEnd / toDateTimeLocal`, `CartContextType.savingsAmount` — dùng thống nhất ở mọi task.
