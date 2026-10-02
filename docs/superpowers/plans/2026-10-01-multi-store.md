# Chuỗi nhiều cơ sở (dự án con A) — Kế hoạch triển khai

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Biến ứng dụng một quán thành chuỗi nhiều cơ sở: đơn tự gán cơ sở gần nhất (khách đổi được), hết món/tồn kho theo cơ sở, nhân viên/shipper/quản lý theo cơ sở, báo cáo lọc theo cơ sở.

**Architecture:** Bảng `stores` + `store_products`, cột `store_id` trên `users`/`orders`/`inventory_movements`. Một `StoreAccessGuard` dùng chung quyết định phạm vi cơ sở của người thao tác; `StoreSelectionService` chấm điều kiện từng cơ sở cho báo giá và tạo đơn. Migration chia 3 bước (V11 tạo + chép dữ liệu, V12 bỏ tồn kho cũ, V13 siết NOT NULL + bỏ cài đặt vị trí quán) để ứng dụng chạy được sau mỗi task.

**Tech Stack:** Java 17, Spring Boot 4.1.1, Hibernate 7 (`ddl-auto=validate`), Flyway, MySQL 8 / MariaDB 10.4, JUnit 5 + Mockito + AssertJ; React 19 + TypeScript + Vite, react-leaflet 5.

**Spec:** `docs/superpowers/specs/2026-10-01-multi-store-design.md`

## Global Constraints

- **Agent KHÔNG commit, KHÔNG push.** Mỗi bước "Commit" là gợi ý lệnh để **người dùng** tự chạy; message tiếng Việt, không thêm dòng Co-Authored-By.
- Làm trên nhánh `feature/multi-store`.
- `spring.jpa.hibernate.ddl-auto=validate`: entity phải khớp migration (tên cột, kiểu, có/không có cột).
- SQL migration phải chạy trên **cả MySQL 8 và MariaDB 10.4**: bỏ ràng buộc CHECK bằng `DROP CONSTRAINT` (không dùng `DROP CHECK`); không dùng cú pháp riêng của một bên.
- **Không sửa** V1–V10. Migration mới của kế hoạch: V11 (Task 1), V12 (Task 3), V13 (Task 5).
- Giữ kiểu xuống dòng của file đang sửa (repo dùng CRLF trên Windows). File mới viết bằng công cụ Write được chấp nhận; `core.autocrlf=true` sẽ chuẩn hoá khi commit.
- Giờ hệ thống: `Asia/Ho_Chi_Minh`. Tiền: `DECIMAL(12,2)` / `BigDecimal`. Khoảng cách: `DECIMAL(6,2)` km.
- Hằng số nghiệp vụ mặc định (spec §2.1): mở 06:30, đóng 22:00, bán kính giao 5 km, freeship 3 km, đơn tối thiểu 0 (cơ sở tạo từ dữ liệu cũ) — admin tự đặt 50 000.
- Hệ số quãng đường: `delivery.road-factor` = 1.3 (đã có trong code).
- Không dùng logo, ảnh, nội dung, địa chỉ thật của BAMI KING®; dữ liệu demo dùng tên chung chung.
- Lệnh test backend (Git Bash, ở thư mục gốc): `./mvnw -B test -Dtest=<TênClass>`; toàn bộ: `./mvnw -B test` (cần MariaDB XAMPP cổng 3307 đang chạy cho test tích hợp).
- Lệnh frontend (thư mục `frontend`): `npx tsc -b`, `npm run build`, `npx oxlint src`.
- Phân quyền: CUSTOMER, STAFF, SHIPPER, **MANAGER** (mới), ADMIN. STAFF/SHIPPER/MANAGER bắt buộc thuộc đúng 1 cơ sở; CUSTOMER/ADMIN không thuộc cơ sở nào.
- Ngoài phạm vi (KHÔNG làm): hẹn giờ giao, PICKUP, giờ mở theo thứ, giá theo cơ sở, SSE, chuyển shipper giữa cơ sở.

## Bản đồ file

**Backend — tạo mới** (gốc `src/main/java/com/banhmyking/banhmyking/`):

| File | Trách nhiệm |
|---|---|
| `entity/Store.java`, `entity/StoreProduct.java`, `entity/StoreProductId.java` | Entity cơ sở và tình trạng món theo cơ sở |
| `repository/StoreRepository.java`, `repository/StoreProductRepository.java` | Truy vấn cơ sở; trừ/cộng tồn nguyên tử theo `(store, product)` |
| `config/TimeConfig.java` | Bean `Clock` giờ Việt Nam (test được giờ mở cửa) |
| `security/StoreAccessGuard.java` | Phạm vi cơ sở theo vai trò; chặn truy cập đơn/cơ sở khác (404) |
| `service/StoreService.java` + `service/impl/StoreServiceImpl.java` | CRUD cơ sở, danh sách public, tạm ngưng nhận đơn |
| `service/StoreHours.java` | Tính "đang mở cửa" |
| `service/StoreSelectionService.java` | Chấm điều kiện cơ sở, đề xuất, bắt buộc hợp lệ khi tạo đơn |
| `dto/store/*` | `StoreRequest`, `StoreResponse`, `PublicStoreResponse`, `AcceptingOrdersRequest`, `StoreStockResponse`, `AvailabilityRequest`, `DeliveryQuoteResponse`, `StoreQuoteOption`, `TransferStoreRequest`, `StoreRevenueResponse` |
| `controller/StoreController.java` (public), `controller/AdminStoreController.java`, `controller/ManagerController.java`, `controller/StoreInventoryController.java` | API mới |
| `src/main/resources/db/migration/V11__multi_store.sql`, `V12__drop_product_stock.sql`, `V13__store_not_null_and_cleanup.sql` | Migration |

**Backend — xoá:** `service/StoreDistanceService.java`, test `StoreDistanceServiceTest.java` (Task 5).

**Frontend — tạo mới** (gốc `frontend/src/`): `types/store.ts`, `api/storeApi.ts`, `api/storeInventoryApi.ts`, `api/managerApi.ts`, `components/store/StoresMap.tsx`, `components/store/storeLabels.ts`, `components/store/StoreScopeSelect.tsx`, `components/staff/StockAdjustModal.tsx`, `context/StoreScopeProvider.tsx` + `context/storeScopeContextDef.ts` + `context/useStoreScope.ts`, `pages/StoresPage.tsx`, `pages/admin/AdminStoresPage.tsx`, `pages/staff/StoreStockPage.tsx`, `pages/manager/ManagerReportsPage.tsx`, `pages/manager/ManagerStaffPage.tsx`, `styles/components/stores.css`.

---

## Task 1: Schema chuỗi cơ sở (V11) + entity + repository

**Files:**
- Create: `src/main/resources/db/migration/V11__multi_store.sql`
- Create: `entity/Store.java`, `entity/StoreProductId.java`, `entity/StoreProduct.java`
- Create: `repository/StoreRepository.java`, `repository/StoreProductRepository.java`
- Modify: `enums/RoleName.java`, `entity/User.java`, `entity/Order.java`, `entity/InventoryMovement.java`
- Test: `src/test/java/com/banhmyking/banhmyking/repository/StoreSchemaIntegrationTest.java`

**Interfaces:**
- Produces: entity `Store` (getters/setters Lombok: `code, name, address, phone, latitude, longitude, openTime, closeTime, acceptingOrders, deliveryRadiusKm, freeShipRadiusKm, minOrderAmount, active, deleted`, method `boolean hasLocation()`); `StoreProductId(Long storeId, Long productId)`; `StoreProduct` (`id, store, product, available, stockQuantity, lowStockThreshold`, method `boolean isLowStock()`); `RoleName.MANAGER`; `User.getStore()/setStore(Store)`; `Order.getStore()/setStore(Store)`; `InventoryMovement.getStore()/setStore(Store)`.
- `StoreRepository`: `List<Store> findByDeletedFalseOrderByCodeAsc()`, `List<Store> findByActiveTrueAndDeletedFalseOrderByCodeAsc()`, `Optional<Store> findByIdAndDeletedFalse(Long id)`, `Optional<Store> findByCodeAndDeletedFalse(String code)`, `boolean existsByCodeAndDeletedFalse(String code)`.
- `StoreProductRepository`: `List<StoreProduct> findByIdStoreId(Long storeId)`, `List<StoreProduct> findByIdStoreIdAndIdProductIdIn(Long storeId, Collection<Long> productIds)`, `Optional<StoreProduct> findByIdStoreIdAndIdProductId(Long storeId, Long productId)` (thao tác tồn nguyên tử thêm ở Task 3).
- Ở task này `orders.store_id` và `inventory_movements.store_id` còn **NULL được** (siết NOT NULL ở Task 3/5) để code tạo đơn hiện tại vẫn chạy.

- [ ] **Step 1: Viết test tích hợp (đỏ)**

```java
package com.banhmyking.banhmyking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.StoreProduct;
import com.banhmyking.banhmyking.entity.StoreProductId;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Chạy trên DB dev thật (giống các test repository khác) — mọi thay đổi rollback sau test. */
@SpringBootTest
@Transactional
class StoreSchemaIntegrationTest {

    @Autowired private StoreRepository storeRepository;
    @Autowired private StoreProductRepository storeProductRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesStoreOneAndBackfillsExistingOrders() {
        assertThat(storeRepository.findByCodeAndDeletedFalse("CS01")).isPresent();
        Integer ordersWithoutStore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE store_id IS NULL", Integer.class);
        assertThat(ordersWithoutStore).isZero();
        Integer staffWithoutStore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role IN ('STAFF','SHIPPER') AND store_id IS NULL", Integer.class);
        assertThat(staffWithoutStore).isZero();
    }

    @Test
    void savesStoreAndPerStoreProductRow() {
        Store store = new Store();
        store.setCode("CSTEST");
        store.setName("Cơ sở test");
        store.setAddress("1 Đường Test, Hà Nội");
        store.setLatitude(new BigDecimal("21.028700"));
        store.setLongitude(new BigDecimal("105.852400"));
        store.setOpenTime(LocalTime.of(6, 30));
        store.setCloseTime(LocalTime.of(22, 0));
        store = storeRepository.save(store);

        Product product = productRepository.findAll().get(0);
        StoreProduct row = new StoreProduct();
        row.setId(new StoreProductId(store.getId(), product.getId()));
        row.setStore(store);
        row.setProduct(product);
        row.setAvailable(false);
        row.setStockQuantity(7);
        storeProductRepository.saveAndFlush(row);

        List<StoreProduct> rows = storeProductRepository.findByIdStoreId(store.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).isAvailable()).isFalse();
        assertThat(rows.get(0).getStockQuantity()).isEqualTo(7);
        assertThat(store.hasLocation()).isTrue();
        assertThat(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc())
                .extracting(Store::getCode).contains("CSTEST");
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B test -Dtest=StoreSchemaIntegrationTest`
Expected: FAIL biên dịch — `cannot find symbol: class Store`.

- [ ] **Step 3: Viết migration V11**

`src/main/resources/db/migration/V11__multi_store.sql`:

```sql
-- V11: chuỗi nhiều cơ sở (spec docs/superpowers/specs/2026-10-01-multi-store-design.md §2, §6)
-- Tạo bảng + chép dữ liệu cũ về "Cơ sở 1". Cột tồn kho cũ ở products bỏ ở V12,
-- orders.store_id / inventory_movements.store_id siết NOT NULL ở V12/V13 (sau khi code luôn ghi).

CREATE TABLE stores (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(20)    NOT NULL,
    name                VARCHAR(100)   NOT NULL,
    address             VARCHAR(500)   NOT NULL,
    phone               VARCHAR(20)    NULL,
    latitude            DECIMAL(9, 6)  NULL,
    longitude           DECIMAL(9, 6)  NULL,
    open_time           TIME           NOT NULL DEFAULT '06:30:00',
    close_time          TIME           NOT NULL DEFAULT '22:00:00',
    accepting_orders    BOOLEAN        NOT NULL DEFAULT TRUE,
    delivery_radius_km  DECIMAL(5, 2)  NOT NULL DEFAULT 5.00,
    free_ship_radius_km DECIMAL(5, 2)  NOT NULL DEFAULT 3.00,
    min_order_amount    DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    is_active           BOOLEAN        NOT NULL DEFAULT TRUE,
    is_deleted          BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NULL,
    CONSTRAINT uk_stores_code UNIQUE (code),
    CONSTRAINT chk_stores_radius CHECK (delivery_radius_km > 0 AND free_ship_radius_km >= 0),
    CONSTRAINT chk_stores_min_order CHECK (min_order_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE store_products (
    store_id            BIGINT      NOT NULL,
    product_id          BIGINT      NOT NULL,
    is_available        BOOLEAN     NOT NULL DEFAULT TRUE,
    stock_quantity      INT         NULL,
    low_stock_threshold INT         NOT NULL DEFAULT 5,
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NULL,
    PRIMARY KEY (store_id, product_id),
    CONSTRAINT fk_store_products_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_store_products_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_store_products_stock CHECK (stock_quantity IS NULL OR stock_quantity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Cơ sở 1 từ cấu hình quán hiện tại (V10 + Cài đặt website). Rỗng → NULL / mặc định.
INSERT INTO stores (code, name, address, latitude, longitude, delivery_radius_km, created_at)
SELECT 'CS01',
       'Cơ sở 1',
       COALESCE(NULLIF(TRIM((SELECT setting_value FROM site_settings WHERE setting_key = 'contactAddress')), ''),
                'Chưa cập nhật địa chỉ'),
       CAST(NULLIF(TRIM((SELECT setting_value FROM site_settings WHERE setting_key = 'storeLatitude')), '')
            AS DECIMAL(9, 6)),
       CAST(NULLIF(TRIM((SELECT setting_value FROM site_settings WHERE setting_key = 'storeLongitude')), '')
            AS DECIMAL(9, 6)),
       COALESCE(CAST(NULLIF(TRIM((SELECT setting_value FROM site_settings
                                  WHERE setting_key = 'deliveryMaxRadiusKm')), '') AS DECIMAL(5, 2)), 5.00),
       NOW(6);

-- Đơn hàng
ALTER TABLE orders ADD COLUMN store_id BIGINT NULL AFTER user_id;
UPDATE orders SET store_id = (SELECT id FROM stores WHERE code = 'CS01');
ALTER TABLE orders
    ADD CONSTRAINT fk_orders_store FOREIGN KEY (store_id) REFERENCES stores (id),
    ADD INDEX idx_orders_store_status_created (store_id, status, created_at);

-- Nhân sự theo cơ sở
ALTER TABLE users ADD COLUMN store_id BIGINT NULL AFTER role;
UPDATE users SET store_id = (SELECT id FROM stores WHERE code = 'CS01') WHERE role IN ('STAFF', 'SHIPPER');
ALTER TABLE users ADD CONSTRAINT fk_users_store FOREIGN KEY (store_id) REFERENCES stores (id);

-- Tồn kho đang quản → store_products của Cơ sở 1
INSERT INTO store_products (store_id, product_id, is_available, stock_quantity, low_stock_threshold, created_at)
SELECT s.id, p.id, TRUE, p.stock_quantity, p.low_stock_threshold, NOW(6)
FROM products p CROSS JOIN stores s
WHERE s.code = 'CS01' AND p.stock_quantity IS NOT NULL;

-- Sổ kho
ALTER TABLE inventory_movements ADD COLUMN store_id BIGINT NULL AFTER product_id;
UPDATE inventory_movements SET store_id = (SELECT id FROM stores WHERE code = 'CS01');
ALTER TABLE inventory_movements
    ADD CONSTRAINT fk_inv_mov_store FOREIGN KEY (store_id) REFERENCES stores (id),
    ADD INDEX idx_inv_mov_store_product_created (store_id, product_id, created_at);
```

- [ ] **Step 4: Thêm `MANAGER` vào `enums/RoleName.java`**

Thêm hằng `MANAGER` ngay sau `SHIPPER` (giữ nguyên các hằng khác và comment hiện có):

```java
    SHIPPER,
    /** Quản lý một cơ sở: vận hành + báo cáo + xem nhân viên của cơ sở mình. */
    MANAGER,
```

- [ ] **Step 5: Tạo `entity/Store.java`**

```java
package com.banhmyking.banhmyking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/** Một cơ sở trong chuỗi (spec §2.1). */
@Getter
@Setter
@Entity
@Table(name = "stores")
public class Store extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(length = 20)
    private String phone;

    /** NULL = chưa ghim — cơ sở phục vụ mọi địa chỉ, phí theo khu vực (spec §3.4). */
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime = LocalTime.of(6, 30);

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime = LocalTime.of(22, 0);

    @Column(name = "accepting_orders", nullable = false)
    private boolean acceptingOrders = true;

    @Column(name = "delivery_radius_km", nullable = false, precision = 5, scale = 2)
    private BigDecimal deliveryRadiusKm = new BigDecimal("5.00");

    @Column(name = "free_ship_radius_km", nullable = false, precision = 5, scale = 2)
    private BigDecimal freeShipRadiusKm = new BigDecimal("3.00");

    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
```

- [ ] **Step 6: Tạo `entity/StoreProductId.java` và `entity/StoreProduct.java`**

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
public class StoreProductId implements Serializable {

    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "product_id")
    private Long productId;
}
```

```java
package com.banhmyking.banhmyking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Tình trạng một món tại một cơ sở (spec §2.2). Không có dòng = đang bán, không quản tồn.
 * Khoá ghép nên không kế thừa BaseEntity (BaseEntity có cột id tự tăng).
 */
@Getter
@Setter
@Entity
@Table(name = "store_products")
@EntityListeners(AuditingEntityListener.class)
public class StoreProduct {

    @EmbeddedId
    private StoreProductId id;

    @MapsId("storeId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    /** NULL = không quản tồn tại cơ sở này. */
    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 5;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isLowStock() {
        return stockQuantity != null && stockQuantity <= lowStockThreshold;
    }
}
```

- [ ] **Step 7: Tạo 2 repository**

```java
package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByDeletedFalseOrderByCodeAsc();

    List<Store> findByActiveTrueAndDeletedFalseOrderByCodeAsc();

    Optional<Store> findByIdAndDeletedFalse(Long id);

    Optional<Store> findByCodeAndDeletedFalse(String code);

    boolean existsByCodeAndDeletedFalse(String code);
}
```

```java
package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.StoreProduct;
import com.banhmyking.banhmyking.entity.StoreProductId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreProductRepository extends JpaRepository<StoreProduct, StoreProductId> {

    List<StoreProduct> findByIdStoreId(Long storeId);

    List<StoreProduct> findByIdStoreIdAndIdProductIdIn(Long storeId, Collection<Long> productIds);

    Optional<StoreProduct> findByIdStoreIdAndIdProductId(Long storeId, Long productId);
}
```

- [ ] **Step 8: Thêm quan hệ `store` vào User, Order, InventoryMovement**

`entity/User.java` — ngay sau trường `role`:

```java
    /** Cơ sở làm việc — bắt buộc với STAFF/SHIPPER/MANAGER, NULL với CUSTOMER/ADMIN (kiểm ở service). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;
```

`entity/Order.java` — ngay sau trường `user`:

```java
    /** Cơ sở phục vụ đơn. NOT NULL ở DB từ V13 (Task 5). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;
```

`entity/InventoryMovement.java` — ngay sau trường `product`:

```java
    /** Cơ sở có biến động tồn. NOT NULL ở DB từ V12 (Task 3). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;
```

Bổ sung import `jakarta.persistence.FetchType/JoinColumn/ManyToOne` nếu file chưa có.

- [ ] **Step 9: Chạy test, xác nhận xanh**

Run: `./mvnw -B test -Dtest=StoreSchemaIntegrationTest`
Expected: PASS (2 test). Log có `Migrating schema ... to version "11 - multi store"`.

- [ ] **Step 10: Chạy toàn bộ test**

Run: `./mvnw -B test`
Expected: BUILD SUCCESS (vai trò mới không làm gãy test cũ; nếu `AdminUsersPage`/switch nào báo thiếu case thì đó là frontend — xử lý ở Task 15).

- [ ] **Step 11: Commit (người dùng tự chạy)**

```
git add src/main/resources/db/migration/V11__multi_store.sql src/main/java/com/banhmyking/banhmyking/enums/RoleName.java src/main/java/com/banhmyking/banhmyking/entity/Store.java src/main/java/com/banhmyking/banhmyking/entity/StoreProductId.java src/main/java/com/banhmyking/banhmyking/entity/StoreProduct.java src/main/java/com/banhmyking/banhmyking/entity/User.java src/main/java/com/banhmyking/banhmyking/entity/Order.java src/main/java/com/banhmyking/banhmyking/entity/InventoryMovement.java src/main/java/com/banhmyking/banhmyking/repository/StoreRepository.java src/main/java/com/banhmyking/banhmyking/repository/StoreProductRepository.java src/test/java/com/banhmyking/banhmyking/repository/StoreSchemaIntegrationTest.java
git commit -m "feat(co-so): V11 bảng stores, store_products, store_id cho đơn/nhân sự/sổ kho, vai trò MANAGER"
```

---

## Task 2: StoreAccessGuard — phạm vi cơ sở theo vai trò

**Files:**
- Create: `security/StoreAccessGuard.java`
- Test: `src/test/java/com/banhmyking/banhmyking/security/StoreAccessGuardTest.java`

**Interfaces:**
- Consumes: `User.getStore()`, `Order.getStore()`, `RoleName.MANAGER` (Task 1).
- Produces (bean `@Component StoreAccessGuard`, không phụ thuộc gì):
  - `boolean isOperator(User actor)` — STAFF, MANAGER, ADMIN.
  - `void requireOperator(User actor)` — không phải operator → `BusinessException(FORBIDDEN, "Chỉ nhân viên, quản lý cơ sở hoặc quản trị viên mới có quyền thực hiện")`.
  - `Long scopedStoreId(User actor)` — ADMIN → `null` (không giới hạn); STAFF/MANAGER/SHIPPER → id cơ sở; thiếu cơ sở → `BusinessException(FORBIDDEN, "Tài khoản chưa được gán cơ sở")`; CUSTOMER → `BusinessException(FORBIDDEN, ...)`.
  - `Long resolveStoreFilter(User actor, Long requestedStoreId)` — ADMIN → `requestedStoreId` (null = tất cả); vai trò khác → cơ sở của mình; nếu `requestedStoreId` khác cơ sở mình → `ResourceNotFoundException("Không tìm thấy cơ sở với ID: " + id)`.
  - `void requireStoreAccess(User actor, Long storeId)` — ADMIN mọi cơ sở; STAFF/MANAGER đúng cơ sở mình; còn lại → `ResourceNotFoundException`.
  - `void requireOrderAccess(User actor, Order order)` — chỉ áp cho STAFF/MANAGER: đơn khác cơ sở → `ResourceNotFoundException(NotFoundMessages.orderByCode(order.getOrderCode()))`. ADMIN/CUSTOMER/SHIPPER: không làm gì (kiểm tra chủ đơn/shipper hiện có giữ nguyên).
  - `boolean sameStore(User a, Store store)` — tiện ích so sánh id.

- [ ] **Step 1: Viết test (đỏ)**

```java
package com.banhmyking.banhmyking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

class StoreAccessGuardTest {

    private final StoreAccessGuard guard = new StoreAccessGuard();

    private static Store store(long id) {
        Store s = new Store();
        s.setId(id);
        return s;
    }

    private static User user(RoleName role, Store store) {
        User u = new User();
        u.setId(99L);
        u.setRole(role);
        u.setStore(store);
        return u;
    }

    private static Order order(Store store) {
        Order o = new Order();
        o.setOrderCode("BMK-TEST");
        o.setStore(store);
        return o;
    }

    @Test
    void adminIsUnscopedAndMayFilterAnyStore() {
        User admin = user(RoleName.ADMIN, null);
        assertThat(guard.scopedStoreId(admin)).isNull();
        assertThat(guard.resolveStoreFilter(admin, null)).isNull();
        assertThat(guard.resolveStoreFilter(admin, 2L)).isEqualTo(2L);
        assertThatCode(() -> guard.requireOrderAccess(admin, order(store(2)))).doesNotThrowAnyException();
    }

    @Test
    void staffIsScopedToOwnStore() {
        User staff = user(RoleName.STAFF, store(1));
        assertThat(guard.scopedStoreId(staff)).isEqualTo(1L);
        assertThat(guard.resolveStoreFilter(staff, null)).isEqualTo(1L);
        assertThat(guard.resolveStoreFilter(staff, 1L)).isEqualTo(1L);
        assertThatThrownBy(() -> guard.resolveStoreFilter(staff, 2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void managerCannotTouchOrderOfAnotherStore() {
        User manager = user(RoleName.MANAGER, store(1));
        assertThatCode(() -> guard.requireOrderAccess(manager, order(store(1)))).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireOrderAccess(manager, order(store(2))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void staffWithoutStoreIsRejected() {
        assertThatThrownBy(() -> guard.scopedStoreId(user(RoleName.STAFF, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa được gán cơ sở");
    }

    @Test
    void customerIsNotOperator() {
        User customer = user(RoleName.CUSTOMER, null);
        assertThat(guard.isOperator(customer)).isFalse();
        assertThatThrownBy(() -> guard.requireOperator(customer)).isInstanceOf(BusinessException.class);
        assertThatCode(() -> guard.requireOrderAccess(customer, order(store(5)))).doesNotThrowAnyException();
    }

    @Test
    void storeAccess() {
        assertThatCode(() -> guard.requireStoreAccess(user(RoleName.ADMIN, null), 7L)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireStoreAccess(user(RoleName.STAFF, store(7)), 7L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireStoreAccess(user(RoleName.MANAGER, store(7)), 8L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> guard.requireStoreAccess(user(RoleName.SHIPPER, store(7)), 7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ**

Run: `./mvnw -B test -Dtest=StoreAccessGuardTest`
Expected: FAIL biên dịch — `cannot find symbol: class StoreAccessGuard`.

- [ ] **Step 3: Viết `security/StoreAccessGuard.java`**

```java
package com.banhmyking.banhmyking.security;

import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.NotFoundMessages;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Một chỗ duy nhất quyết định "người này được đụng tới cơ sở nào" (spec §4).
 * Ngoài phạm vi trả 404 thay vì 403 — không để lộ đơn/cơ sở khác có tồn tại.
 */
@Component
public class StoreAccessGuard {

    public boolean isOperator(User actor) {
        RoleName role = actor.getRole();
        return role == RoleName.STAFF || role == RoleName.MANAGER || role == RoleName.ADMIN;
    }

    public void requireOperator(User actor) {
        if (!isOperator(actor)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Chỉ nhân viên, quản lý cơ sở hoặc quản trị viên mới có quyền thực hiện");
        }
    }

    /** ADMIN → null (không giới hạn); STAFF/MANAGER/SHIPPER → cơ sở của mình. */
    public Long scopedStoreId(User actor) {
        RoleName role = actor.getRole();
        if (role == RoleName.ADMIN) {
            return null;
        }
        if (role == RoleName.CUSTOMER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Khách hàng không thuộc cơ sở nào");
        }
        if (actor.getStore() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Tài khoản chưa được gán cơ sở");
        }
        return actor.getStore().getId();
    }

    /** ADMIN lọc theo cơ sở yêu cầu (null = tất cả); vai trò khác luôn bị khoá về cơ sở mình. */
    public Long resolveStoreFilter(User actor, Long requestedStoreId) {
        Long own = scopedStoreId(actor);
        if (own == null) {
            return requestedStoreId;
        }
        if (requestedStoreId != null && !requestedStoreId.equals(own)) {
            throw new ResourceNotFoundException("Không tìm thấy cơ sở với ID: " + requestedStoreId);
        }
        return own;
    }

    public void requireStoreAccess(User actor, Long storeId) {
        RoleName role = actor.getRole();
        if (role == RoleName.ADMIN) {
            return;
        }
        boolean ownStore = (role == RoleName.STAFF || role == RoleName.MANAGER)
                && actor.getStore() != null && actor.getStore().getId().equals(storeId);
        if (!ownStore) {
            throw new ResourceNotFoundException("Không tìm thấy cơ sở với ID: " + storeId);
        }
    }

    /** Chỉ khoá STAFF/MANAGER theo cơ sở; khách/shipper đã có kiểm tra chủ đơn/được gán riêng. */
    public void requireOrderAccess(User actor, Order order) {
        RoleName role = actor.getRole();
        if (role != RoleName.STAFF && role != RoleName.MANAGER) {
            return;
        }
        if (!sameStore(actor, order.getStore())) {
            throw new ResourceNotFoundException(NotFoundMessages.orderByCode(order.getOrderCode()));
        }
    }

    public boolean sameStore(User actor, Store store) {
        return actor.getStore() != null && store != null && actor.getStore().getId().equals(store.getId());
    }
}
```

- [ ] **Step 4: Chạy, xác nhận xanh**

Run: `./mvnw -B test -Dtest=StoreAccessGuardTest`
Expected: PASS (6 test).

- [ ] **Step 5: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/security/StoreAccessGuard.java src/test/java/com/banhmyking/banhmyking/security/StoreAccessGuardTest.java
git commit -m "feat(co-so): StoreAccessGuard khoá phạm vi cơ sở theo vai trò"
```

---

## Task 3: Kho theo cơ sở + API "Tình trạng món" + bỏ tồn kho ở món (V12)

**Files:**
- Create: `src/main/resources/db/migration/V12__drop_product_stock.sql`
- Create: `dto/store/StoreStockResponse.java`, `dto/store/AvailabilityRequest.java`, `controller/StoreInventoryController.java`
- Modify: `repository/StoreProductRepository.java`, `repository/InventoryMovementRepository.java`, `service/InventoryService.java`, `service/impl/InventoryServiceImpl.java`, `service/impl/CartServiceImpl.java`, `entity/Product.java`, `entity/InventoryMovement.java`, `dto/catalog/ProductResponse.java`, `dto/catalog/ProductRequest.java`, `service/impl/CatalogServiceImpl.java`, `controller/CatalogController.java`, `config/SecurityConfig.java`
- Test: `service/InventoryServiceTest.java` (viết lại phần trừ/hoàn/điều chỉnh), `service/CartServiceTest.java` (bỏ stub `assertEnough`)

**Interfaces:**
- Consumes: `StoreProduct`, `StoreProductRepository`, `Order.getStore()` (Task 1); `StoreAccessGuard` (Task 2).
- Produces:
  - `StoreProductRepository.decrementStockAtomic(Long storeId, Long productId, int qty): int`, `incrementStockAtomic(Long storeId, Long productId, int qty): int`.
  - `InventoryService`:
    - `void decreaseForOrder(Order order)` / `boolean tryDecreaseForOrder(Order order)` / `void restoreForOrder(Order order)` — theo `order.getStore()`.
    - `List<String> unavailableItems(Long storeId, Map<Product, Integer> quantities)` — tên các món không bán được / thiếu tồn tại cơ sở (dùng ở Task 5).
    - `List<StoreStockResponse> listStoreStock(Long storeId)`.
    - `StoreStockResponse setAvailability(Long storeId, Long productId, boolean available)`.
    - `StoreStockResponse adjustStock(Long storeId, Long productId, StockChangeRequest request, Long actorId)`.
    - `PageResponse<StockMovementResponse> getMovements(Long storeId, Long productId, int page, int size)`.
    - **Bỏ** `assertEnough(Product, int)`.
  - API `/api/v1/store-inventory/{storeId}/products` (GET), `/{storeId}/products/{productId}/availability` (PATCH `{available}`), `/{storeId}/products/{productId}/stock` (POST `StockChangeRequest`), `/{storeId}/products/{productId}/movements` (GET) — STAFF/MANAGER/ADMIN, kiểm `guard.requireStoreAccess`.
  - `StoreStockResponse { Long productId; String productName; String categoryName; String imageUrl; BigDecimal price; boolean onChainMenu; boolean available; Integer stockQuantity; int lowStockThreshold; boolean lowStock; }`.
  - `ProductResponse`/`ProductRequest` **không còn** `stockQuantity`, `lowStockThreshold`, `lowStock`.
  - Ghi thực đơn chung `/api/v1/catalog/**` (không phải GET) chỉ còn ADMIN.

- [ ] **Step 1: Viết lại test kho (đỏ)**

Trong `src/test/java/com/banhmyking/banhmyking/service/InventoryServiceTest.java`:
- Thay `@Mock ProductRepository productRepository;` bằng `@Mock StoreProductRepository storeProductRepository;` và **giữ** `@Mock ProductRepository productRepository;` (dùng cho `listStoreStock`/`setAvailability`).
- Xoá 4 test `assertEnough*`.
- Helper mới (thay `banhMi(Integer stock)` và `order(...)`):

```java
    private static final Long STORE_ID = 3L;

    private Product banhMi() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Bánh mì thập cẩm");
        product.setAvailable(true);
        return product;
    }

    private Order order(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        Store store = new Store();
        store.setId(STORE_ID);
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderCode("BMK-TEST-1");
        order.setStore(store);
        order.setItems(List.of(item));
        return order;
    }

    private StoreProduct row(Integer stock, boolean available) {
        StoreProduct sp = new StoreProduct();
        sp.setId(new StoreProductId(STORE_ID, PRODUCT_ID));
        sp.setProduct(banhMi());
        sp.setAvailable(available);
        sp.setStockQuantity(stock);
        return sp;
    }
```

- Đổi mọi `productRepository.decrementStockAtomic(PRODUCT_ID, n)` → `storeProductRepository.decrementStockAtomic(STORE_ID, PRODUCT_ID, n)`, tương tự `incrementStockAtomic`; `existsByOrderIdAndProductIdAndReason` giữ nguyên.
- Trong các test trừ/hoàn, thêm stub để món được quản tồn tại cơ sở:
  `when(storeProductRepository.findByIdStoreIdAndIdProductIdIn(eq(STORE_ID), anyCollection())).thenReturn(List.of(row(10, true)));`
  và với test "món không quản tồn thì bỏ qua": trả `List.of()` rồi `verify(storeProductRepository, never()).decrementStockAtomic(anyLong(), anyLong(), anyInt());`
- Thêm test mới:

```java
    @Test
    @DisplayName("unavailableItems: hết món tại cơ sở hoặc thiếu tồn thì trả tên món")
    void unavailableItemsReportsSoldOutAndShortStock() {
        Product product = banhMi();
        when(storeProductRepository.findByIdStoreIdAndIdProductIdIn(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of(row(2, true)));

        assertThat(inventoryService.unavailableItems(STORE_ID, Map.of(product, 3)))
                .containsExactly("Bánh mì thập cẩm");
        assertThat(inventoryService.unavailableItems(STORE_ID, Map.of(product, 2))).isEmpty();
    }

    @Test
    @DisplayName("unavailableItems: không có dòng store_products = đang bán, không quản tồn")
    void unavailableItemsTreatsMissingRowAsAvailable() {
        when(storeProductRepository.findByIdStoreIdAndIdProductIdIn(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of());

        assertThat(inventoryService.unavailableItems(STORE_ID, Map.of(banhMi(), 99))).isEmpty();
    }

    @Test
    @DisplayName("unavailableItems: món đã tắt khỏi thực đơn chuỗi luôn không bán được")
    void unavailableItemsRespectsChainMenu() {
        Product product = banhMi();
        product.setAvailable(false);
        when(storeProductRepository.findByIdStoreIdAndIdProductIdIn(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of());

        assertThat(inventoryService.unavailableItems(STORE_ID, Map.of(product, 1)))
                .containsExactly("Bánh mì thập cẩm");
    }

    @Test
    @DisplayName("setAvailability: chưa có dòng thì tạo mới với is_available theo yêu cầu")
    void setAvailabilityCreatesRowWhenMissing() {
        Store store = new Store();
        store.setId(STORE_ID);
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi()));
        when(storeProductRepository.findByIdStoreIdAndIdProductId(STORE_ID, PRODUCT_ID)).thenReturn(Optional.empty());
        when(entityManager.getReference(Store.class, STORE_ID)).thenReturn(store);
        when(storeProductRepository.save(any(StoreProduct.class))).thenAnswer(inv -> inv.getArgument(0));

        StoreStockResponse result = inventoryService.setAvailability(STORE_ID, PRODUCT_ID, false);

        assertThat(result.isAvailable()).isFalse();
        assertThat(result.getStockQuantity()).isNull();
    }
```

(import `java.util.Map`, `com.banhmyking.banhmyking.entity.Store/StoreProduct/StoreProductId`, `com.banhmyking.banhmyking.dto.store.StoreStockResponse`, `com.banhmyking.banhmyking.repository.StoreProductRepository`, `static org.mockito.ArgumentMatchers.anyCollection/eq`.)

- Viết lại 4 test `adjustStock*` theo chữ ký mới `adjustStock(STORE_ID, PRODUCT_ID, change(n), 5L)`:
  - `adjustStockRejectsZero` → `BusinessException`.
  - `adjustStockRejectsNegativeOnUntracked`: `findByIdStoreIdAndIdProductId` trả `Optional.empty()` → `BusinessException` "chưa quản tồn".
  - `adjustStockStartsTrackingOnFirstImport`: không có dòng, `change(12)` → `save` một `StoreProduct` có `stockQuantity == 12`, movement `IMPORT` +12 có `store` id `STORE_ID`.
  - `adjustStockIncrementsAtomically`: dòng `row(5,true)`, `change(3)` → `verify(storeProductRepository).incrementStockAtomic(STORE_ID, PRODUCT_ID, 3)`; `entityManager.refresh(row)`.
  - `adjustStockRejectsTooLargeDecrease`: `decrementStockAtomic` trả 0 → `BusinessException`.

`src/test/java/com/banhmyking/banhmyking/service/CartServiceTest.java`: xoá mọi `when(inventoryService.assertEnough(...))`, `doThrow(...).when(inventoryService).assertEnough(...)`, `verify(inventoryService...assertEnough...)` và test chỉ kiểm `assertEnough` (giỏ hàng không còn kiểm tồn — spec §3.6); xoá `@Mock InventoryService inventoryService` nếu không còn dùng.

- [ ] **Step 2: Chạy, xác nhận đỏ**

Run: `./mvnw -B test -Dtest=InventoryServiceTest,CartServiceTest`
Expected: FAIL biên dịch (`decrementStockAtomic(long,long,int)` / `StoreStockResponse` chưa có).

- [ ] **Step 3: Thao tác tồn nguyên tử theo cơ sở — `StoreProductRepository`**

Thêm vào interface:

```java
    /** Trừ tồn nguyên tử tại một cơ sở. 0 = không đủ hàng / không quản tồn. Không clearAutomatically (giữ Order đang managed). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE StoreProduct sp SET sp.stockQuantity = sp.stockQuantity - :qty "
            + "WHERE sp.id.storeId = :storeId AND sp.id.productId = :productId "
            + "AND sp.stockQuantity IS NOT NULL AND sp.stockQuantity >= :qty")
    int decrementStockAtomic(@Param("storeId") Long storeId, @Param("productId") Long productId, @Param("qty") int qty);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE StoreProduct sp SET sp.stockQuantity = sp.stockQuantity + :qty "
            + "WHERE sp.id.storeId = :storeId AND sp.id.productId = :productId AND sp.stockQuantity IS NOT NULL")
    int incrementStockAtomic(@Param("storeId") Long storeId, @Param("productId") Long productId, @Param("qty") int qty);
```

(import `org.springframework.data.jpa.repository.Modifying`, `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`.)

`InventoryMovementRepository` — thay `findPageByProductId` bằng:

```java
    @Query(value = "SELECT m FROM InventoryMovement m LEFT JOIN FETCH m.order "
                   + "WHERE m.store.id = :storeId AND m.product.id = :productId",
            countQuery = "SELECT COUNT(m) FROM InventoryMovement m "
                   + "WHERE m.store.id = :storeId AND m.product.id = :productId")
    Page<InventoryMovement> findPageByStoreIdAndProductId(@Param("storeId") Long storeId,
                                                         @Param("productId") Long productId,
                                                         Pageable pageable);
```

Xoá 2 method `decrementStockAtomic`/`incrementStockAtomic` khỏi `ProductRepository`.

- [ ] **Step 4: DTO**

`dto/store/StoreStockResponse.java`:

```java
package com.banhmyking.banhmyking.dto.store;

import java.math.BigDecimal;
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
    /** Món còn trong thực đơn chuỗi (admin tắt = cả chuỗi ngừng bán). */
    private boolean onChainMenu;
    /** Cơ sở đang bán món (không đánh dấu hết món). */
    private boolean available;
    /** NULL = không quản tồn tại cơ sở. */
    private Integer stockQuantity;
    private int lowStockThreshold;
    private boolean lowStock;
}
```

`dto/store/AvailabilityRequest.java`:

```java
package com.banhmyking.banhmyking.dto.store;

import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(@NotNull(message = "Thiếu trạng thái còn/hết món") Boolean available) {
}
```

- [ ] **Step 5: `InventoryService` + `InventoryServiceImpl`**

Thay toàn bộ interface `service/InventoryService.java`:

```java
package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.catalog.StockChangeRequest;
import com.banhmyking.banhmyking.dto.catalog.StockMovementResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.store.StoreStockResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Product;
import java.util.List;
import java.util.Map;

/** Tồn kho và hết món theo từng cơ sở (spec §2.2, §3.6). */
public interface InventoryService {

    /** Trừ tồn tại {@code order.getStore()} khi đơn được xác nhận. Không đủ → BusinessException. */
    void decreaseForOrder(Order order);

    /** Như decreaseForOrder nhưng trả false thay vì ném lỗi (luồng đã thu tiền). */
    boolean tryDecreaseForOrder(Order order);

    /** Hoàn tồn về đúng cơ sở của đơn; gọi lại nhiều lần vẫn chỉ hoàn một lần. */
    void restoreForOrder(Order order);

    /** Tên các món không bán được tại cơ sở (tắt khỏi thực đơn chuỗi, hết món, hoặc thiếu tồn). */
    List<String> unavailableItems(Long storeId, Map<Product, Integer> quantities);

    List<StoreStockResponse> listStoreStock(Long storeId);

    StoreStockResponse setAvailability(Long storeId, Long productId, boolean available);

    StoreStockResponse adjustStock(Long storeId, Long productId, StockChangeRequest request, Long actorId);

    PageResponse<StockMovementResponse> getMovements(Long storeId, Long productId, int page, int size);
}
```

Trong `service/impl/InventoryServiceImpl.java` (giữ nguyên `toMovementResponse`, cấu trúc gộp theo món của `restoreForOrder`):
- Field: thay `ProductRepository productRepository` bằng cặp `ProductRepository productRepository` + `StoreProductRepository storeProductRepository`; giữ `InventoryMovementRepository`, `UserRepository`, `EntityManager`.
- Xoá `assertEnough` và các `@PreAuthorize` (phân quyền ở controller qua guard).
- Thay `decreaseAllOrNothing`:

```java
    private Product decreaseAllOrNothing(Order order) {
        Long storeId = order.getStore().getId();
        Set<Long> tracked = trackedProductIds(storeId, order.getItems());
        List<OrderItem> decreased = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product == null || !tracked.contains(product.getId())) {
                continue;
            }
            if (storeProductRepository.decrementStockAtomic(storeId, product.getId(), item.getQuantity()) == 0) {
                for (OrderItem done : decreased) {
                    storeProductRepository.incrementStockAtomic(storeId, done.getProduct().getId(), done.getQuantity());
                }
                return product;
            }
            decreased.add(item);
        }
        for (OrderItem item : decreased) {
            saveMovement(order.getStore(), item.getProduct(), -item.getQuantity(), InventoryReason.ORDER, order, null, null);
        }
        return null;
    }

    /** Món được quản tồn tại cơ sở = có dòng store_products với stock_quantity khác NULL. */
    private Set<Long> trackedProductIds(Long storeId, List<OrderItem> items) {
        List<Long> productIds = items.stream()
                .map(OrderItem::getProduct).filter(Objects::nonNull).map(Product::getId).distinct().toList();
        if (productIds.isEmpty()) {
            return Set.of();
        }
        return storeProductRepository.findByIdStoreIdAndIdProductIdIn(storeId, productIds).stream()
                .filter(sp -> sp.getStockQuantity() != null)
                .map(sp -> sp.getId().getProductId())
                .collect(Collectors.toSet());
    }
```

- Trong `restoreForOrder`: lấy `Long storeId = order.getStore().getId();`, đổi `productRepository.incrementStockAtomic(productId, quantity)` → `storeProductRepository.incrementStockAtomic(storeId, productId, quantity)`, `saveMovement(order.getStore(), productById.get(productId), ...)`.
- `saveMovement` thêm tham số đầu `Store store` và `movement.setStore(store);`.
- Method mới:

```java
    @Override
    @Transactional(readOnly = true)
    public List<String> unavailableItems(Long storeId, Map<Product, Integer> quantities) {
        if (quantities.isEmpty()) {
            return List.of();
        }
        Map<Long, StoreProduct> rows = storeProductRepository
                .findByIdStoreIdAndIdProductIdIn(storeId, quantities.keySet().stream().map(Product::getId).toList())
                .stream().collect(Collectors.toMap(sp -> sp.getId().getProductId(), sp -> sp));
        List<String> names = new ArrayList<>();
        for (Map.Entry<Product, Integer> entry : quantities.entrySet()) {
            Product product = entry.getKey();
            StoreProduct row = rows.get(product.getId());
            boolean soldOut = !product.isAvailable() || product.isDeleted() || (row != null && !row.isAvailable());
            boolean shortStock = row != null && row.getStockQuantity() != null && row.getStockQuantity() < entry.getValue();
            if (soldOut || shortStock) {
                names.add(product.getName());
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoreStockResponse> listStoreStock(Long storeId) {
        Map<Long, StoreProduct> rows = storeProductRepository.findByIdStoreId(storeId).stream()
                .collect(Collectors.toMap(sp -> sp.getId().getProductId(), sp -> sp));
        return productRepository.findAll(Sort.by("name")).stream()
                .filter(product -> !product.isDeleted())
                .map(product -> toStockResponse(product, rows.get(product.getId())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreStockResponse setAvailability(Long storeId, Long productId, boolean available) {
        Product product = requireProduct(productId);
        StoreProduct row = storeProductRepository.findByIdStoreIdAndIdProductId(storeId, productId)
                .orElseGet(() -> newRow(storeId, product));
        row.setAvailable(available);
        return toStockResponse(product, storeProductRepository.save(row));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreStockResponse adjustStock(Long storeId, Long productId, StockChangeRequest request, Long actorId) {
        Product product = requireProduct(productId);
        int changeQty = request.getChangeQty();
        if (changeQty == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Số lượng thay đổi phải khác 0");
        }
        StoreProduct row = storeProductRepository.findByIdStoreIdAndIdProductId(storeId, productId).orElse(null);
        Integer current = row == null ? null : row.getStockQuantity();
        if (current == null) {
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
        return toStockResponse(product, row);
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

    private StoreStockResponse toStockResponse(Product product, StoreProduct row) {
        return StoreStockResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .categoryName(product.getCategory() == null ? null : product.getCategory().getName())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .onChainMenu(product.isAvailable())
                .available(row == null || row.isAvailable())
                .stockQuantity(row == null ? null : row.getStockQuantity())
                .lowStockThreshold(row == null ? 5 : row.getLowStockThreshold())
                .lowStock(row != null && row.isLowStock())
                .build();
    }
```

(import thêm: `Store`, `StoreProduct`, `StoreProductId`, `StoreStockResponse`, `StoreProductRepository`, `java.util.Map`, `java.util.Objects`, `java.util.Set`, `java.util.stream.Collectors`.)

- [ ] **Step 6: Bỏ tồn kho khỏi món, giỏ hàng; catalog chỉ ADMIN ghi**

- `entity/Product.java`: xoá trường `stockQuantity` và `lowStockThreshold`.
- `dto/catalog/ProductResponse.java`: xoá `stockQuantity`, `lowStockThreshold`, `lowStock`.
- `dto/catalog/ProductRequest.java`: xoá `stockQuantity`, `lowStockThreshold` (kèm `@Min`).
- `service/impl/CatalogServiceImpl.java`: trong `applyProduct` xoá khối `if (product.getId() == null) { product.setStockQuantity(...) }` và khối `lowStockThreshold`; trong `toProductResponse` xoá 3 dòng `.stockQuantity/.lowStockThreshold/.lowStock`; đổi mọi `@PreAuthorize("hasAnyRole('STAFF','ADMIN')")` thành `@PreAuthorize("hasRole('ADMIN')")`.
- `controller/CatalogController.java`: xoá 2 endpoint `/products/{productId}/stock` và `/products/{productId}/stock-movements` cùng import/field `InventoryService` nếu không còn dùng; sửa mô tả Swagger "Cần quyền STAFF hoặc ADMIN" → "Cần quyền ADMIN".
- `service/impl/CartServiceImpl.java`: xoá 2 lời gọi `inventoryService.assertEnough(...)` (dòng ~151 và ~173) và field `inventoryService` + import.
- `config/SecurityConfig.java`: đổi dòng
  `.requestMatchers("/api/v1/catalog/**").hasAnyRole("STAFF", "ADMIN")`
  thành
  `.requestMatchers("/api/v1/catalog/**").hasRole("ADMIN")`
  và thêm ngay sau nó:
  `.requestMatchers("/api/v1/store-inventory/**").hasAnyRole("STAFF", "MANAGER", "ADMIN")`.

- [ ] **Step 7: `controller/StoreInventoryController.java`**

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.catalog.StockChangeRequest;
import com.banhmyking.banhmyking.dto.catalog.StockMovementResponse;
import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.store.AvailabilityRequest;
import com.banhmyking.banhmyking.dto.store.StoreStockResponse;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.exception.NotFoundMessages;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.security.StoreAccessGuard;
import com.banhmyking.banhmyking.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store-inventory/{storeId}/products")
@RequiredArgsConstructor
@Tag(name = "Store inventory", description = "Hết món và tồn kho theo cơ sở (STAFF/MANAGER cơ sở mình, ADMIN mọi cơ sở)")
public class StoreInventoryController {

    private final InventoryService inventoryService;
    private final StoreAccessGuard storeAccessGuard;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Tình trạng mọi món tại cơ sở")
    public ResponseEntity<ApiResponse<List<StoreStockResponse>>> list(
            @PathVariable Long storeId, @AuthenticationPrincipal UserDetails principal) {
        authorize(principal, storeId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy tình trạng món thành công", inventoryService.listStoreStock(storeId)));
    }

    @PatchMapping("/{productId}/availability")
    @Operation(summary = "Bật/tắt hết món tại cơ sở")
    public ResponseEntity<ApiResponse<StoreStockResponse>> setAvailability(
            @PathVariable Long storeId, @PathVariable Long productId,
            @Valid @RequestBody AvailabilityRequest request, @AuthenticationPrincipal UserDetails principal) {
        authorize(principal, storeId);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật tình trạng món thành công",
                inventoryService.setAvailability(storeId, productId, request.available())));
    }

    @PostMapping("/{productId}/stock")
    @Operation(summary = "Nhập / điều chỉnh tồn tại cơ sở")
    public ResponseEntity<ApiResponse<StoreStockResponse>> adjustStock(
            @PathVariable Long storeId, @PathVariable Long productId,
            @Valid @RequestBody StockChangeRequest request, @AuthenticationPrincipal UserDetails principal) {
        User actor = authorize(principal, storeId);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật tồn kho thành công",
                inventoryService.adjustStock(storeId, productId, request, actor.getId())));
    }

    @GetMapping("/{productId}/movements")
    @Operation(summary = "Sổ kho của món tại cơ sở")
    public ResponseEntity<ApiResponse<PageResponse<StockMovementResponse>>> movements(
            @PathVariable Long storeId, @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails principal) {
        authorize(principal, storeId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy sổ kho thành công",
                inventoryService.getMovements(storeId, productId, page, size)));
    }

    private User authorize(UserDetails principal, Long storeId) {
        Long userId = SecurityUtils.requireUserId(principal);
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(NotFoundMessages.userById(userId)));
        storeAccessGuard.requireStoreAccess(actor, storeId);
        return actor;
    }
}
```

- [ ] **Step 8: Migration V12**

`src/main/resources/db/migration/V12__drop_product_stock.sql`:

```sql
-- V12: tồn kho đã chuyển sang store_products (V11) — bỏ cột tồn ở products.
-- Mọi biến động kho giờ luôn ghi store_id → siết NOT NULL.
ALTER TABLE products DROP CONSTRAINT chk_products_stock;
ALTER TABLE products
    DROP COLUMN stock_quantity,
    DROP COLUMN low_stock_threshold;

ALTER TABLE inventory_movements MODIFY COLUMN store_id BIGINT NOT NULL;
```

`entity/InventoryMovement.java`: đổi `@JoinColumn(name = "store_id")` thành `@JoinColumn(name = "store_id", nullable = false)` và comment thành "Cơ sở có biến động tồn".

- [ ] **Step 9: Chạy test, xác nhận xanh**

Run: `./mvnw -B test -Dtest=InventoryServiceTest,CartServiceTest,PaymentServiceTest,OrderServiceTest,CatalogServiceTest`
Expected: PASS. Sửa nốt chỗ biên dịch lỗi nếu test cũ còn gọi `product.setStockQuantity(...)`/`getStockQuantity()` (xoá các dòng đó — giỏ hàng/đơn không còn dùng tồn ở món).

Run: `./mvnw -B test`
Expected: BUILD SUCCESS; log có `version "12 - drop product stock"`.

- [ ] **Step 10: Commit (người dùng tự chạy)**

```
git add -A src/main/java src/test/java src/main/resources/db/migration/V12__drop_product_stock.sql
git commit -m "feat(co-so): tồn kho và hết món theo từng cơ sở, API Tình trạng món, chỉ ADMIN sửa thực đơn chung (V12)"
```

---

## Task 4: Quản lý cơ sở — CRUD, danh sách public, tạm ngưng nhận đơn

**Files:**
- Create: `config/TimeConfig.java`, `service/StoreHours.java`, `service/StoreService.java`, `service/impl/StoreServiceImpl.java`
- Create: `dto/store/StoreRequest.java`, `dto/store/StoreResponse.java`, `dto/store/PublicStoreResponse.java`, `dto/store/AcceptingOrdersRequest.java`
- Create: `controller/StoreController.java`, `controller/AdminStoreController.java`, `controller/ManagerController.java`
- Modify: `repository/UserRepository.java`, `repository/OrderRepository.java`, `config/SecurityConfig.java`
- Test: `service/StoreServiceImplTest.java`, `service/StoreHoursTest.java`

**Interfaces:**
- Consumes: `Store`, `StoreRepository` (Task 1), `StoreAccessGuard` (Task 2).
- Produces:
  - Bean `java.time.Clock` (`Asia/Ho_Chi_Minh`).
  - `StoreHours.isOpen(Store store, LocalTime now): boolean` (static) — `open <= now < close`.
  - `StoreService`: `List<StoreResponse> listAll()`, `List<PublicStoreResponse> listPublic()`, `StoreResponse get(Long id)`, `StoreResponse create(StoreRequest r)`, `StoreResponse update(Long id, StoreRequest r)`, `void delete(Long id)`, `StoreResponse setAcceptingOrders(Long actorId, Long storeId, boolean accepting)`, `Store requireActiveStore(Long id)`.
  - `UserRepository.countByStoreIdAndDeletedFalse(Long storeId): long`.
  - `OrderRepository.countByStoreIdAndStatusIn(Long storeId, List<OrderStatus> statuses): long`.
  - API: `GET /api/v1/stores` (public), `GET|POST /api/v1/admin/stores`, `GET|PUT|DELETE /api/v1/admin/stores/{id}`, `PATCH /api/v1/manager/stores/{storeId}/accepting` (MANAGER cơ sở mình, ADMIN mọi cơ sở).
  - `StoreResponse` = mọi cột của `Store` + `boolean openNow` + `long staffCount`; `PublicStoreResponse { id, code, name, address, phone, latitude, longitude, openTime "HH:mm", closeTime "HH:mm", openNow, acceptingOrders }`.

- [ ] **Step 1: Test (đỏ)**

`src/test/java/com/banhmyking/banhmyking/service/StoreHoursTest.java`:

```java
package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.banhmyking.banhmyking.entity.Store;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class StoreHoursTest {

    private final Store store = new Store(); // mặc định 06:30–22:00

    @Test
    void openInsideHours() {
        assertThat(StoreHours.isOpen(store, LocalTime.of(6, 30))).isTrue();
        assertThat(StoreHours.isOpen(store, LocalTime.of(21, 59))).isTrue();
    }

    @Test
    void closedAtAndAfterCloseTimeAndBeforeOpen() {
        assertThat(StoreHours.isOpen(store, LocalTime.of(22, 0))).isFalse();
        assertThat(StoreHours.isOpen(store, LocalTime.of(6, 29))).isFalse();
    }
}
```

`src/test/java/com/banhmyking/banhmyking/service/StoreServiceImplTest.java`:

```java
package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.dto.store.StoreRequest;
import com.banhmyking.banhmyking.dto.store.StoreResponse;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.StoreAccessGuard;
import com.banhmyking.banhmyking.service.impl.StoreServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreServiceImplTest {

    @Mock private StoreRepository storeRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;

    private StoreServiceImpl service;

    /** 10:00 sáng giờ VN */
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @BeforeEach
    void setUp() {
        service = new StoreServiceImpl(storeRepository, userRepository, orderRepository, new StoreAccessGuard(), clock);
    }

    private StoreRequest request(String code) {
        return new StoreRequest(code, "Cơ sở Cầu Giấy", "12 Trần Thái Tông, Hà Nội", "0901000001",
                new BigDecimal("21.033300"), new BigDecimal("105.792000"), "06:30", "22:00",
                new BigDecimal("5"), new BigDecimal("3"), new BigDecimal("50000"), true);
    }

    @Test
    void createNormalisesCodeAndRejectsDuplicates() {
        when(storeRepository.existsByCodeAndDeletedFalse("CS02")).thenReturn(false);
        when(storeRepository.save(any(Store.class))).thenAnswer(inv -> {
            Store s = inv.getArgument(0);
            s.setId(2L);
            return s;
        });

        StoreResponse created = service.create(request(" cs02 "));

        assertThat(created.getCode()).isEqualTo("CS02");
        assertThat(created.isOpenNow()).isTrue();

        when(storeRepository.existsByCodeAndDeletedFalse("CS02")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request("CS02"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsOpenTimeNotBeforeCloseTime() {
        StoreRequest bad = new StoreRequest("CS09", "X", "Y địa chỉ", null, null, null, "22:00", "06:30",
                new BigDecimal("5"), new BigDecimal("3"), BigDecimal.ZERO, true);
        assertThatThrownBy(() -> service.create(bad))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Giờ mở cửa phải trước giờ đóng cửa");
    }

    @Test
    void deleteRefusedWhileStaffOrOpenOrdersRemain() {
        Store store = new Store();
        store.setId(4L);
        when(storeRepository.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(store));
        when(userRepository.countByStoreIdAndDeletedFalse(4L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(4L)).hasMessageContaining("còn nhân viên");
        verify(storeRepository, never()).save(any());

        when(userRepository.countByStoreIdAndDeletedFalse(4L)).thenReturn(0L);
        when(orderRepository.countByStoreIdAndStatusIn(any(), anyList())).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(4L)).hasMessageContaining("đơn chưa kết thúc");
    }

    @Test
    void managerTogglesOnlyOwnStore() {
        Store own = new Store();
        own.setId(1L);
        User manager = new User();
        manager.setId(50L);
        manager.setRole(RoleName.MANAGER);
        manager.setStore(own);
        when(userRepository.findById(50L)).thenReturn(Optional.of(manager));
        when(storeRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(own));
        when(storeRepository.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.setAcceptingOrders(50L, 1L, false).isAcceptingOrders()).isFalse();
        assertThatThrownBy(() -> service.setAcceptingOrders(50L, 2L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ**

Run: `./mvnw -B test -Dtest=StoreHoursTest,StoreServiceImplTest`
Expected: FAIL biên dịch.

- [ ] **Step 3: `config/TimeConfig.java` + `service/StoreHours.java`**

```java
package com.banhmyking.banhmyking.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Giờ Việt Nam dùng chung — inject Clock để test giờ mở cửa không phụ thuộc giờ máy. */
@Configuration
public class TimeConfig {

    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public Clock clock() {
        return Clock.system(VIETNAM);
    }
}
```

```java
package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.entity.Store;
import java.time.LocalTime;

/** Giờ mở cửa: cùng giờ mọi ngày, không hỗ trợ ca qua nửa đêm (spec §3.5). */
public final class StoreHours {

    private StoreHours() {
    }

    public static boolean isOpen(Store store, LocalTime now) {
        return !now.isBefore(store.getOpenTime()) && now.isBefore(store.getCloseTime());
    }
}
```

- [ ] **Step 4: DTO**

`dto/store/StoreRequest.java`:

```java
package com.banhmyking.banhmyking.dto.store;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record StoreRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "^\\s*[A-Za-z0-9_-]+\\s*$", message = "Mã chỉ gồm chữ, số, - và _") String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 500) String address,
        @Size(max = 20) String phone,
        @DecimalMin(value = "8.0", message = "Vĩ độ nằm ngoài Việt Nam") @DecimalMax(value = "23.5", message = "Vĩ độ nằm ngoài Việt Nam") BigDecimal latitude,
        @DecimalMin(value = "102.0", message = "Kinh độ nằm ngoài Việt Nam") @DecimalMax(value = "110.0", message = "Kinh độ nằm ngoài Việt Nam") BigDecimal longitude,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ dạng HH:mm") String openTime,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ dạng HH:mm") String closeTime,
        @NotNull @DecimalMin(value = "0.5") @DecimalMax(value = "100") BigDecimal deliveryRadiusKm,
        @NotNull @DecimalMin(value = "0") @DecimalMax(value = "100") BigDecimal freeShipRadiusKm,
        @NotNull @DecimalMin(value = "0") BigDecimal minOrderAmount,
        boolean active) {
}
```

`dto/store/StoreResponse.java`:

```java
package com.banhmyking.banhmyking.dto.store;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoreResponse {
    private Long id;
    private String code;
    private String name;
    private String address;
    private String phone;
    private BigDecimal latitude;
    private BigDecimal longitude;
    /** HH:mm */
    private String openTime;
    private String closeTime;
    private boolean acceptingOrders;
    private BigDecimal deliveryRadiusKm;
    private BigDecimal freeShipRadiusKm;
    private BigDecimal minOrderAmount;
    private boolean active;
    private boolean openNow;
    private long staffCount;
}
```

`dto/store/PublicStoreResponse.java`:

```java
package com.banhmyking.banhmyking.dto.store;

import java.math.BigDecimal;

/** Thông tin cơ sở cho trang "Hệ thống cửa hàng" — không lộ cấu hình phí/bán kính. */
public record PublicStoreResponse(Long id, String code, String name, String address, String phone,
                                  BigDecimal latitude, BigDecimal longitude, String openTime, String closeTime,
                                  boolean openNow, boolean acceptingOrders) {
}
```

`dto/store/AcceptingOrdersRequest.java`:

```java
package com.banhmyking.banhmyking.dto.store;

import jakarta.validation.constraints.NotNull;

public record AcceptingOrdersRequest(@NotNull(message = "Thiếu trạng thái nhận đơn") Boolean accepting) {
}
```

- [ ] **Step 5: Repository**

`repository/UserRepository.java` thêm: `long countByStoreIdAndDeletedFalse(Long storeId);`

`repository/OrderRepository.java` thêm: `long countByStoreIdAndStatusIn(Long storeId, List<OrderStatus> statuses);`

- [ ] **Step 6: `service/StoreService.java` + `service/impl/StoreServiceImpl.java`**

```java
package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.store.PublicStoreResponse;
import com.banhmyking.banhmyking.dto.store.StoreRequest;
import com.banhmyking.banhmyking.dto.store.StoreResponse;
import com.banhmyking.banhmyking.entity.Store;
import java.util.List;

public interface StoreService {
    List<StoreResponse> listAll();
    List<PublicStoreResponse> listPublic();
    StoreResponse get(Long id);
    StoreResponse create(StoreRequest request);
    StoreResponse update(Long id, StoreRequest request);
    void delete(Long id);
    StoreResponse setAcceptingOrders(Long actorId, Long storeId, boolean accepting);
    /** Cơ sở chưa xoá và đang hoạt động; không có → ResourceNotFoundException. */
    Store requireActiveStore(Long id);
}
```

```java
package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.store.PublicStoreResponse;
import com.banhmyking.banhmyking.dto.store.StoreRequest;
import com.banhmyking.banhmyking.dto.store.StoreResponse;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.NotFoundMessages;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.StoreAccessGuard;
import com.banhmyking.banhmyking.service.StoreHours;
import com.banhmyking.banhmyking.service.StoreService;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<OrderStatus> OPEN_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED,
            OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERING);

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final StoreAccessGuard storeAccessGuard;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<StoreResponse> listAll() {
        return storeRepository.findByDeletedFalseOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicStoreResponse> listPublic() {
        LocalTime now = LocalTime.now(clock);
        return storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc().stream()
                .map(s -> new PublicStoreResponse(s.getId(), s.getCode(), s.getName(), s.getAddress(), s.getPhone(),
                        s.getLatitude(), s.getLongitude(), s.getOpenTime().format(HH_MM),
                        s.getCloseTime().format(HH_MM), StoreHours.isOpen(s, now), s.isAcceptingOrders()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse get(Long id) {
        return toResponse(requireStore(id));
    }

    @Override
    @Transactional
    public StoreResponse create(StoreRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (storeRepository.existsByCodeAndDeletedFalse(code)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã cơ sở " + code + " đã tồn tại");
        }
        Store store = new Store();
        store.setCode(code);
        apply(store, request);
        return toResponse(storeRepository.save(store));
    }

    @Override
    @Transactional
    public StoreResponse update(Long id, StoreRequest request) {
        Store store = requireStore(id);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!code.equals(store.getCode()) && storeRepository.existsByCodeAndDeletedFalse(code)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã cơ sở " + code + " đã tồn tại");
        }
        store.setCode(code);
        apply(store, request);
        return toResponse(storeRepository.save(store));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Store store = requireStore(id);
        if (userRepository.countByStoreIdAndDeletedFalse(id) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Cơ sở còn nhân viên — chuyển nhân viên sang cơ sở khác trước khi xoá");
        }
        if (orderRepository.countByStoreIdAndStatusIn(id, OPEN_STATUSES) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Cơ sở còn đơn chưa kết thúc — không thể xoá");
        }
        store.setDeleted(true);
        store.setActive(false);
        storeRepository.save(store);
    }

    @Override
    @Transactional
    public StoreResponse setAcceptingOrders(Long actorId, Long storeId, boolean accepting) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException(NotFoundMessages.userById(actorId)));
        storeAccessGuard.requireStoreAccess(actor, storeId);
        Store store = requireStore(storeId);
        store.setAcceptingOrders(accepting);
        return toResponse(storeRepository.save(store));
    }

    @Override
    @Transactional(readOnly = true)
    public Store requireActiveStore(Long id) {
        Store store = requireStore(id);
        if (!store.isActive()) {
            throw new ResourceNotFoundException("Không tìm thấy cơ sở với ID: " + id);
        }
        return store;
    }

    private Store requireStore(Long id) {
        return storeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cơ sở với ID: " + id));
    }

    private void apply(Store store, StoreRequest request) {
        LocalTime open = LocalTime.parse(request.openTime());
        LocalTime close = LocalTime.parse(request.closeTime());
        if (!open.isBefore(close)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giờ mở cửa phải trước giờ đóng cửa");
        }
        boolean pinned = request.latitude() != null && request.longitude() != null;
        store.setName(request.name().trim());
        store.setAddress(request.address().trim());
        store.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        store.setLatitude(pinned ? request.latitude() : null);
        store.setLongitude(pinned ? request.longitude() : null);
        store.setOpenTime(open);
        store.setCloseTime(close);
        store.setDeliveryRadiusKm(request.deliveryRadiusKm());
        store.setFreeShipRadiusKm(request.freeShipRadiusKm());
        store.setMinOrderAmount(request.minOrderAmount());
        store.setActive(request.active());
    }

    private StoreResponse toResponse(Store s) {
        return StoreResponse.builder()
                .id(s.getId()).code(s.getCode()).name(s.getName()).address(s.getAddress()).phone(s.getPhone())
                .latitude(s.getLatitude()).longitude(s.getLongitude())
                .openTime(s.getOpenTime().format(HH_MM)).closeTime(s.getCloseTime().format(HH_MM))
                .acceptingOrders(s.isAcceptingOrders()).deliveryRadiusKm(s.getDeliveryRadiusKm())
                .freeShipRadiusKm(s.getFreeShipRadiusKm()).minOrderAmount(s.getMinOrderAmount())
                .active(s.isActive()).openNow(StoreHours.isOpen(s, LocalTime.now(clock)))
                .staffCount(s.getId() == null ? 0 : userRepository.countByStoreIdAndDeletedFalse(s.getId()))
                .build();
    }
}
```

- [ ] **Step 7: Controller**

`controller/StoreController.java`:

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.store.PublicStoreResponse;
import com.banhmyking.banhmyking.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
@Tag(name = "Stores (public)", description = "Hệ thống cửa hàng cho khách")
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    @Operation(summary = "Danh sách cơ sở đang hoạt động")
    public ResponseEntity<ApiResponse<List<PublicStoreResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách cơ sở thành công", storeService.listPublic()));
    }
}
```

`controller/AdminStoreController.java`:

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.store.StoreRequest;
import com.banhmyking.banhmyking.dto.store.StoreResponse;
import com.banhmyking.banhmyking.service.StoreService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stores")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin stores", description = "Quản lý cơ sở trong chuỗi (ADMIN)")
public class AdminStoreController {

    private final StoreService storeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách cơ sở thành công", storeService.listAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy cơ sở thành công", storeService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StoreResponse>> create(@Valid @RequestBody StoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo cơ sở thành công", storeService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreResponse>> update(@PathVariable Long id, @Valid @RequestBody StoreRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật cơ sở thành công", storeService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        storeService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Xoá cơ sở thành công"));
    }
}
```

`controller/ManagerController.java` (Task 7 và 8 bổ sung thêm endpoint vào đây):

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.store.AcceptingOrdersRequest;
import com.banhmyking.banhmyking.dto.store.StoreResponse;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
@Tag(name = "Manager", description = "Quản lý cơ sở (MANAGER cơ sở mình, ADMIN mọi cơ sở)")
public class ManagerController {

    private final StoreService storeService;

    @PatchMapping("/stores/{storeId}/accepting")
    @Operation(summary = "Tạm ngưng / mở lại nhận đơn")
    public ResponseEntity<ApiResponse<StoreResponse>> setAccepting(
            @PathVariable Long storeId, @Valid @RequestBody AcceptingOrdersRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        StoreResponse store = storeService.setAcceptingOrders(
                SecurityUtils.requireUserId(principal), storeId, request.accepting());
        return ResponseEntity.ok(ApiResponse.ok(
                store.isAcceptingOrders() ? "Đã mở lại nhận đơn" : "Đã tạm ngưng nhận đơn", store));
    }
}
```

`config/SecurityConfig.java` — thêm trước dòng `.requestMatchers("/api/v1/admin/orders/**")`:

```java
                        .requestMatchers(HttpMethod.GET, "/api/v1/stores").permitAll()
                        .requestMatchers("/api/v1/manager/**").hasAnyRole("MANAGER", "ADMIN")
```

- [ ] **Step 8: Chạy, xác nhận xanh**

Run: `./mvnw -B test -Dtest=StoreHoursTest,StoreServiceImplTest`
Expected: PASS. Rồi `./mvnw -B test` → BUILD SUCCESS.

- [ ] **Step 9: Commit (người dùng tự chạy)**

```
git add -A src/main/java src/test/java
git commit -m "feat(co-so): CRUD cơ sở cho admin, danh sách cơ sở công khai, tạm ngưng nhận đơn"
```

---

## Task 5: Chọn cơ sở khi báo giá & tạo đơn, freeship theo km (V13)

**Files:**
- Create: `service/StoreSelectionService.java`, `dto/store/StoreQuoteOption.java`, `dto/store/DeliveryQuoteResponse.java`, `src/main/resources/db/migration/V13__store_not_null_and_cleanup.sql`
- Modify: `service/DeliveryFeeCalculator.java`, `controller/DeliveryController.java`, `dto/order/CreateOrderRequest.java`, `dto/order/OrderResponse.java`, `service/impl/OrderServiceImpl.java`, `entity/Order.java`, `service/SiteSettingKeys.java`, `service/impl/SiteSettingServiceImpl.java`, `config/SecurityConfig.java`
- Delete: `service/StoreDistanceService.java`, `src/test/java/.../service/StoreDistanceServiceTest.java`, `dto/delivery/CalculateDeliveryFeeRequest.java`
- Test: `service/StoreSelectionServiceTest.java` (mới), `service/DeliveryFeeCalculatorTest.java`, `service/OrderServiceTest.java`, `service/OrderPriceSnapshotTest.java`, `service/SiteSettingServiceImplTest.java`

**Interfaces:**
- Consumes: `InventoryService.unavailableItems` (Task 3), `StoreRepository` (Task 1), `StoreHours`, `Clock` (Task 4), `GeoUtils.haversineKm` (đã có).
- Produces:
  - `enum StoreSelectionService.Reason { CLOSED, NOT_ACCEPTING, OUT_OF_RADIUS, BELOW_MIN_ORDER, ITEM_UNAVAILABLE }`.
  - `record StoreSelectionService.Candidate(Store store, BigDecimal distanceKm, List<Reason> reasons, List<String> unavailableItems)` với `boolean eligible()`.
  - `List<Candidate> evaluate(BigDecimal lat, BigDecimal lng, BigDecimal subtotal, Map<Product,Integer> items)` — mọi cơ sở active, sắp xếp: đủ điều kiện trước, rồi khoảng cách tăng dần (null cuối), rồi mã.
  - `Optional<Candidate> recommend(List<Candidate>)` — phần tử đầu nếu `eligible()` **và** điểm giao có toạ độ.
  - `Candidate requireEligible(Long storeId, BigDecimal lat, BigDecimal lng, BigDecimal subtotal, Map<Product,Integer> items)` — `storeId` null → đề xuất; không có đề xuất mà điểm giao không toạ độ → `BusinessException("Vui lòng chọn cơ sở phục vụ")`; cơ sở không đủ điều kiện → `BusinessException` với lý do tiếng Việt.
  - `String describe(Reason r, Candidate c)` — câu tiếng Việt (dùng cho lỗi và frontend tự dịch mã).
  - `DeliveryFeeCalculator.calculateFee(BigDecimal distanceKm, String address, BigDecimal subtotal, BigDecimal freeShipRadiusKm)`.
  - `GET /api/v1/delivery/quote?latitude&longitude&shippingAddress` (đăng nhập; đọc giỏ hàng phía server) → `DeliveryQuoteResponse { Long recommendedStoreId; List<StoreQuoteOption> options; }`.
  - `StoreQuoteOption { Long storeId; String storeCode; String storeName; String storeAddress; String storePhone; String openTime; String closeTime; BigDecimal minOrderAmount; BigDecimal distanceKm; BigDecimal shippingFee; BigDecimal originalFee; boolean freeship; String feeDescription; boolean eligible; List<String> reasons; List<String> unavailableItems; }`.
  - `CreateOrderRequest.storeId` (Long, tuỳ chọn).
  - `OrderResponse.storeId`, `storeName`, `storePhone`.
  - Xoá API `GET/POST /api/v1/delivery/fee`; xoá khoá `storeLatitude/storeLongitude/deliveryMaxRadiusKm` khỏi `SiteSettingKeys`.

- [ ] **Step 1: Test chọn cơ sở (đỏ)**

`src/test/java/com/banhmyking/banhmyking/service/StoreSelectionServiceTest.java`:

```java
package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.service.StoreSelectionService.Candidate;
import com.banhmyking.banhmyking.service.StoreSelectionService.Reason;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreSelectionServiceTest {

    // Điểm giao: gần Hồ Gươm
    private static final BigDecimal LAT = new BigDecimal("21.028700");
    private static final BigDecimal LNG = new BigDecimal("105.852400");

    @Mock private StoreRepository storeRepository;
    @Mock private InventoryService inventoryService;

    private StoreSelectionService service;
    private final Map<Product, Integer> items = Map.of(new Product(), 1);

    /** 10:00 sáng giờ VN */
    private final Clock tenAm = Clock.fixed(Instant.parse("2026-10-01T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @BeforeEach
    void setUp() {
        service = new StoreSelectionService(storeRepository, inventoryService, tenAm, 1.3);
        lenient().when(inventoryService.unavailableItems(anyLong(), anyMap())).thenReturn(List.of());
    }

    private static Store store(long id, String code, String lat, String lng) {
        Store s = new Store();
        s.setId(id);
        s.setCode(code);
        s.setName("Cơ sở " + code);
        s.setLatitude(lat == null ? null : new BigDecimal(lat));
        s.setLongitude(lng == null ? null : new BigDecimal(lng));
        return s;
    }

    @Test
    void recommendsNearestEligibleStore() {
        Store near = store(1, "CS01", "21.030000", "105.850000");   // ~0.3 km
        Store far = store(2, "CS02", "21.016000", "105.814000");    // ~5.4 km theo đường
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(far, near));

        List<Candidate> result = service.evaluate(LAT, LNG, new BigDecimal("60000"), items);

        assertThat(result.get(0).store().getCode()).isEqualTo("CS01");
        assertThat(service.recommend(result)).map(c -> c.store().getCode()).contains("CS01");
        assertThat(result.get(1).reasons()).containsExactly(Reason.OUT_OF_RADIUS);
    }

    @Test
    void closedNotAcceptingAndBelowMinOrderAreReported() {
        Store closed = store(1, "CS01", "21.030000", "105.850000");
        closed.setOpenTime(LocalTime.of(11, 0));
        Store paused = store(2, "CS02", "21.029000", "105.851000");
        paused.setAcceptingOrders(false);
        Store minOrder = store(3, "CS03", "21.028000", "105.852000");
        minOrder.setMinOrderAmount(new BigDecimal("50000"));
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(closed, paused, minOrder));

        List<Candidate> result = service.evaluate(LAT, LNG, new BigDecimal("30000"), items);

        assertThat(result).allMatch(c -> !c.eligible());
        assertThat(result).filteredOn(c -> c.store().getCode().equals("CS01")).first()
                .extracting(Candidate::reasons).asList().containsExactly(Reason.CLOSED);
        assertThat(result).filteredOn(c -> c.store().getCode().equals("CS02")).first()
                .extracting(Candidate::reasons).asList().containsExactly(Reason.NOT_ACCEPTING);
        assertThat(result).filteredOn(c -> c.store().getCode().equals("CS03")).first()
                .extracting(Candidate::reasons).asList().containsExactly(Reason.BELOW_MIN_ORDER);
        assertThat(service.recommend(result)).isEmpty();
    }

    @Test
    void soldOutItemMakesStoreIneligible() {
        Store s = store(1, "CS01", "21.030000", "105.850000");
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(s));
        when(inventoryService.unavailableItems(1L, items)).thenReturn(List.of("Bánh mì pate"));

        Candidate c = service.evaluate(LAT, LNG, new BigDecimal("60000"), items).get(0);

        assertThat(c.reasons()).containsExactly(Reason.ITEM_UNAVAILABLE);
        assertThat(c.unavailableItems()).containsExactly("Bánh mì pate");
    }

    @Test
    void storeWithoutLocationServesEverywhereButRanksLast() {
        Store unpinned = store(1, "CS01", null, null);
        Store pinned = store(2, "CS02", "21.030000", "105.850000");
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(unpinned, pinned));

        List<Candidate> result = service.evaluate(LAT, LNG, new BigDecimal("60000"), items);

        assertThat(result).extracting(c -> c.store().getCode()).containsExactly("CS02", "CS01");
        assertThat(result.get(1).eligible()).isTrue();
        assertThat(result.get(1).distanceKm()).isNull();
    }

    @Test
    void destinationWithoutCoordinatesHasNoRecommendationAndRequiresChoice() {
        Store s = store(1, "CS01", "21.030000", "105.850000");
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(s));

        List<Candidate> result = service.evaluate(null, null, new BigDecimal("60000"), items);

        assertThat(result.get(0).eligible()).isTrue();
        assertThat(service.recommend(result)).isEmpty();
        assertThatThrownBy(() -> service.requireEligible(null, null, null, new BigDecimal("60000"), items))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chọn cơ sở");
        assertThat(service.requireEligible(1L, null, null, new BigDecimal("60000"), items).store().getId()).isEqualTo(1L);
    }

    @Test
    void requireEligibleRejectsChosenStoreWithReason() {
        Store far = store(2, "CS02", "21.016000", "105.814000");
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(far));

        assertThatThrownBy(() -> service.requireEligible(2L, LAT, LNG, new BigDecimal("60000"), items))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ngoài bán kính giao hàng");
    }
}
```

`src/test/java/com/banhmyking/banhmyking/service/DeliveryFeeCalculatorTest.java` — thêm nested/plain test:

```java
    @Test
    @DisplayName("Freeship theo bán kính: trong bán kính miễn phí, đúng bằng bán kính vẫn miễn phí, ngoài thì tính phí")
    void calculateFee_freeShipRadius() {
        DeliveryFeeResult inside = calculator.calculateFee(new BigDecimal("2.5"), "Hà Nội", new BigDecimal("50000"), new BigDecimal("3"));
        DeliveryFeeResult edge = calculator.calculateFee(new BigDecimal("3.0"), "Hà Nội", new BigDecimal("50000"), new BigDecimal("3"));
        DeliveryFeeResult outside = calculator.calculateFee(new BigDecimal("3.5"), "Hà Nội", new BigDecimal("50000"), new BigDecimal("3"));

        assertThat(inside.isFreeship()).isTrue();
        assertThat(inside.getShippingFee()).isEqualByComparingTo("0");
        assertThat(edge.isFreeship()).isTrue();
        assertThat(outside.isFreeship()).isFalse();
        assertThat(outside.getShippingFee()).isEqualByComparingTo("25000");
    }
```

(`3.5 km` → 15 000 + ceil(1.5)=2 × 5 000 = 25 000, đúng công thức hiện có.)

- [ ] **Step 2: Chạy, xác nhận đỏ**

Run: `./mvnw -B test -Dtest=StoreSelectionServiceTest,DeliveryFeeCalculatorTest`
Expected: FAIL biên dịch.

- [ ] **Step 3: `DeliveryFeeCalculator` — overload 4 tham số**

Đổi chữ ký method hiện có thành 4 tham số và thêm overload 3 tham số giữ tương thích:

```java
    public DeliveryFeeResult calculateFee(BigDecimal distanceKm, String shippingAddress, BigDecimal subtotal) {
        return calculateFee(distanceKm, shippingAddress, subtotal, null);
    }

    /**
     * Như trên + freeship theo bán kính của cơ sở (spec §3.3): khoảng cách ≤ freeShipRadiusKm → miễn phí.
     * freeShipRadiusKm null/0 = chỉ xét ngưỡng giá trị đơn.
     */
    public DeliveryFeeResult calculateFee(BigDecimal distanceKm, String shippingAddress, BigDecimal subtotal,
                                          BigDecimal freeShipRadiusKm) {
```

Trong thân method, ngay sau khi tính `originalFee` và **trước** khối "Kiểm tra ngưỡng Freeship", thêm:

```java
        boolean freeByRadius = distanceKm != null && freeShipRadiusKm != null
                && freeShipRadiusKm.compareTo(BigDecimal.ZERO) > 0
                && distanceKm.compareTo(freeShipRadiusKm) <= 0;
```

và đổi khối freeship thành:

```java
        if (freeByRadius) {
            isFreeship = true;
            finalFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            description = String.format("Miễn phí giao hàng trong bán kính %.1f km", freeShipRadiusKm.doubleValue());
        } else if (subtotal != null && subtotal.compareTo(freeshipThreshold) >= 0) {
            // (giữ nguyên nội dung nhánh cũ)
```

- [ ] **Step 4: `service/StoreSelectionService.java`**

```java
package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.util.GeoUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Chấm điều kiện từng cơ sở cho một giỏ hàng + điểm giao (spec §3.1, §3.2, §3.4).
 * Server luôn chấm lại khi tạo đơn — không tin cơ sở/phí client gửi.
 */
@Service
public class StoreSelectionService {

    public enum Reason { CLOSED, NOT_ACCEPTING, OUT_OF_RADIUS, BELOW_MIN_ORDER, ITEM_UNAVAILABLE }

    public record Candidate(Store store, BigDecimal distanceKm, List<Reason> reasons, List<String> unavailableItems) {
        public boolean eligible() {
            return reasons.isEmpty();
        }
    }

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final StoreRepository storeRepository;
    private final InventoryService inventoryService;
    private final Clock clock;
    private final double roadFactor;

    public StoreSelectionService(StoreRepository storeRepository, InventoryService inventoryService, Clock clock,
                                 @Value("${delivery.road-factor:1.3}") double roadFactor) {
        this.storeRepository = storeRepository;
        this.inventoryService = inventoryService;
        this.clock = clock;
        this.roadFactor = roadFactor;
    }

    public List<Candidate> evaluate(BigDecimal latitude, BigDecimal longitude, BigDecimal subtotal,
                                    Map<Product, Integer> items) {
        boolean destinationPinned = latitude != null && longitude != null;
        LocalTime now = LocalTime.now(clock);
        List<Candidate> result = new ArrayList<>();
        for (Store store : storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()) {
            List<Reason> reasons = new ArrayList<>();
            BigDecimal distance = null;
            if (destinationPinned && store.hasLocation()) {
                double km = GeoUtils.haversineKm(store.getLatitude().doubleValue(), store.getLongitude().doubleValue(),
                        latitude.doubleValue(), longitude.doubleValue()) * roadFactor;
                distance = BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
                if (distance.compareTo(store.getDeliveryRadiusKm()) > 0) {
                    reasons.add(Reason.OUT_OF_RADIUS);
                }
            }
            if (!StoreHours.isOpen(store, now)) {
                reasons.add(0, Reason.CLOSED);
            } else if (!store.isAcceptingOrders()) {
                reasons.add(0, Reason.NOT_ACCEPTING);
            }
            if (subtotal != null && subtotal.compareTo(store.getMinOrderAmount()) < 0) {
                reasons.add(Reason.BELOW_MIN_ORDER);
            }
            List<String> unavailable = inventoryService.unavailableItems(store.getId(), items);
            if (!unavailable.isEmpty()) {
                reasons.add(Reason.ITEM_UNAVAILABLE);
            }
            result.add(new Candidate(store, distance, List.copyOf(reasons), unavailable));
        }
        result.sort(Comparator.comparing((Candidate c) -> !c.eligible())
                .thenComparing(Candidate::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(c -> c.store().getCode()));
        return result;
    }

    /** Đề xuất chỉ khi điểm giao có toạ độ — không có toạ độ thì khách tự chọn (spec §3.4). */
    public Optional<Candidate> recommend(List<Candidate> candidates) {
        if (candidates.isEmpty() || !candidates.get(0).eligible()) {
            return Optional.empty();
        }
        boolean anyDistance = candidates.stream().anyMatch(c -> c.distanceKm() != null);
        return anyDistance ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    public Candidate requireEligible(Long storeId, BigDecimal latitude, BigDecimal longitude, BigDecimal subtotal,
                                     Map<Product, Integer> items) {
        List<Candidate> candidates = evaluate(latitude, longitude, subtotal, items);
        if (storeId == null) {
            return recommend(candidates).orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR,
                    candidates.stream().anyMatch(Candidate::eligible)
                            ? "Vui lòng chọn cơ sở phục vụ đơn hàng"
                            : "Hiện chưa có cơ sở nào phục vụ được địa chỉ và giỏ hàng này"));
        }
        Candidate chosen = candidates.stream().filter(c -> c.store().getId().equals(storeId)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "Cơ sở đã chọn không còn hoạt động"));
        if (!chosen.eligible()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    chosen.store().getName() + ": " + describe(chosen.reasons().get(0), chosen));
        }
        return chosen;
    }

    public String describe(Reason reason, Candidate c) {
        Store s = c.store();
        return switch (reason) {
            case CLOSED -> "đang đóng cửa (mở " + s.getOpenTime().format(HH_MM) + "–" + s.getCloseTime().format(HH_MM) + ")";
            case NOT_ACCEPTING -> "tạm ngưng nhận đơn";
            case OUT_OF_RADIUS -> String.format("địa chỉ cách khoảng %.1f km, ngoài bán kính giao hàng %.1f km",
                    c.distanceKm().doubleValue(), s.getDeliveryRadiusKm().doubleValue());
            case BELOW_MIN_ORDER -> "đơn tối thiểu " + NumberFormat.getIntegerInstance(new Locale("vi", "VN"))
                    .format(s.getMinOrderAmount()) + "đ";
            case ITEM_UNAVAILABLE -> "tạm hết " + String.join(", ", c.unavailableItems());
        };
    }
}
```

- [ ] **Step 5: DTO báo giá + DeliveryController**

`dto/store/StoreQuoteOption.java`:

```java
package com.banhmyking.banhmyking.dto.store;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoreQuoteOption {
    private Long storeId;
    private String storeCode;
    private String storeName;
    private String storeAddress;
    private String storePhone;
    private String openTime;
    private String closeTime;
    private BigDecimal minOrderAmount;
    /** NULL = không tính được khoảng cách (thiếu toạ độ) — phí theo khu vực */
    private BigDecimal distanceKm;
    private BigDecimal shippingFee;
    private BigDecimal originalFee;
    private boolean freeship;
    private String feeDescription;
    private boolean eligible;
    /** Mã lý do: CLOSED, NOT_ACCEPTING, OUT_OF_RADIUS, BELOW_MIN_ORDER, ITEM_UNAVAILABLE */
    private List<String> reasons;
    /** Câu giải thích tiếng Việt theo cùng thứ tự với reasons */
    private List<String> reasonMessages;
    private List<String> unavailableItems;
}
```

`dto/store/DeliveryQuoteResponse.java`:

```java
package com.banhmyking.banhmyking.dto.store;

import java.util.List;

public record DeliveryQuoteResponse(Long recommendedStoreId, List<StoreQuoteOption> options) {
}
```

Thay toàn bộ `controller/DeliveryController.java`:

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.delivery.DeliveryFeeResult;
import com.banhmyking.banhmyking.dto.store.DeliveryQuoteResponse;
import com.banhmyking.banhmyking.dto.store.StoreQuoteOption;
import com.banhmyking.banhmyking.entity.Cart;
import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.repository.CartRepository;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.DeliveryFeeCalculator;
import com.banhmyking.banhmyking.service.PriceCalculator;
import com.banhmyking.banhmyking.service.StoreSelectionService;
import com.banhmyking.banhmyking.service.StoreSelectionService.Candidate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
@Tag(name = "Delivery & Shipping", description = "Báo giá giao hàng theo cơ sở")
public class DeliveryController {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final StoreSelectionService storeSelectionService;
    private final DeliveryFeeCalculator deliveryFeeCalculator;
    private final CartRepository cartRepository;

    @GetMapping("/quote")
    @Transactional(readOnly = true)
    @Operation(summary = "Báo giá giao hàng: cơ sở đề xuất + phí và lý do của từng cơ sở (đọc giỏ hàng phía server)")
    public ResponseEntity<ApiResponse<DeliveryQuoteResponse>> quote(
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) String shippingAddress,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = SecurityUtils.requireUserId(principal);
        Map<Product, Integer> items = new LinkedHashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        Cart cart = cartRepository.findByUserIdWithDetails(userId).orElse(null);
        if (cart != null && cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                items.merge(item.getProduct(), item.getQuantity(), Integer::sum);
                subtotal = subtotal.add(PriceCalculator.lineTotalOf(item));
            }
        }
        List<Candidate> candidates = storeSelectionService.evaluate(latitude, longitude, subtotal, items);
        Long recommended = storeSelectionService.recommend(candidates).map(c -> c.store().getId()).orElse(null);
        BigDecimal cartSubtotal = subtotal;
        List<StoreQuoteOption> options = candidates.stream()
                .map(c -> toOption(c, shippingAddress, cartSubtotal)).toList();
        return ResponseEntity.ok(ApiResponse.ok("Báo giá giao hàng thành công",
                new DeliveryQuoteResponse(recommended, options)));
    }

    private StoreQuoteOption toOption(Candidate c, String address, BigDecimal subtotal) {
        DeliveryFeeResult fee = deliveryFeeCalculator.calculateFee(
                c.distanceKm(), address, subtotal, c.store().getFreeShipRadiusKm());
        return StoreQuoteOption.builder()
                .storeId(c.store().getId()).storeCode(c.store().getCode()).storeName(c.store().getName())
                .storeAddress(c.store().getAddress()).storePhone(c.store().getPhone())
                .openTime(c.store().getOpenTime().format(HH_MM)).closeTime(c.store().getCloseTime().format(HH_MM))
                .minOrderAmount(c.store().getMinOrderAmount())
                .distanceKm(c.distanceKm())
                .shippingFee(fee.getShippingFee()).originalFee(fee.getOriginalFee())
                .freeship(fee.isFreeship()).feeDescription(fee.getDescription())
                .eligible(c.eligible())
                .reasons(c.reasons().stream().map(Enum::name).toList())
                .reasonMessages(c.reasons().stream().map(r -> storeSelectionService.describe(r, c)).toList())
                .unavailableItems(c.unavailableItems())
                .build();
    }
}
```

Xoá `dto/delivery/CalculateDeliveryFeeRequest.java`.

`config/SecurityConfig.java`: đổi `.requestMatchers("/api/v1/delivery/**").permitAll()` thành xoá dòng đó (quote rơi vào `.requestMatchers("/api/v1/**").authenticated()`).

- [ ] **Step 6: Tạo đơn theo cơ sở**

`dto/order/CreateOrderRequest.java` — thêm sau `paymentMethod`:

```java
    /** Cơ sở khách chọn; bỏ trống = server tự chọn cơ sở gần nhất đủ điều kiện (spec §3.2). */
    @Schema(description = "ID cơ sở phục vụ (tuỳ chọn)", example = "1")
    private Long storeId;
```

`dto/order/OrderResponse.java` — thêm sau `shippingAddress`:

```java
    private Long storeId;
    private String storeName;
    private String storePhone;
```

`service/impl/OrderServiceImpl.java`:
- Thay field `storeDistanceService` bằng `private final com.banhmyking.banhmyking.service.StoreSelectionService storeSelectionService;`.
- Trong `createFromCart`, thay khối

```java
        BigDecimal distanceKm = storeDistanceService.roadDistanceKm(deliveryLatitude, deliveryLongitude)
                .orElse(null);
```

bằng (đặt **sau** khối tính `cartSubtotal` ở bước 5 — di chuyển khối tính `cartSubtotal` lên trước):

```java
        // Chọn / kiểm tra cơ sở phục vụ — server chấm lại toàn bộ điều kiện (spec §3.2)
        java.util.Map<Product, Integer> demand = new java.util.LinkedHashMap<>();
        for (CartItem item : cart.getItems()) {
            demand.merge(item.getProduct(), item.getQuantity(), Integer::sum);
        }
        com.banhmyking.banhmyking.service.StoreSelectionService.Candidate chosen = storeSelectionService.requireEligible(
                request.getStoreId(), deliveryLatitude, deliveryLongitude, cartSubtotal, demand);
        BigDecimal distanceKm = chosen.distanceKm();
```

- Đổi lời gọi phí: `deliveryFeeCalculator.calculateFee(distanceKm, shippingAddress, cartSubtotal, chosen.store().getFreeShipRadiusKm());`
- Sau `order.setUser(user);` thêm `order.setStore(chosen.store());`.
- Trong `toOrderResponse` sau `.shippingAddress(...)` thêm:

```java
                .storeId(order.getStore() != null ? order.getStore().getId() : null)
                .storeName(order.getStore() != null ? order.getStore().getName() : null)
                .storePhone(order.getStore() != null ? order.getStore().getPhone() : null)
```

`entity/Order.java`: `@JoinColumn(name = "store_id", nullable = false)` và comment "Cơ sở phục vụ đơn".

- [ ] **Step 7: Bỏ vị trí quán khỏi Cài đặt website + V13**

- Xoá `service/StoreDistanceService.java` và `src/test/java/com/banhmyking/banhmyking/service/StoreDistanceServiceTest.java`.
- `service/SiteSettingKeys.java`: xoá 3 hằng `STORE_LATITUDE`, `STORE_LONGITUDE`, `DELIVERY_MAX_RADIUS_KM` và 3 `Map.entry` tương ứng (dòng cuối `FOOTER_DESCRIPTION` kết thúc bằng `)`);
- `service/impl/SiteSettingServiceImpl.java`: xoá method `validateNumeric`, lời gọi `validateNumeric(key, value);` và khối kiểm tra cặp toạ độ (`Map<String, String> after = ...` tới hết `if`).
- `SiteSettingServiceImplTest.java`: xoá 4 test `updateSettingsRejectsNonNumericStoreLatitude`, `updateSettingsRejectsStoreLatitudeOutsideVietnam`, `updateSettingsRejectsHalfOfStoreCoordinates`, `updateSettingsSavesStoreLocationPair` và import `times` nếu không còn dùng.

`src/main/resources/db/migration/V13__store_not_null_and_cleanup.sql`:

```sql
-- V13: mọi đơn mới luôn có cơ sở (Task 5) → siết NOT NULL.
-- Vị trí quán chuyển sang bảng stores (V11) → bỏ 3 khoá cài đặt cũ.
ALTER TABLE orders MODIFY COLUMN store_id BIGINT NOT NULL;

DELETE FROM site_settings WHERE setting_key IN ('storeLatitude', 'storeLongitude', 'deliveryMaxRadiusKm');
```

- [ ] **Step 8: Cập nhật test đơn hàng**

Trong `OrderServiceTest.java` và `OrderPriceSnapshotTest.java`:
- Thay `@Mock private StoreDistanceService storeDistanceService;` bằng `@Mock private StoreSelectionService storeSelectionService;`.
- Thêm fixture và stub trong `setUp()`:

```java
        testStore = new Store();
        testStore.setId(1L);
        testStore.setName("Cơ sở 1");
        lenient().when(storeSelectionService.requireEligible(any(), any(), any(), any(), anyMap()))
                .thenReturn(new StoreSelectionService.Candidate(testStore, null, List.of(), List.of()));
```

(khai báo `private Store testStore;`, import `Store`, `StoreSelectionService`, `static org.mockito.ArgumentMatchers.anyMap`, `static org.mockito.Mockito.lenient`.)
- Đổi mọi `when(deliveryFeeCalculator.calculateFee(any(), any(), any()))` thành `when(deliveryFeeCalculator.calculateFee(any(), any(), any(), any()))`; mọi `verify(deliveryFeeCalculator).calculateFee(eq(...), any(), any())` thêm `, any()`.
- Viết lại 2 test của Task trước dùng toạ độ:
  - `createFromCart_withPinnedAddress_usesServerDistance`: stub `storeSelectionService.requireEligible(isNull(), eq(lat), eq(lng), any(), anyMap())` trả `Candidate(testStore, new BigDecimal("3.25"), List.of(), List.of())`; khẳng định `verify(deliveryFeeCalculator).calculateFee(eq(new BigDecimal("3.25")), any(), any(), any())`, `response.getDistanceKm()` = 3.25 và `response.getStoreId()` = 1.
  - `createFromCart_outsideDeliveryRadius_isRejected`: stub `requireEligible(...)` `thenThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "ngoài bán kính giao hàng"))`.
- Thêm test:

```java
    @Test
    @DisplayName("Khách chọn cơ sở: storeId được chuyển cho StoreSelectionService và gắn vào đơn")
    void createFromCart_withChosenStore_assignsStore() {
        Store chosenStore = new Store();
        chosenStore.setId(7L);
        chosenStore.setName("Cơ sở 7");
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressId(200L).paymentMethod(PaymentMethod.COD).storeId(7L).build();
        PriceBreakdown breakdown = PriceBreakdown.builder()
                .subtotal(BigDecimal.valueOf(80000)).shippingFee(BigDecimal.valueOf(15000))
                .discountAmount(BigDecimal.ZERO).total(BigDecimal.valueOf(95000)).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(testCart));
        when(addressRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(testAddress));
        when(storeSelectionService.requireEligible(eq(7L), any(), any(), any(), anyMap()))
                .thenReturn(new StoreSelectionService.Candidate(chosenStore, null, List.of(), List.of()));
        when(priceCalculator.calculate(eq(testCart), any(), any())).thenReturn(breakdown);
        when(orderCodeGenerator.generateUniqueCode(any(), anyInt())).thenReturn("BMK-20261001-STORE7");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.createFromCart(1L, request);

        assertThat(response.getStoreId()).isEqualTo(7L);
        assertThat(response.getStoreName()).isEqualTo("Cơ sở 7");
    }
```

- [ ] **Step 9: Chạy, xác nhận xanh**

Run: `./mvnw -B test -Dtest=StoreSelectionServiceTest,DeliveryFeeCalculatorTest,OrderServiceTest,OrderPriceSnapshotTest,SiteSettingServiceImplTest`
Expected: PASS.
Run: `./mvnw -B test` → BUILD SUCCESS; log `version "13 - store not null and cleanup"`.

- [ ] **Step 10: Commit (người dùng tự chạy)**

```
git add -A src/main/java src/test/java src/main/resources/db/migration/V13__store_not_null_and_cleanup.sql
git commit -m "feat(co-so): báo giá theo cơ sở, tự chọn cơ sở gần nhất khi đặt đơn, freeship theo bán kính (V13)"
```

---

## Task 6: Đơn hàng theo phạm vi cơ sở + chuyển đơn giữa cơ sở + MANAGER trong luồng đơn

**Files:**
- Create: `dto/store/TransferStoreRequest.java`
- Modify: `repository/specification/OrderSpecifications.java`, `repository/UserRepository.java`, `service/OrderService.java`, `service/impl/OrderServiceImpl.java`, `service/impl/PaymentServiceImpl.java`, `validator/OrderStatusValidator.java`, `controller/AdminOrderController.java`, `controller/ReviewController.java`, `config/SecurityConfig.java`
- Test: `service/OrderStoreScopeTest.java` (mới); cập nhật `OrderOwnershipTest`, `OrderServiceApisTest`, `OrderServiceStateMachineTest`, `OrderServiceTest`, `PaymentServiceTest`, `controller/AdminOrderControllerTest`

**Interfaces:**
- Consumes: `StoreAccessGuard` (Task 2), `StoreSelectionService` (Task 5), `StoreService.requireActiveStore` (Task 4).
- Produces:
  - `OrderSpecifications.withFilters(OrderStatus status, LocalDateTime from, LocalDateTime to, Long storeId)` (storeId null = mọi cơ sở). Giữ overload 3 tham số gọi sang 4 tham số với `null`.
  - `UserRepository.findByRoleAndStoreIdAndDeletedFalse(RoleName role, Long storeId): List<User>`.
  - `OrderService.getAllOrdersForAdmin(Long userId, OrderStatus status, String from, String to, Long storeId, int page, int size)`.
  - `OrderService.getAvailableShippers(Long userId, Long storeId)`.
  - `OrderService.transferStore(Long userId, String orderCode, TransferStoreRequest request): OrderResponse`.
  - API: `GET /api/v1/admin/orders?storeId=`, `GET /api/v1/admin/orders/shippers/available?storeId=`, `PUT /api/v1/admin/orders/{orderCode}/store` body `{storeId, reason}`.
  - `TransferStoreRequest(@NotNull Long storeId, @NotBlank @Size(max=300) String reason)`.

- [ ] **Step 1: Test phạm vi cơ sở (đỏ)**

`src/test/java/com/banhmyking/banhmyking/service/OrderStoreScopeTest.java`:

```java
package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.dto.order.AssignShipperRequest;
import com.banhmyking.banhmyking.dto.store.TransferStoreRequest;
import com.banhmyking.banhmyking.dto.delivery.DeliveryFeeResult;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.Payment;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.PaymentStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.OrderStatusHistoryRepository;
import com.banhmyking.banhmyking.repository.PaymentRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.StoreAccessGuard;
import com.banhmyking.banhmyking.service.impl.OrderServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderStoreScopeTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock private StoreSelectionService storeSelectionService;
    @Mock private StoreService storeService;
    @Mock private DeliveryFeeCalculator deliveryFeeCalculator;
    @Spy private StoreAccessGuard storeAccessGuard = new StoreAccessGuard();
    @InjectMocks private OrderServiceImpl orderService;

    private Store storeA;
    private Store storeB;
    private User managerA;
    private Order orderB;

    @BeforeEach
    void setUp() {
        storeA = new Store();
        storeA.setId(1L);
        storeA.setName("Cơ sở A");
        storeB = new Store();
        storeB.setId(2L);
        storeB.setName("Cơ sở B");
        managerA = new User();
        managerA.setId(10L);
        managerA.setRole(RoleName.MANAGER);
        managerA.setStore(storeA);
        orderB = new Order();
        orderB.setId(500L);
        orderB.setOrderCode("BMK-B");
        orderB.setStore(storeB);
        orderB.setStatus(OrderStatus.PENDING);
        when(userRepository.findById(10L)).thenReturn(Optional.of(managerA));
    }

    @Test
    void managerCannotAssignShipperOnOtherStoreOrder() {
        when(orderRepository.findByOrderCode("BMK-B")).thenReturn(Optional.of(orderB));
        AssignShipperRequest request = new AssignShipperRequest();
        request.setShipperId(20L);

        assertThatThrownBy(() -> orderService.assignShipper(10L, "BMK-B", request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shipperOfAnotherStoreCannotBeAssigned() {
        Order orderA = new Order();
        orderA.setId(501L);
        orderA.setOrderCode("BMK-A");
        orderA.setStore(storeA);
        orderA.setStatus(OrderStatus.READY_FOR_PICKUP);
        User shipperB = new User();
        shipperB.setId(20L);
        shipperB.setRole(RoleName.SHIPPER);
        shipperB.setStore(storeB);
        when(orderRepository.findByOrderCode("BMK-A")).thenReturn(Optional.of(orderA));
        when(userRepository.findById(20L)).thenReturn(Optional.of(shipperB));
        AssignShipperRequest request = new AssignShipperRequest();
        request.setShipperId(20L);

        assertThatThrownBy(() -> orderService.assignShipper(10L, "BMK-A", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không thuộc cơ sở");
    }

    @Test
    void managerListIsLockedToOwnStore() {
        assertThatThrownBy(() -> orderService.getAllOrdersForAdmin(10L, null, null, null, 2L, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void transferMovesPendingOrderAndKeepsTotalWhenUnpaid() {
        Order orderA = new Order();
        orderA.setId(502L);
        orderA.setOrderCode("BMK-T");
        orderA.setStore(storeA);
        orderA.setStatus(OrderStatus.PENDING);
        orderA.setShippingAddress("12 Láng Hạ");
        orderA.setSubtotal(new BigDecimal("60000"));
        orderA.setShippingFee(new BigDecimal("15000"));
        orderA.setDiscountAmount(BigDecimal.ZERO);
        orderA.setTotal(new BigDecimal("75000"));
        OrderItem item = new OrderItem();
        item.setProduct(new Product());
        item.setQuantity(1);
        orderA.setItems(List.of(item));
        when(orderRepository.findByOrderCode("BMK-T")).thenReturn(Optional.of(orderA));
        when(storeSelectionService.requireEligible(eq(2L), any(), any(), any(), anyMap()))
                .thenReturn(new StoreSelectionService.Candidate(storeB, null, List.of(), List.of()));
        when(deliveryFeeCalculator.calculateFee(any(), any(), any(), any()))
                .thenReturn(DeliveryFeeResult.builder().shippingFee(new BigDecimal("20000")).build());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = orderService.transferStore(10L, "BMK-T", new TransferStoreRequest(2L, "Hết bánh"));

        assertThat(response.getStoreId()).isEqualTo(2L);
        assertThat(orderA.getShippingFee()).isEqualByComparingTo("20000");
        assertThat(orderA.getTotal()).isEqualByComparingTo("80000");
    }

    @Test
    void transferRefusedWhenPaidAndTotalWouldChange() {
        Order paid = new Order();
        paid.setId(503L);
        paid.setOrderCode("BMK-P");
        paid.setStore(storeA);
        paid.setStatus(OrderStatus.PENDING);
        paid.setSubtotal(new BigDecimal("60000"));
        paid.setShippingFee(new BigDecimal("15000"));
        paid.setDiscountAmount(BigDecimal.ZERO);
        paid.setTotal(new BigDecimal("75000"));
        paid.setItems(List.of());
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.PAID);
        paid.setPayment(payment);
        when(orderRepository.findByOrderCode("BMK-P")).thenReturn(Optional.of(paid));
        when(storeSelectionService.requireEligible(eq(2L), any(), any(), any(), anyMap()))
                .thenReturn(new StoreSelectionService.Candidate(storeB, null, List.of(), List.of()));
        when(deliveryFeeCalculator.calculateFee(any(), any(), any(), any()))
                .thenReturn(DeliveryFeeResult.builder().shippingFee(new BigDecimal("30000")).build());

        assertThatThrownBy(() -> orderService.transferStore(10L, "BMK-P", new TransferStoreRequest(2L, "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã thanh toán");
    }

    @Test
    void transferOnlyFromPending() {
        Order confirmed = new Order();
        confirmed.setOrderCode("BMK-C");
        confirmed.setStore(storeA);
        confirmed.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByOrderCode("BMK-C")).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> orderService.transferStore(10L, "BMK-C", new TransferStoreRequest(2L, "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PENDING");
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ**

Run: `./mvnw -B test -Dtest=OrderStoreScopeTest`
Expected: FAIL biên dịch (`transferStore`, `getAllOrdersForAdmin(...7 tham số)` chưa có).

- [ ] **Step 3: DTO + repository + specification**

`dto/store/TransferStoreRequest.java`:

```java
package com.banhmyking.banhmyking.dto.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferStoreRequest(
        @NotNull(message = "Chưa chọn cơ sở đích") Long storeId,
        @NotBlank(message = "Vui lòng nhập lý do chuyển cơ sở") @Size(max = 300) String reason) {
}
```

`UserRepository`: thêm `java.util.List<User> findByRoleAndStoreIdAndDeletedFalse(RoleName role, Long storeId);`

`OrderSpecifications`: thay `withFilters` bằng:

```java
    public static Specification<Order> withFilters(OrderStatus status, LocalDateTime fromDate, LocalDateTime toDate) {
        return withFilters(status, fromDate, toDate, null);
    }

    /** Staff/Manager/Admin lọc đơn; storeId null = mọi cơ sở (chỉ ADMIN được truyền null — xem StoreAccessGuard). */
    public static Specification<Order> withFilters(OrderStatus status, LocalDateTime fromDate, LocalDateTime toDate,
                                                   Long storeId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }
            if (storeId != null) {
                predicates.add(cb.equal(root.get("store").get("id"), storeId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
```

- [ ] **Step 4: `OrderService` + `OrderServiceImpl`**

`service/OrderService.java`: đổi chữ ký 2 method và thêm 1 method:

```java
    PageResponse<OrderResponse> getAllOrdersForAdmin(Long userId, OrderStatus status, String fromDate, String toDate,
                                                     Long storeId, int page, int size);

    java.util.List<com.banhmyking.banhmyking.dto.order.ShipperAvailabilityResponse> getAvailableShippers(Long userId, Long storeId);

    OrderResponse transferStore(Long userId, String orderCode, com.banhmyking.banhmyking.dto.store.TransferStoreRequest request);
```

`service/impl/OrderServiceImpl.java` — thêm field:

```java
    private final com.banhmyking.banhmyking.security.StoreAccessGuard storeAccessGuard;
    private final com.banhmyking.banhmyking.service.StoreService storeService;
```

Thay các kiểm tra vai trò (đúng các vị trí sau, giữ nguyên phần còn lại):

1. `getOrderByCode`: sau 2 khối kiểm tra CUSTOMER/SHIPPER thêm `storeAccessGuard.requireOrderAccess(actor, order);`.
2. `getAllOrdersForAdmin` (chữ ký mới thêm `Long storeId` trước `page`): thay khối `if (actor.getRole() != STAFF && != ADMIN) throw ...` bằng `storeAccessGuard.requireOperator(actor);`, thêm `Long scope = storeAccessGuard.resolveStoreFilter(actor, storeId);` và dùng `OrderSpecifications.withFilters(status, fromDate, toDate, scope)`.
3. `assignShipper`: thay khối kiểm vai trò bằng `storeAccessGuard.requireOperator(actor);`; sau `Order order = requireOrderByCode(orderCode);` thêm `storeAccessGuard.requireOrderAccess(actor, order);`; sau `User shipper = requireShipper(...)` thêm:

```java
        if (!storeAccessGuard.sameStore(shipper, order.getStore())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Tài xế " + shipper.getFullName() + " không thuộc cơ sở phục vụ đơn này");
        }
```

4. `getOrdersForShipper`: thay khối kiểm vai trò bằng
   `if (actor.getRole() != RoleName.SHIPPER) { storeAccessGuard.requireOperator(actor); }`
   và nhánh không phải shipper dùng `OrderSpecifications.withFilters(status, null, null, storeAccessGuard.scopedStoreId(actor))`.
5. `confirmDelivery`: thay khối kiểm vai trò bằng
   `if (actor.getRole() != RoleName.SHIPPER) { storeAccessGuard.requireOperator(actor); }`;
   sau `Order order = requireOrderByCode(orderCode);` thêm `storeAccessGuard.requireOrderAccess(actor, order);`.
6. `rejectAssignedOrder`: giữ nguyên (chỉ shipper được gán mới từ chối được — kiểm `order.getShipper()`).
7. `cancelOrder`, `refundOrder`, `updateOrderStatus`, `getOrderStatusHistory`: ngay sau dòng lấy `order` thêm `storeAccessGuard.requireOrderAccess(actor, order);`. Trong `refundOrder` thay khối `if (actor.getRole() != STAFF && != ADMIN)` bằng `storeAccessGuard.requireOperator(actor);`.
8. `getAvailableShippers(Long userId, Long storeId)`: thay khối kiểm vai trò bằng `storeAccessGuard.requireOperator(actor);` và

```java
        Long scope = storeAccessGuard.resolveStoreFilter(actor, storeId);
        java.util.List<User> shippers = scope == null
                ? userRepository.findByRoleAndDeletedFalse(RoleName.SHIPPER)
                : userRepository.findByRoleAndStoreIdAndDeletedFalse(RoleName.SHIPPER, scope);
```

9. Method mới (đặt sau `refundOrder`):

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse transferStore(Long userId, String orderCode,
                                       com.banhmyking.banhmyking.dto.store.TransferStoreRequest request) {
        User actor = requireUser(userId);
        if (actor.getRole() != RoleName.ADMIN && actor.getRole() != RoleName.MANAGER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ quản lý cơ sở hoặc quản trị viên mới được chuyển cơ sở");
        }
        Order order = requireOrderByCode(orderCode);
        storeAccessGuard.requireOrderAccess(actor, order);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Chỉ chuyển cơ sở được khi đơn còn PENDING. Trạng thái hiện tại: " + order.getStatus());
        }
        if (order.getStore() != null && order.getStore().getId().equals(request.storeId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Đơn đã thuộc cơ sở này");
        }

        java.util.Map<Product, Integer> demand = new java.util.LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            demand.merge(item.getProduct(), item.getQuantity(), Integer::sum);
        }
        com.banhmyking.banhmyking.service.StoreSelectionService.Candidate target = storeSelectionService.requireEligible(
                request.storeId(), order.getDeliveryLatitude(), order.getDeliveryLongitude(), order.getSubtotal(), demand);
        DeliveryFeeResult fee = deliveryFeeCalculator.calculateFee(target.distanceKm(), order.getShippingAddress(),
                order.getSubtotal(), target.store().getFreeShipRadiusKm());
        BigDecimal newFee = fee.getShippingFee();
        BigDecimal newTotal = order.getSubtotal().add(newFee).subtract(order.getDiscountAmount()).max(BigDecimal.ZERO);

        Payment payment = order.getPayment();
        if (payment == null && order.getId() != null) {
            payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        }
        boolean paid = payment != null && payment.getStatus() == PaymentStatus.PAID;
        if (paid && newTotal.compareTo(order.getTotal()) != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Đơn đã thanh toán — không thể chuyển sang cơ sở làm thay đổi tổng tiền");
        }

        String fromName = order.getStore() == null ? "?" : order.getStore().getName();
        order.setStore(target.store());
        order.setDistanceKm(target.distanceKm());
        order.setShippingFee(newFee);
        order.setTotal(newTotal);
        if (payment != null && !paid) {
            payment.setAmount(newTotal);
            paymentRepository.save(payment);
        }
        Order saved = orderRepository.save(order);
        recordHistory(saved, OrderStatus.PENDING, OrderStatus.PENDING, actor,
                "Chuyển từ " + fromName + " sang " + target.store().getName() + ": " + request.reason().trim());
        return toOrderResponse(saved);
    }
```

- [ ] **Step 5: Thanh toán, validator, controller, security**

- `PaymentServiceImpl`: field mới `private final com.banhmyking.banhmyking.security.StoreAccessGuard storeAccessGuard;`. Trong `processPayment` đổi
  `boolean canReconcileManually = actor.getRole() == RoleName.STAFF || actor.getRole() == RoleName.ADMIN;`
  thành
  `boolean canReconcileManually = storeAccessGuard.isOperator(actor);`
  và ngay trong nhánh `if (canReconcileManually)` (trước khi set PAID) thêm `storeAccessGuard.requireOrderAccess(actor, order);`. Các kiểm tra khác dùng `role == STAFF || role == ADMIN` trong file này (dòng ~99, ~103): thay bằng `storeAccessGuard.isOperator(actor)`.
- `validator/OrderStatusValidator.java`: trong `validateCancel` đổi `} else if (role == RoleName.STAFF || role == RoleName.ADMIN) {` thành `} else if (role == RoleName.STAFF || role == RoleName.MANAGER || role == RoleName.ADMIN) {`.
- `controller/AdminOrderController.java`:
  - GET list: thêm `@RequestParam(required = false) Long storeId` và truyền `storeId` vào `getAllOrdersForAdmin(userId, status, fromDate, toDate, storeId, page, size)`.
  - GET `/shippers/available`: thêm `@RequestParam(required = false) Long storeId`, gọi `getAvailableShippers(userId, storeId)`.
  - Endpoint mới:

```java
    @PutMapping("/{orderCode}/store")
    @Operation(summary = "Chuyển đơn PENDING sang cơ sở khác (MANAGER cơ sở hiện tại / ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> transferStore(
            @PathVariable String orderCode,
            @Valid @RequestBody com.banhmyking.banhmyking.dto.store.TransferStoreRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Chuyển cơ sở thành công",
                orderService.transferStore(SecurityUtils.requireUserId(principal), orderCode, request)));
    }
```

- `controller/ReviewController.java`: `@PreAuthorize("hasAnyRole('STAFF','ADMIN')")` → `@PreAuthorize("hasAnyRole('STAFF','MANAGER','ADMIN')")`.
- `config/SecurityConfig.java`:
  - `.requestMatchers("/api/v1/admin/orders/**").hasAnyRole("STAFF", "ADMIN")` → `.hasAnyRole("STAFF", "MANAGER", "ADMIN")`
  - `.requestMatchers("/api/v1/shipper/**").hasAnyRole("SHIPPER", "STAFF", "ADMIN")` → `.hasAnyRole("SHIPPER", "STAFF", "MANAGER", "ADMIN")`

- [ ] **Step 6: Cập nhật test cũ**

- Mọi class test có `@InjectMocks OrderServiceImpl` (`OrderServiceTest`, `OrderPriceSnapshotTest`, `OrderOwnershipTest`, `OrderServiceApisTest`, `OrderServiceStateMachineTest`) thêm:

```java
    @Spy
    private StoreAccessGuard storeAccessGuard = new StoreAccessGuard();

    @Mock
    private StoreService storeService;
```

  (import `org.mockito.Spy`, `com.banhmyking.banhmyking.security.StoreAccessGuard`.)
- Fixture STAFF/ADMIN/SHIPPER trong các test này: gán cùng một `Store` cho STAFF, SHIPPER và cho các `Order` mẫu (vd `Store store = new Store(); store.setId(1L);` → `staff.setStore(store); shipper.setStore(store); order.setStore(store);`) để kiểm tra phạm vi không chặn các kịch bản cũ.
- Gọi `getAllOrdersForAdmin(..., page, size)` → thêm đối số `null` (storeId) trước `page`; `getAvailableShippers(userId)` → `getAvailableShippers(userId, null)`.
- `OrderOwnershipTest` khởi tạo `new PaymentServiceImpl(paymentRepository, orderRepository, userRepository, orderStatusHistoryRepository, inventoryService)` → thêm đối số cuối `storeAccessGuard`.
- `PaymentServiceTest`: thêm `@Spy private StoreAccessGuard storeAccessGuard = new StoreAccessGuard();` và gán `staff.setStore(store)` + `testOrder.setStore(store)` cùng một `Store`.
- `controller/AdminOrderControllerTest`: các `when(orderService.getAllOrdersForAdmin(...))` / `verify` thêm đối số `any()`/`isNull()` cho `storeId`; `getAvailableShippers(anyLong())` → `getAvailableShippers(anyLong(), any())`.

- [ ] **Step 7: Chạy, xác nhận xanh**

Run: `./mvnw -B test -Dtest=OrderStoreScopeTest`
Expected: PASS (6 test).
Run: `./mvnw -B test` → BUILD SUCCESS.

- [ ] **Step 8: Commit (người dùng tự chạy)**

```
git add -A src/main/java src/test/java
git commit -m "feat(co-so): khoá đơn hàng theo cơ sở, chuyển đơn giữa cơ sở, MANAGER trong luồng đơn"
```

---

## Task 7: Tài khoản theo cơ sở — gán cơ sở, lọc, Manager xem nhân viên

**Files:**
- Modify: `dto/user/AdminCreateUserRequest.java`, `dto/user/AdminUpdateUserRequest.java`, `dto/user/UpdateRoleRequest.java`, `dto/user/UserDetailResponse.java`, `repository/UserRepository.java`, `service/UserService.java`, `service/impl/UserServiceImpl.java`, `controller/AdminUserController.java`, `controller/ManagerController.java`
- Test: `service/UserServiceImplTest.java`; cập nhật `controller/AdminUserControllerTest.java`, `controller/UserControllerTest.java`

**Interfaces:**
- Consumes: `StoreRepository` (Task 1), `StoreAccessGuard` (Task 2).
- Produces:
  - `AdminCreateUserRequest(..., RoleName role, Long storeId)`; `AdminUpdateUserRequest(..., String password, Long storeId)`; `UpdateRoleRequest(RoleName role, Long storeId)`.
  - `UserDetailResponse(..., LocalDateTime createdAt, Long storeId, String storeName)` (2 thành phần mới ở cuối). Cũng là dữ liệu `/auth/me` → frontend biết cơ sở làm việc.
  - `UserService.getUsers(RoleName role, Boolean banned, String keyword, Long storeId, int page, int size)`.
  - `UserService.getStoreStaff(Long actorId): List<UserDetailResponse>` — MANAGER: nhân sự cơ sở mình; ADMIN: lỗi 400 "Dùng trang Tài khoản".
  - `GET /api/v1/admin/users?storeId=`; `GET /api/v1/manager/staff`.
  - Quy tắc (spec §4): STAFF/SHIPPER/MANAGER bắt buộc `storeId` (cơ sở tồn tại, chưa xoá); CUSTOMER/ADMIN → `store = null`; đổi cơ sở hoặc vai trò → thu hồi refresh token.

- [ ] **Step 1: Test (đỏ)** — thêm vào `UserServiceImplTest.java`:

```java
    @Test
    void createStaffRequiresStore() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "staff9@banhmyking.vn", "12345678", "Nhân viên 9", null, RoleName.STAFF, null);
        when(userRepository.existsByEmail("staff9@banhmyking.vn")).thenReturn(false);

        assertThatThrownBy(() -> userService.createUser(ACTOR_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chọn cơ sở");
    }

    @Test
    void createManagerAssignsStore() {
        Store store = new Store();
        store.setId(3L);
        store.setName("Cơ sở 3");
        when(storeRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(store));
        when(userRepository.existsByEmail("ql3@banhmyking.vn")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDetailResponse res = userService.createUser(ACTOR_ID, new AdminCreateUserRequest(
                "ql3@banhmyking.vn", "12345678", "Quản lý 3", null, RoleName.MANAGER, 3L));

        assertThat(res.storeId()).isEqualTo(3L);
        assertThat(res.storeName()).isEqualTo("Cơ sở 3");
    }

    @Test
    void customerNeverKeepsStore() {
        when(storeRepository.findByIdAndDeletedFalse(any())).thenReturn(Optional.of(new Store()));
        when(userRepository.existsByEmail("kh@x.vn")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDetailResponse res = userService.createUser(ACTOR_ID, new AdminCreateUserRequest(
                "kh@x.vn", "12345678", "Khách", null, RoleName.CUSTOMER, 3L));

        assertThat(res.storeId()).isNull();
    }

    @Test
    void changingStoreRevokesSessions() {
        Store oldStore = new Store();
        oldStore.setId(1L);
        Store newStore = new Store();
        newStore.setId(2L);
        User staff = buildUser(TARGET_ID, RoleName.STAFF, false);
        staff.setStore(oldStore);
        when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(staff));
        when(storeRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(newStore));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser(ACTOR_ID, TARGET_ID,
                new AdminUpdateUserRequest(null, null, null, null, null, 2L));

        assertThat(staff.getStore().getId()).isEqualTo(2L);
        verify(refreshTokenRepository).findByUserIdAndRevokedAtIsNull(TARGET_ID);
    }
```

(Helper sẵn có trong file: `buildUser(Long id, RoleName role, boolean banned)`. Thêm `@Mock StoreRepository storeRepository;` và import `Store`, `StoreRepository`, `AdminCreateUserRequest`, `AdminUpdateUserRequest`.)

- [ ] **Step 2: Chạy, xác nhận đỏ**

Run: `./mvnw -B test -Dtest=UserServiceImplTest`
Expected: FAIL biên dịch (constructor record có 6 thành phần).

- [ ] **Step 3: DTO**

- `AdminCreateUserRequest`: thêm thành phần cuối
  `@Schema(description = "Cơ sở làm việc — bắt buộc với STAFF/SHIPPER/MANAGER", example = "1") Long storeId`
  và sửa mô tả `role` thành `"(STAFF, SHIPPER, MANAGER, CUSTOMER, ADMIN)"`.
- `AdminUpdateUserRequest`: thêm thành phần cuối `@Schema(description = "Đổi cơ sở làm việc (STAFF/SHIPPER/MANAGER)", example = "2") Long storeId`.
- `UpdateRoleRequest`: thêm thành phần `@Schema(description = "Cơ sở — bắt buộc khi vai trò mới là STAFF/SHIPPER/MANAGER") Long storeId`.
- `UserDetailResponse`: thêm 2 thành phần cuối `@Schema(description = "ID cơ sở làm việc") Long storeId, @Schema(description = "Tên cơ sở làm việc") String storeName`.

- [ ] **Step 4: Repository**

Thay query `searchUsers` trong `UserRepository`:

```java
    @Query("""
            SELECT u FROM User u
            WHERE u.deleted = false
              AND (:role IS NULL OR u.role = :role)
              AND (:banned IS NULL OR u.banned = :banned)
              AND (:storeId IS NULL OR u.store.id = :storeId)
              AND (:keyword IS NULL
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<User> searchUsers(@Param("role") RoleName role,
                           @Param("banned") Boolean banned,
                           @Param("keyword") String keyword,
                           @Param("storeId") Long storeId,
                           Pageable pageable);

    List<User> findByStoreIdAndDeletedFalseOrderByRoleAscFullNameAsc(Long storeId);
```

- [ ] **Step 5: `UserServiceImpl`**

- Field mới: `private final StoreRepository storeRepository;` (+ import `Store`, `StoreRepository`).
- Hằng: `private static final java.util.Set<RoleName> STORE_ROLES = java.util.EnumSet.of(RoleName.STAFF, RoleName.SHIPPER, RoleName.MANAGER);`
- Helper:

```java
    /** STAFF/SHIPPER/MANAGER bắt buộc thuộc một cơ sở; vai trò khác luôn không thuộc cơ sở nào (spec §4). */
    private Store resolveStore(RoleName role, Long storeId) {
        if (!STORE_ROLES.contains(role)) {
            return null;
        }
        if (storeId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn cơ sở làm việc cho vai trò " + role);
        }
        return storeRepository.findByIdAndDeletedFalse(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy cơ sở với ID: " + storeId));
    }

    private static Long storeIdOf(User user) {
        return user.getStore() == null ? null : user.getStore().getId();
    }
```

- `createUser`: sau `user.setRole(...)` thêm `user.setStore(resolveStore(user.getRole(), request.storeId()));`.
- `updateUser`: đổi khối vai trò + thêm khối cơ sở:

```java
        RoleName newRole = request.role() != null ? request.role() : target.getRole();
        Long newStoreId = request.storeId() != null ? request.storeId() : storeIdOf(target);
        boolean roleChanged = newRole != target.getRole();
        if (roleChanged) {
            if (targetId.equals(actorId)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Không thể thay đổi vai trò của chính mình");
            }
            if (target.getRole() == RoleName.ADMIN && countActiveAdmins() <= 1) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Không thể thay đổi vai trò của ADMIN cuối cùng");
            }
        }
        Store newStore = resolveStore(newRole, newStoreId);
        boolean storeChanged = !java.util.Objects.equals(newStore == null ? null : newStore.getId(), storeIdOf(target));
        target.setRole(newRole);
        target.setStore(newStore);
        if (roleChanged || storeChanged) {
            revokeRefreshTokens(targetId);
        }
```

  (khối `if (request.role() != null && request.role() != target.getRole()) {...}` cũ bị thay hoàn toàn bởi đoạn trên.)
- `changeRole`: sau kiểm tra ADMIN cuối cùng thêm `target.setStore(resolveStore(request.role(), request.storeId() != null ? request.storeId() : storeIdOf(target)));` trước `target.setRole(...)`.
- `getUsers(...)`: thêm tham số `Long storeId`, truyền vào `searchUsers(role, banned, keyword, storeId, pageable)`.
- Method mới:

```java
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<UserDetailResponse> getStoreStaff(Long actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy user"));
        if (actor.getRole() != RoleName.MANAGER || actor.getStore() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Quản trị viên xem nhân sự ở trang Tài khoản");
        }
        return userRepository.findByStoreIdAndDeletedFalseOrderByRoleAscFullNameAsc(actor.getStore().getId())
                .stream().map(this::toDetail).toList();
    }
```

- `toDetail`:

```java
    private UserDetailResponse toDetail(User user) {
        return new UserDetailResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getImage(), user.getRole(), user.isBanned(), user.getCreatedAt(),
                user.getStore() == null ? null : user.getStore().getId(),
                user.getStore() == null ? null : user.getStore().getName());
    }
```

- `service/UserService.java`: cập nhật chữ ký `getUsers` và thêm `List<UserDetailResponse> getStoreStaff(Long actorId);`. Nơi khác dựng `UserDetailResponse` (grep `new UserDetailResponse(` trong `src/main`) thêm `null, null` hoặc store tương ứng.
- `AdminUserController` GET list: thêm `@RequestParam(required = false) Long storeId` và truyền vào service.
- `ManagerController`: thêm field `private final UserService userService;` và

```java
    @GetMapping("/staff")
    @Operation(summary = "Nhân sự của cơ sở mình (chỉ xem)")
    public ResponseEntity<ApiResponse<java.util.List<com.banhmyking.banhmyking.dto.user.UserDetailResponse>>> staff(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy nhân sự cơ sở thành công",
                userService.getStoreStaff(SecurityUtils.requireUserId(principal))));
    }
```

- [ ] **Step 6: Cập nhật test controller**

`AdminUserControllerTest.java`, `UserControllerTest.java`: mọi `new UserDetailResponse(...8 đối số...)` thêm `, null, null`; mọi `new AdminCreateUserRequest(...)`/`new AdminUpdateUserRequest(...)` thêm `, null` (hoặc `, 1L` cho STAFF/SHIPPER và stub service tương ứng); `when(userService.getUsers(any(), any(), any(), anyInt(), anyInt()))` → thêm một `any()` cho `storeId`.

- [ ] **Step 7: Chạy, xác nhận xanh**

Run: `./mvnw -B test -Dtest=UserServiceImplTest,AdminUserControllerTest,UserControllerTest`
Expected: PASS. `./mvnw -B test` → BUILD SUCCESS.

- [ ] **Step 8: Commit (người dùng tự chạy)**

```
git add -A src/main/java src/test/java
git commit -m "feat(co-so): gán cơ sở cho nhân viên/shipper/quản lý, lọc tài khoản theo cơ sở, Manager xem nhân sự"
```

---

## Task 8: Báo cáo & dashboard theo cơ sở + doanh thu theo cơ sở

**Files:**
- Create: `dto/report/StoreRevenueResponse.java`
- Modify: `repository/OrderRepository.java`, `repository/OrderItemRepository.java`, `enums/ReportType.java`, `service/AdminDashboardService.java`, `service/impl/AdminDashboardServiceImpl.java`, `service/AdminReportService.java`, `service/impl/AdminReportServiceImpl.java`, `controller/AdminDashboardController.java`, `controller/AdminReportController.java`, `controller/ManagerController.java`
- Test: `service/AdminReportServiceStoreTest.java` (mới, `@SpringBootTest @Transactional` trên DB dev); cập nhật `controller/AdminDashboardControllerTest`, `controller/AdminReportControllerTest`

**Interfaces:**
- Produces:
  - `OrderRepository`: `BigDecimal sumRevenue(Long storeId, LocalDateTime since)`, `long countOrders(Long storeId, LocalDateTime since)`, `long countByStatuses(Long storeId, List<OrderStatus> statuses)`, `List<Order> findForChart(Long storeId, LocalDateTime from)`, `List<Order> findInRange(Long storeId, LocalDateTime from, LocalDateTime to)`, `List<ShipperRevenueResponse> findRevenueByShipper(LocalDateTime from, LocalDateTime to, Long storeId)`, `List<StoreRevenueResponse> findRevenueByStore(LocalDateTime from, LocalDateTime to)`. (`storeId`/`since` null = không lọc.)
  - `OrderItemRepository.findTopProducts(from, to, storeId, pageable)`, `findRevenueByCategory(from, to, storeId)`.
  - `AdminDashboardService.getDashboardMetrics(Long storeId)`, `getDailyRevenueChart(int days, Long storeId)`, `getOrderStatusStats(Long storeId)`.
  - `AdminReportService.getTopProducts(from, to, limit, storeId)`, `exportCsv(type, from, to, limit, storeId)`, `getRevenueByStore(from, to)`.
  - `ReportType.REVENUE_BY_STORE`.
  - API: `?storeId=` trên mọi endpoint `/admin/dashboard/*`, `/admin/reports/*`; `GET /api/v1/admin/reports/revenue-by-store`; `GET /api/v1/manager/dashboard/metrics`, `GET /api/v1/manager/dashboard/revenue-chart?days=`, `GET /api/v1/manager/reports/top-products?fromDate&toDate&limit` (khoá về cơ sở của MANAGER qua `StoreAccessGuard.scopedStoreId`).
  - `StoreRevenueResponse(Long storeId, String storeName, Long orderCount, BigDecimal revenue)` (class Lombok `@Getter @AllArgsConstructor` như `ShipperRevenueResponse`).

- [ ] **Step 1: Test tích hợp (đỏ)**

`src/test/java/com/banhmyking/banhmyking/service/AdminReportServiceStoreTest.java`:

```java
package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.banhmyking.banhmyking.dto.dashboard.DashboardMetricsResponse;
import com.banhmyking.banhmyking.dto.report.StoreRevenueResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AdminReportServiceStoreTest {

    @Autowired private AdminDashboardService dashboardService;
    @Autowired private AdminReportService reportService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void storeFilteredTotalsAddUpToChainTotal() {
        List<Long> storeIds = jdbcTemplate.queryForList("SELECT id FROM stores WHERE is_deleted = FALSE", Long.class);
        DashboardMetricsResponse chain = dashboardService.getDashboardMetrics(null);
        long sum = storeIds.stream().mapToLong(id -> dashboardService.getDashboardMetrics(id).getTotalOrders()).sum();
        assertThat(sum).isEqualTo(chain.getTotalOrders());
    }

    @Test
    void revenueByStoreListsStoresWithRevenue() {
        List<StoreRevenueResponse> rows = reportService.getRevenueByStore(LocalDate.now().minusYears(5), LocalDate.now());
        assertThat(rows).allSatisfy(r -> assertThat(r.getStoreName()).isNotBlank());
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ**

Run: `./mvnw -B test -Dtest=AdminReportServiceStoreTest`
Expected: FAIL biên dịch.

- [ ] **Step 3: Repository**

`OrderRepository` — thêm (giữ các method cũ đang được nơi khác dùng; xoá `sumTotalRevenue`, `sumRevenueSince`, `countByCreatedAtGreaterThanEqual`, `countByStatus`, `countByStatusIn`, `findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc`, `findByCreatedAtGreaterThanEqualAndCreatedAtLessThan` **chỉ khi** grep xác nhận không còn nơi dùng):

```java
    String REVENUE_STATUS = "o.status NOT IN (com.banhmyking.banhmyking.enums.OrderStatus.CANCELLED, "
            + "com.banhmyking.banhmyking.enums.OrderStatus.FAILED)";

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE " + REVENUE_STATUS
            + " AND (:storeId IS NULL OR o.store.id = :storeId) AND (:since IS NULL OR o.createdAt >= :since)")
    java.math.BigDecimal sumRevenue(@Param("storeId") Long storeId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT COUNT(o) FROM Order o WHERE (:storeId IS NULL OR o.store.id = :storeId) "
            + "AND (:since IS NULL OR o.createdAt >= :since)")
    long countOrders(@Param("storeId") Long storeId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT COUNT(o) FROM Order o WHERE (:storeId IS NULL OR o.store.id = :storeId) AND o.status IN :statuses")
    long countByStatuses(@Param("storeId") Long storeId, @Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT o FROM Order o WHERE (:storeId IS NULL OR o.store.id = :storeId) AND o.createdAt >= :from "
            + "ORDER BY o.createdAt ASC")
    List<Order> findForChart(@Param("storeId") Long storeId, @Param("from") java.time.LocalDateTime from);

    @Query("SELECT o FROM Order o WHERE (:storeId IS NULL OR o.store.id = :storeId) "
            + "AND o.createdAt >= :from AND o.createdAt < :to")
    List<Order> findInRange(@Param("storeId") Long storeId, @Param("from") java.time.LocalDateTime from,
                            @Param("to") java.time.LocalDateTime to);

    @Query("SELECT new com.banhmyking.banhmyking.dto.report.StoreRevenueResponse("
            + "s.id, s.name, COUNT(o.id), SUM(o.total)) "
            + "FROM Order o JOIN o.store s WHERE " + REVENUE_STATUS
            + " AND o.createdAt >= :from AND o.createdAt < :to GROUP BY s.id, s.name ORDER BY SUM(o.total) DESC")
    List<com.banhmyking.banhmyking.dto.report.StoreRevenueResponse> findRevenueByStore(
            @Param("from") java.time.LocalDateTime from, @Param("to") java.time.LocalDateTime to);
```

Sửa `findRevenueByShipper` thêm tham số cuối `@Param("storeId") Long storeId` và điều kiện `AND (:storeId IS NULL OR o.store.id = :storeId)`.

`OrderItemRepository`: `findTopProducts` và `findRevenueByCategory` thêm điều kiện `AND (:storeId IS NULL OR oi.order.store.id = :storeId)` và tham số `@Param("storeId") Long storeId` (đặt trước `Pageable` ở `findTopProducts`).

`dto/report/StoreRevenueResponse.java`:

```java
package com.banhmyking.banhmyking.dto.report;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StoreRevenueResponse {
    private Long storeId;
    private String storeName;
    private Long orderCount;
    private BigDecimal revenue;
}
```

`enums/ReportType.java`: thêm `REVENUE_BY_STORE`.

- [ ] **Step 4: Service + controller**

`AdminDashboardServiceImpl`:
- `getDashboardMetrics(Long storeId)`: dùng `sumRevenue(storeId, null)`, `sumRevenue(storeId, startOfToday)`, `countOrders(storeId, null)`, `countOrders(storeId, startOfToday)`, `countByStatuses(storeId, List.of(PENDING))`, `countByStatuses(storeId, List.of(CONFIRMED, PREPARING, READY_FOR_PICKUP, DELIVERING))`, `countByStatuses(storeId, List.of(DELIVERED))`, `countByStatuses(storeId, List.of(CANCELLED, FAILED))`; số người dùng giữ toàn hệ thống khi `storeId == null`, còn khi có `storeId` thì `staffCount`/`shipperCount` đếm `userRepository.findByStoreIdAndDeletedFalseOrderByRoleAscFullNameAsc(storeId)` theo vai trò và `customerCount = 0`, `totalUsers = staff + shipper + manager`.
- `getDailyRevenueChart(int days, Long storeId)`: `orderRepository.findForChart(storeId, startDateTime)`.
- `getOrderStatusStats(Long storeId)`: tổng = `countOrders(storeId, null)`, từng trạng thái `countByStatuses(storeId, List.of(status))`.

`AdminReportServiceImpl`: thêm tham số `Long storeId` cho `getTopProducts`, `exportCsv` và các helper `*Rows` (truyền xuống repository; `revenueByDayRows` dùng `findInRange(storeId, from, to)`); thêm case `REVENUE_BY_STORE -> revenueByStoreRows(from, to)`:

```java
    private List<String[]> revenueByStoreRows(LocalDate from, LocalDate to) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Cơ sở", "Số đơn", "Doanh thu (đ)"});
        for (StoreRevenueResponse item : getRevenueByStore(from, to)) {
            rows.add(new String[] {item.getStoreName(), String.valueOf(item.getOrderCount()), plain(item.getRevenue())});
        }
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoreRevenueResponse> getRevenueByStore(LocalDate fromDate, LocalDate toDate) {
        LocalDate from = resolveFrom(fromDate, toDate);
        LocalDate to = resolveTo(toDate);
        assertValidRange(from, to);
        return orderRepository.findRevenueByStore(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }
```

Cập nhật 2 interface tương ứng.

`AdminDashboardController`, `AdminReportController`: mọi endpoint thêm `@RequestParam(required = false) Long storeId` và truyền xuống; thêm vào `AdminReportController`:

```java
    @GetMapping("/revenue-by-store")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<StoreRevenueResponse>>> revenueByStore(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy doanh thu theo cơ sở thành công",
                adminReportService.getRevenueByStore(fromDate, toDate)));
    }
```

`ManagerController` — thêm field `AdminDashboardService dashboardService`, `AdminReportService reportService`, `UserRepository userRepository`, `StoreAccessGuard storeAccessGuard` và:

```java
    @GetMapping("/dashboard/metrics")
    public ResponseEntity<ApiResponse<DashboardMetricsResponse>> metrics(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy chỉ số cơ sở thành công",
                dashboardService.getDashboardMetrics(ownStore(principal))));
    }

    @GetMapping("/dashboard/revenue-chart")
    public ResponseEntity<ApiResponse<List<DailyRevenueResponse>>> revenueChart(
            @RequestParam(defaultValue = "7") int days, @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy biểu đồ doanh thu thành công",
                dashboardService.getDailyRevenueChart(days, ownStore(principal))));
    }

    @GetMapping("/reports/top-products")
    public ResponseEntity<ApiResponse<List<TopProductResponse>>> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "10") int limit, @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy món bán chạy thành công",
                reportService.getTopProducts(fromDate, toDate, limit, ownStore(principal))));
    }

    /** MANAGER → cơ sở của mình; ADMIN gọi API này nhận báo cáo toàn chuỗi (null). */
    private Long ownStore(UserDetails principal) {
        Long userId = SecurityUtils.requireUserId(principal);
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(NotFoundMessages.userById(userId)));
        return storeAccessGuard.scopedStoreId(actor);
    }
```

Lưu ý: `AdminReportServiceImpl.getTopProducts` / `AdminDashboardServiceImpl` đang có `@PreAuthorize("hasRole('ADMIN')")` ở **controller** admin (không ở service) — giữ nguyên; nếu service có `@PreAuthorize ADMIN` thì đổi thành `hasAnyRole('MANAGER','ADMIN')`.

- [ ] **Step 5: Cập nhật test controller**

`AdminDashboardControllerTest`, `AdminReportControllerTest`: `when(service.getDashboardMetrics())` → `getDashboardMetrics(any())`, `getDailyRevenueChart(anyInt())` → `getDailyRevenueChart(anyInt(), any())`, `getOrderStatusStats()` → `getOrderStatusStats(any())`, `getTopProducts(any(), any(), anyInt())` → thêm `any()`, `exportCsv(any(), any(), any(), anyInt())` → thêm `any()`.

- [ ] **Step 6: Chạy, xác nhận xanh**

Run: `./mvnw -B test -Dtest=AdminReportServiceStoreTest,AdminDashboardControllerTest,AdminReportControllerTest`
Expected: PASS. `./mvnw -B test` → BUILD SUCCESS.

- [ ] **Step 7: Commit (người dùng tự chạy)**

```
git add -A src/main/java src/test/java
git commit -m "feat(co-so): báo cáo, dashboard lọc theo cơ sở, doanh thu theo cơ sở, báo cáo cho Manager"
```

---

## Task 9: Dữ liệu demo cho môi trường dev

**Files:**
- Modify: `config/DataInitializer.java`
- Test: chạy app profile dev (thủ công) — không có unit test (seed chỉ chạy ở profile dev/demo).

**Interfaces:**
- Consumes: `StoreRepository`, `User.setStore`.
- Produces: cơ sở `CS02` "Cơ sở Cầu Giấy" (21.033300, 105.792000), `CS03` "Cơ sở Hà Đông" (20.971400, 105.778800) khi chưa có; tài khoản `manager@gmail.com` (MANAGER, CS01), `staff2@gmail.com` (STAFF, CS02), `shipper2@gmail.com` (SHIPPER, CS02); STAFF/SHIPPER demo chưa có cơ sở → CS01. Tên chung chung, không dùng địa chỉ BAMI KING.

- [ ] **Step 1: Sửa `DataInitializer`**

- Field mới: `private final StoreRepository storeRepository;`.
- Đổi chữ ký `seedUserIfAbsent(String email, String rawPassword, String fullName, String phone, RoleName role)` thành thêm tham số cuối `Store store` và `u.setStore(store);` trước `save`.
- Đầu `run(...)` thêm:

```java
        Store cs01 = storeRepository.findByCodeAndDeletedFalse("CS01").orElseThrow();
        Store cs02 = seedStoreIfAbsent("CS02", "Cơ sở Cầu Giấy", "Phường Dịch Vọng, Hà Nội",
                new BigDecimal("21.033300"), new BigDecimal("105.792000"));
        seedStoreIfAbsent("CS03", "Cơ sở Hà Đông", "Phường Hà Đông, Hà Nội",
                new BigDecimal("20.971400"), new BigDecimal("105.778800"));
```

- Các lời gọi `seedUserIfAbsent` hiện có: CUSTOMER/ADMIN truyền `null`; STAFF/SHIPPER truyền `cs01`. Thêm:

```java
        seedUserIfAbsent("manager@gmail.com", "12345678", "Quản Lý Cơ Sở 1", "0900000004", RoleName.MANAGER, cs01);
        seedUserIfAbsent("staff2@gmail.com", "12345678", "Nhân Viên Cầu Giấy", "0900000005", RoleName.STAFF, cs02);
        seedUserIfAbsent("shipper2@gmail.com", "12345678", "Shipper Cầu Giấy", "0900000006", RoleName.SHIPPER, cs02);
```

- Helper:

```java
    private Store seedStoreIfAbsent(String code, String name, String address, BigDecimal lat, BigDecimal lng) {
        return storeRepository.findByCodeAndDeletedFalse(code).orElseGet(() -> {
            Store store = new Store();
            store.setCode(code);
            store.setName(name);
            store.setAddress(address);
            store.setLatitude(lat);
            store.setLongitude(lng);
            store.setMinOrderAmount(new BigDecimal("50000"));
            log.info("Seed cơ sở demo: {} {}", code, name);
            return storeRepository.save(store);
        });
    }
```

(import `Store`, `StoreRepository`.)

- [ ] **Step 2: Kiểm tra**

Run: `./mvnw -B test` → BUILD SUCCESS (seed chạy khi context dev khởi động trong test tích hợp).
Run: `./mvnw -B spring-boot:run` → log `Seed cơ sở demo: CS02 ...`; đăng nhập `manager@gmail.com / 12345678` qua `POST /api/v1/auth/login` thành công.

- [ ] **Step 3: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/config/DataInitializer.java
git commit -m "chore(co-so): dữ liệu demo 3 cơ sở và tài khoản quản lý/nhân viên theo cơ sở"
```

---

## Task 10: Frontend — types & API client

**Files:**
- Create: `frontend/src/types/store.ts`, `frontend/src/api/storeApi.ts`, `frontend/src/api/storeInventoryApi.ts`, `frontend/src/api/managerApi.ts`
- Modify: `frontend/src/types/auth.ts`, `frontend/src/types/order.ts`, `frontend/src/types/admin.ts`, `frontend/src/types/staff.ts`, `frontend/src/api/deliveryApi.ts`, `frontend/src/api/staffOrderApi.ts`, `frontend/src/api/adminUserApi.ts`, `frontend/src/api/adminReportsApi.ts`, `frontend/src/api/adminDashboardApi.ts`, `frontend/src/api/staffCatalogApi.ts`

**Interfaces:**
- Produces (TypeScript, dùng ở Task 11–15):
  - `RoleName = 'CUSTOMER' | 'STAFF' | 'SHIPPER' | 'MANAGER' | 'ADMIN'`; `UserInfoResponse.storeId?: number | null; storeName?: string | null`.
  - `types/store.ts`: `PublicStore`, `Store`, `StorePayload`, `StoreQuoteReason`, `StoreQuoteOption`, `DeliveryQuote`, `StoreStockItem`, `StoreRevenue`.
  - `storeApi`: `listPublic()`, `adminList()`, `adminCreate(p)`, `adminUpdate(id, p)`, `adminDelete(id)`, `setAccepting(storeId, accepting)`.
  - `deliveryApi.getQuote({ latitude, longitude, shippingAddress }): Promise<DeliveryQuote>` (xoá `getFee`).
  - `storeInventoryApi`: `list(storeId)`, `setAvailability(storeId, productId, available)`, `adjustStock(storeId, productId, changeQty, note?)`, `movements(storeId, productId, size?)`.
  - `managerApi`: `staff()`, `metrics()`, `revenueChart(days)`, `topProducts(params)`.
  - `staffOrderApi.getOrders(filter)` nhận `filter.storeId`; `getAvailableShippers(storeId?)`; `transferStore(orderCode, storeId, reason)`.
  - `AdminUser.storeId/storeName`, `AdminCreateUserPayload.storeId?`, `AdminUpdateUserPayload.storeId?`, `UserFilterParams.storeId?`.
  - `ReportType` thêm `'REVENUE_BY_STORE'`; `ReportFilterParams.storeId?`; `adminReportsApi.getRevenueByStore(params)`; `adminDashboardApi.getMetrics(storeId?)`, `getRevenueChart(days, storeId?)`, `getOrderStatusStats(storeId?)`.
  - `OrderResponse.storeId?`, `storeName?`, `storePhone?`; `CreateOrderRequest.storeId?: number | null`.
  - `ProductItem` bỏ `stockQuantity`, `lowStockThreshold`, `lowStock`; `staffCatalogApi` bỏ `adjustStock`, `getStockMovements`.

- [ ] **Step 1: `types/store.ts`**

```ts
/** Khớp DTO cơ sở ở backend (dto/store/*) */
export interface PublicStore {
  id: number;
  code: string;
  name: string;
  address: string;
  phone?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  /** HH:mm */
  openTime: string;
  closeTime: string;
  openNow: boolean;
  acceptingOrders: boolean;
}

export interface Store extends PublicStore {
  deliveryRadiusKm: number;
  freeShipRadiusKm: number;
  minOrderAmount: number;
  active: boolean;
  staffCount: number;
}

export interface StorePayload {
  code: string;
  name: string;
  address: string;
  phone?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  openTime: string;
  closeTime: string;
  deliveryRadiusKm: number;
  freeShipRadiusKm: number;
  minOrderAmount: number;
  active: boolean;
}

export type StoreQuoteReason = 'CLOSED' | 'NOT_ACCEPTING' | 'OUT_OF_RADIUS' | 'BELOW_MIN_ORDER' | 'ITEM_UNAVAILABLE';

export interface StoreQuoteOption {
  storeId: number;
  storeCode: string;
  storeName: string;
  storeAddress: string;
  storePhone?: string | null;
  openTime: string;
  closeTime: string;
  minOrderAmount: number;
  distanceKm?: number | null;
  shippingFee: number;
  originalFee: number;
  freeship: boolean;
  feeDescription: string;
  eligible: boolean;
  reasons: StoreQuoteReason[];
  /** Câu giải thích tiếng Việt, cùng thứ tự với reasons */
  reasonMessages: string[];
  unavailableItems: string[];
}

export interface DeliveryQuote {
  recommendedStoreId: number | null;
  options: StoreQuoteOption[];
}

export interface StoreStockItem {
  productId: number;
  productName: string;
  categoryName?: string | null;
  imageUrl?: string | null;
  price: number;
  onChainMenu: boolean;
  available: boolean;
  stockQuantity?: number | null;
  lowStockThreshold: number;
  lowStock: boolean;
}

export interface StoreRevenue {
  storeId: number;
  storeName: string;
  orderCount: number;
  revenue: number;
}
```

- [ ] **Step 2: `api/storeApi.ts`, `api/storeInventoryApi.ts`, `api/managerApi.ts`**

```ts
import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PublicStore, Store, StorePayload } from '../types/store';

export const storeApi = {
  async listPublic(): Promise<PublicStore[]> {
    const res = await axiosClient.get<ApiResponse<PublicStore[]>>('/stores');
    return res.data.data;
  },
  async adminList(): Promise<Store[]> {
    const res = await axiosClient.get<ApiResponse<Store[]>>('/admin/stores');
    return res.data.data;
  },
  async adminCreate(payload: StorePayload): Promise<Store> {
    const res = await axiosClient.post<ApiResponse<Store>>('/admin/stores', payload);
    return res.data.data;
  },
  async adminUpdate(id: number, payload: StorePayload): Promise<Store> {
    const res = await axiosClient.put<ApiResponse<Store>>(`/admin/stores/${id}`, payload);
    return res.data.data;
  },
  async adminDelete(id: number): Promise<void> {
    await axiosClient.delete<ApiResponse<void>>(`/admin/stores/${id}`);
  },
  /** MANAGER (cơ sở mình) / ADMIN: tạm ngưng hoặc mở lại nhận đơn */
  async setAccepting(storeId: number, accepting: boolean): Promise<Store> {
    const res = await axiosClient.patch<ApiResponse<Store>>(`/manager/stores/${storeId}/accepting`, { accepting });
    return res.data.data;
  },
};
```

```ts
import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
import type { StockMovement } from '../types/staff';
import type { StoreStockItem } from '../types/store';

const base = (storeId: number) => `/store-inventory/${storeId}/products`;

export const storeInventoryApi = {
  async list(storeId: number): Promise<StoreStockItem[]> {
    const res = await axiosClient.get<ApiResponse<StoreStockItem[]>>(base(storeId));
    return res.data.data;
  },
  async setAvailability(storeId: number, productId: number, available: boolean): Promise<StoreStockItem> {
    const res = await axiosClient.patch<ApiResponse<StoreStockItem>>(`${base(storeId)}/${productId}/availability`, {
      available,
    });
    return res.data.data;
  },
  async adjustStock(storeId: number, productId: number, changeQty: number, note?: string): Promise<StoreStockItem> {
    const res = await axiosClient.post<ApiResponse<StoreStockItem>>(`${base(storeId)}/${productId}/stock`, {
      changeQty,
      note,
    });
    return res.data.data;
  },
  async movements(storeId: number, productId: number, size = 8): Promise<StockMovement[]> {
    const res = await axiosClient.get<ApiResponse<PageResponse<StockMovement>>>(
      `${base(storeId)}/${productId}/movements`,
      { params: { page: 0, size } }
    );
    return res.data.data.content;
  },
};
```

```ts
import { axiosClient } from './axiosClient';
import type { ApiResponse, UserInfoResponse } from '../types/auth';
import type { DailyRevenue, DashboardMetrics, ReportFilterParams, TopProduct } from '../types/admin';

/** API riêng của Quản lý cơ sở — server tự khoá về cơ sở của người gọi */
export const managerApi = {
  async staff(): Promise<UserInfoResponse[]> {
    const res = await axiosClient.get<ApiResponse<UserInfoResponse[]>>('/manager/staff');
    return res.data.data;
  },
  async metrics(): Promise<DashboardMetrics> {
    const res = await axiosClient.get<ApiResponse<DashboardMetrics>>('/manager/dashboard/metrics');
    return res.data.data;
  },
  async revenueChart(days = 7): Promise<DailyRevenue[]> {
    const res = await axiosClient.get<ApiResponse<DailyRevenue[]>>('/manager/dashboard/revenue-chart', {
      params: { days },
    });
    return res.data.data;
  },
  async topProducts(params: ReportFilterParams = {}): Promise<TopProduct[]> {
    const res = await axiosClient.get<ApiResponse<TopProduct[]>>('/manager/reports/top-products', { params });
    return res.data.data;
  },
};
```

- [ ] **Step 3: Sửa type & API có sẵn**

- `types/auth.ts`: `export type RoleName = 'CUSTOMER' | 'STAFF' | 'SHIPPER' | 'MANAGER' | 'ADMIN';` và trong `UserInfoResponse` thêm `storeId?: number | null;` `storeName?: string | null;`.
- `types/order.ts`: `CreateOrderRequest` thêm `storeId?: number | null;`; `OrderResponse` thêm `storeId?: number | null; storeName?: string | null; storePhone?: string | null;`.
- `types/admin.ts`: `AdminUser` thêm `storeId?: number | null; storeName?: string | null;`; `AdminCreateUserPayload`, `AdminUpdateUserPayload` thêm `storeId?: number | null;`; `UserFilterParams` thêm `storeId?: number;`; `ReportType` thêm `| 'REVENUE_BY_STORE'`; `ReportFilterParams` thêm `storeId?: number;`.
- `types/staff.ts`: xoá 3 trường tồn kho khỏi `ProductItem` và `stockQuantity`/`lowStockThreshold` khỏi `ProductCreatePayload`/`ProductUpdatePayload` nếu có.
- `api/deliveryApi.ts` — thay toàn file:

```ts
import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { DeliveryQuote } from '../types/store';

export interface DeliveryQuoteParams {
  latitude?: number | null;
  longitude?: number | null;
  shippingAddress?: string;
}

export const deliveryApi = {
  /**
   * Báo giá theo từng cơ sở cho giỏ hàng hiện tại (server tự đọc giỏ) — cùng logic mà
   * `POST /orders` dùng để chọn cơ sở và tính phí, nên số xem trước khớp số chốt đơn.
   */
  async getQuote({ latitude, longitude, shippingAddress }: DeliveryQuoteParams): Promise<DeliveryQuote> {
    const res = await axiosClient.get<ApiResponse<DeliveryQuote>>('/delivery/quote', {
      params: { latitude: latitude ?? undefined, longitude: longitude ?? undefined, shippingAddress },
    });
    return res.data.data;
  },
};
```

- `types/delivery.ts`: giữ `DeliveryFeeResult` (không còn nơi dùng thì xoá file và import).
- `api/staffOrderApi.ts`: `AdminOrderFilter` thêm `storeId?: number;`, truyền `storeId: filter.storeId` trong params của `getOrders`; `getOrderQueue(status?, page = 0, size = 50, storeId?: number)` truyền `storeId`; `getAvailableShippers(storeId?: number)` thêm `params: { storeId }`; thêm:

```ts
  /** Chuyển đơn PENDING sang cơ sở khác (MANAGER cơ sở hiện tại / ADMIN) */
  async transferStore(orderCode: string, storeId: number, reason: string): Promise<OrderResponse> {
    const res = await axiosClient.put<ApiResponse<OrderResponse>>(`/admin/orders/${orderCode}/store`, {
      storeId,
      reason,
    });
    return res.data.data;
  },
```

- `api/adminUserApi.ts`: `getUsers` truyền `storeId` trong params (đã nhận `UserFilterParams`).
- `api/adminReportsApi.ts`: thêm

```ts
  async getRevenueByStore(params: ReportFilterParams = {}): Promise<StoreRevenue[]> {
    const res = await axiosClient.get<ApiResponse<StoreRevenue[]>>('/admin/reports/revenue-by-store', { params });
    return res.data.data;
  },
```

  (import `StoreRevenue` từ `../types/store`.)
- `api/adminDashboardApi.ts`: 3 method thêm tham số `storeId?: number` và `params: { storeId }` (`getRevenueChart(days, storeId)` → `params: { days, storeId }`).
- `api/staffCatalogApi.ts`: xoá `adjustStock`, `getStockMovements`.

- [ ] **Step 4: Type-check**

Run (thư mục `frontend`): `npx tsc -b`
Expected: lỗi **chỉ** ở các trang dùng API/field đã xoá (`CheckoutPage` dùng `getFee`, `StaffMenuPage` dùng tồn kho, `AdminUsersPage` `Record<RoleName,…>` thiếu `MANAGER`) — sửa ở Task 12/14/15. Ghi lại danh sách lỗi; không sửa ở task này.

- [ ] **Step 5: Commit** — gộp với Task 12 (frontend chưa build được giữa chừng); không commit riêng.

---

## Task 11: Frontend — trang "Hệ thống cửa hàng" + menu/footer

**Files:**
- Create: `frontend/src/components/store/storeLabels.ts`, `frontend/src/components/store/StoresMap.tsx`, `frontend/src/pages/StoresPage.tsx`, `frontend/src/styles/components/stores.css`
- Modify: `frontend/src/App.tsx`, `frontend/src/components/layout/CustomerLayout.tsx`

**Interfaces:**
- Consumes: `storeApi.listPublic`, `PublicStore` (Task 10); `GeoUtils` không có ở FE → dùng hàm `haversineKm` trong `storeLabels.ts`.
- Produces: `storeStatus(store: PublicStore): { label: string; tone: BadgeTone }`, `haversineKm(lat1,lng1,lat2,lng2): number`, `directionsUrl(lat,lng,address)`; component `StoresMap({ stores, highlightId?, height? })`; route `/stores`.

- [ ] **Step 1: `components/store/storeLabels.ts`**

```ts
import type { BadgeTone } from '../ui';
import type { PublicStore } from '../../types/store';

/** Nhãn trạng thái cơ sở hiển thị cho khách */
export const storeStatus = (store: Pick<PublicStore, 'openNow' | 'acceptingOrders'>): { label: string; tone: BadgeTone } => {
  if (!store.openNow) return { label: 'Đã đóng cửa', tone: 'neutral' };
  if (!store.acceptingOrders) return { label: 'Tạm ngưng nhận đơn', tone: 'warning' };
  return { label: 'Đang mở cửa', tone: 'success' };
};

/** Khoảng cách đường chim bay (km) — chỉ để sắp xếp "gần tôi", phí ship do server tính */
export const haversineKm = (lat1: number, lng1: number, lat2: number, lng2: number): number => {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371.0088 * Math.asin(Math.min(1, Math.sqrt(a)));
};

export const directionsUrl = (latitude: number | null | undefined, longitude: number | null | undefined, address: string) =>
  latitude != null && longitude != null
    ? `https://www.google.com/maps/dir/?api=1&destination=${latitude},${longitude}`
    : `https://maps.google.com/?q=${encodeURIComponent(address)}`;
```

- [ ] **Step 2: `components/store/StoresMap.tsx`**

```tsx
import { useMemo } from 'react';
import { MapContainer, Marker, Popup, TileLayer } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
import type { PublicStore } from '../../types/store';
import { storeStatus } from './storeLabels';

const PIN_ICON = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  shadowSize: [41, 41],
});

const FALLBACK_CENTER: [number, number] = [21.028511, 105.854167];

export interface StoresMapProps {
  stores: PublicStore[];
  height?: number;
}

/** Bản đồ ghim mọi cơ sở đã có toạ độ (không gọi tra địa chỉ — chỉ tải tile) */
export const StoresMap = ({ stores, height = 360 }: StoresMapProps) => {
  const pinned = stores.filter((s) => s.latitude != null && s.longitude != null);
  const bounds = useMemo(
    () =>
      pinned.length > 1
        ? L.latLngBounds(pinned.map((s) => [s.latitude as number, s.longitude as number]))
        : undefined,
    [pinned]
  );
  const center: [number, number] =
    pinned.length === 1 ? [pinned[0].latitude as number, pinned[0].longitude as number] : FALLBACK_CENTER;

  return (
    <div className="addr-map__canvas" style={{ height }}>
      <MapContainer
        center={center}
        zoom={12}
        bounds={bounds}
        boundsOptions={{ padding: [32, 32] }}
        scrollWheelZoom={false}
        style={{ height: '100%', width: '100%' }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {pinned.map((store) => (
          <Marker key={store.id} position={[store.latitude as number, store.longitude as number]} icon={PIN_ICON}>
            <Popup>
              <strong>{store.name}</strong>
              <br />
              {store.address}
              <br />
              {store.openTime}–{store.closeTime} · {storeStatus(store).label}
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
};
```

- [ ] **Step 3: `pages/StoresPage.tsx`**

```tsx
import { useEffect, useMemo, useState } from 'react';
import { Clock, Crosshair, MapPin, Navigation, Phone, Store as StoreIcon } from 'lucide-react';
import { storeApi } from '../api/storeApi';
import { Badge, Button, EmptyState, Spinner } from '../components/ui';
import { StoresMap } from '../components/store/StoresMap';
import { directionsUrl, haversineKm, storeStatus } from '../components/store/storeLabels';
import type { PublicStore } from '../types/store';
import '../styles/components/address-map.css';
import '../styles/components/stores.css';

/** Hệ thống cửa hàng — bản đồ + danh sách, sắp theo khoảng cách khi khách bật vị trí */
export const StoresPage = () => {
  const [stores, setStores] = useState<PublicStore[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [me, setMe] = useState<{ latitude: number; longitude: number } | null>(null);
  const [locating, setLocating] = useState(false);

  useEffect(() => {
    let alive = true;
    storeApi
      .listPublic()
      .then((data) => {
        if (alive) setStores(data);
      })
      .catch(() => {
        if (alive) setError('Không tải được danh sách cửa hàng. Vui lòng thử lại.');
      })
      .finally(() => {
        if (alive) setIsLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  const sorted = useMemo(() => {
    if (!me) return stores;
    const distance = (s: PublicStore) =>
      s.latitude != null && s.longitude != null
        ? haversineKm(me.latitude, me.longitude, s.latitude, s.longitude)
        : Number.POSITIVE_INFINITY;
    return [...stores].sort((a, b) => distance(a) - distance(b));
  }, [stores, me]);

  const locate = () => {
    if (!('geolocation' in navigator)) return;
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setMe({ latitude: pos.coords.latitude, longitude: pos.coords.longitude });
        setLocating(false);
      },
      () => setLocating(false),
      { enableHighAccuracy: true, timeout: 10000 }
    );
  };

  if (isLoading) {
    return (
      <div className="page-state">
        <Spinner size={28} />
      </div>
    );
  }

  if (error || stores.length === 0) {
    return (
      <EmptyState
        icon={<StoreIcon size={30} />}
        title="Chưa có cửa hàng"
        description={error ?? 'Hệ thống cửa hàng đang được cập nhật.'}
      />
    );
  }

  return (
    <div className="stores">
      <div className="page-bar">
        <div>
          <h1 className="page-bar__title">Hệ thống cửa hàng</h1>
          <p className="stores__sub">{stores.length} cơ sở · giao nhanh trong bán kính phục vụ của từng cơ sở</p>
        </div>
        <Button variant="secondary" icon={<Crosshair size={16} />} loading={locating} onClick={locate}>
          Tìm cơ sở gần tôi
        </Button>
      </div>

      <StoresMap stores={stores} />

      <ul className="stores__list">
        {sorted.map((store) => {
          const status = storeStatus(store);
          const km =
            me && store.latitude != null && store.longitude != null
              ? haversineKm(me.latitude, me.longitude, store.latitude, store.longitude)
              : null;
          return (
            <li key={store.id} className="card stores__item">
              <div className="stores__head">
                <h2 className="stores__name">{store.name}</h2>
                <Badge tone={status.tone}>{status.label}</Badge>
              </div>
              <p className="stores__line">
                <MapPin size={15} /> {store.address}
                {km != null && <span className="stores__km"> · cách bạn ~{km.toFixed(1)} km</span>}
              </p>
              <p className="stores__line">
                <Clock size={15} /> {store.openTime} – {store.closeTime} hằng ngày
              </p>
              {store.phone && (
                <a className="stores__line" href={`tel:${store.phone.replace(/\s/g, '')}`}>
                  <Phone size={15} /> {store.phone}
                </a>
              )}
              <a
                className="ui-btn ui-btn--ghost ui-btn--sm stores__dir"
                href={directionsUrl(store.latitude, store.longitude, store.address)}
                target="_blank"
                rel="noopener noreferrer"
              >
                <Navigation size={15} /> Chỉ đường
              </a>
            </li>
          );
        })}
      </ul>
    </div>
  );
};
```

- [ ] **Step 4: `styles/components/stores.css`**

```css
/* Trang Hệ thống cửa hàng + thẻ chọn cơ sở ở thanh toán */
.stores {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stores__sub {
  margin: 4px 0 0;
  font-size: var(--text-sm);
  color: var(--stone-500);
}

.stores__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.stores__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
}

.stores__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.stores__name {
  margin: 0;
  font-size: var(--text-base);
  color: var(--stone-800);
}

.stores__line {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0;
  font-size: var(--text-sm);
  color: var(--stone-600);
  text-decoration: none;
}

.stores__line svg {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--primary-600);
}

.stores__km {
  color: var(--stone-500);
}

.stores__dir {
  align-self: flex-start;
  margin-top: 4px;
}

/* Thẻ "Giao từ cơ sở" ở trang thanh toán */
.ck-store {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--primary-50);
}

.ck-store__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ck-store__name {
  font-weight: 600;
  color: var(--stone-800);
}

.ck-store__meta {
  font-size: var(--text-sm);
  color: var(--stone-600);
}

.ck-store__options {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ck-store__option {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  font-size: var(--text-sm);
  cursor: pointer;
}

.ck-store__option--off {
  cursor: not-allowed;
  opacity: 0.65;
}

.ck-store__reason {
  display: block;
  color: var(--danger);
  font-size: var(--text-xs);
}

.ck-store__warn {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--danger);
}
```

- [ ] **Step 5: Route + menu + footer**

- `App.tsx`: import `StoresPage` và trong nhóm route khách công khai (cạnh `about`/`contact`) thêm `<Route path="stores" element={<StoresPage />} />`.
- `CustomerLayout.tsx`:
  - Sau `<NavLink to="/menu" ...>...</NavLink>` thêm `<NavLink to="/stores" className={navLinkClass}>Cửa hàng</NavLink>`.
  - Trong footer, cột liên kết (sau `<Link to="/menu">Thực đơn</Link>`) thêm `<Link to="/stores">Hệ thống cửa hàng</Link>`.
  - Trong cột "Liên hệ", sau khối `settings.contactAddress` thêm:

```tsx
              <Link className="cshop__foot-note" to="/stores">
                <MapPin size={15} />
                Xem hệ thống cửa hàng
              </Link>
```

  và đổi điều kiện hiển thị cột thành `{(hasContact || true) && (` → **thay bằng** hiển thị cột luôn (bỏ điều kiện `hasContact &&`), vì link cửa hàng luôn có.

- [ ] **Step 6: Kiểm tra** — chạy cùng Task 12 (frontend build).

---

## Task 12: Frontend — chọn cơ sở ở Thanh toán + cơ sở trên trang theo dõi đơn

**Files:**
- Modify: `frontend/src/pages/CheckoutPage.tsx`, `frontend/src/pages/OrderTrackingPage.tsx`

**Interfaces:**
- Consumes: `deliveryApi.getQuote`, `DeliveryQuote`, `StoreQuoteOption` (Task 10); class CSS `ck-store*` (Task 11).
- Produces: payload `CreateOrderRequest.storeId` = cơ sở đang chọn.

- [ ] **Step 1: State và gọi báo giá**

Trong `CheckoutPage.tsx`:
- Import: `import type { DeliveryQuote, StoreQuoteOption } from '../types/store';` và `import '../styles/components/stores.css';`; bỏ import `DeliveryFeeResult`.
- Thay state `fee`/`isLoadingFee`/`feeError` bằng:

```tsx
  const [quote, setQuote] = useState<DeliveryQuote | null>(null);
  const [isLoadingFee, setIsLoadingFee] = useState(false);
  const [feeError, setFeeError] = useState<string | null>(null);
  /** Cơ sở khách chọn; null = theo đề xuất của server */
  const [chosenStoreId, setChosenStoreId] = useState<number | null>(null);
  const [showStores, setShowStores] = useState(false);
```

- Thay effect báo phí (khối gọi `deliveryApi.getFee`) bằng:

```tsx
  useEffect(() => {
    const address = shippingAddress.trim();
    if (subtotal <= 0 || address.length < 5) {
      setQuote(null);
      setFeeError(null);
      return;
    }
    let cancelled = false;
    setIsLoadingFee(true);
    const timer = window.setTimeout(() => {
      deliveryApi
        .getQuote({ shippingAddress: address, latitude, longitude })
        .then((result) => {
          if (cancelled) return;
          setQuote(result);
          setFeeError(null);
        })
        .catch((err) => {
          if (cancelled) return;
          setQuote(null);
          setFeeError(err instanceof Error ? err.message : 'Không tính được phí giao hàng');
        })
        .finally(() => {
          if (!cancelled) setIsLoadingFee(false);
        });
    }, 400);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [shippingAddress, subtotal, latitude, longitude]);
```

- Dẫn xuất cơ sở đang áp dụng và phí (đặt ngay sau effect):

```tsx
  const selectedOption: StoreQuoteOption | null = (() => {
    if (!quote) return null;
    const byChoice = quote.options.find((o) => o.storeId === chosenStoreId && o.eligible);
    if (byChoice) return byChoice;
    return quote.options.find((o) => o.storeId === quote.recommendedStoreId) ?? null;
  })();
  const noStoreAvailable = quote != null && !quote.options.some((o) => o.eligible);
  const needsManualChoice = quote != null && quote.recommendedStoreId == null && !noStoreAvailable && selectedOption == null;
```

- Thay `const shippingFee = fee?.shippingFee ?? 0;` bằng `const shippingFee = selectedOption?.shippingFee ?? 0;`. Mọi chỗ còn dùng `fee?.freeship`, `fee?.description`, `fee?.freeshipThreshold` đổi thành `selectedOption?.freeship`, `selectedOption?.feeDescription`, và `200000` (ngưỡng freeship giá trị đơn mặc định) tương ứng; chỗ truyền `shippingFee: fee?.shippingFee` khi kiểm mã KM đổi thành `shippingFee: selectedOption?.shippingFee`.

- [ ] **Step 2: Thẻ "Giao từ cơ sở"**

Ngay trước khối `<div className="ck__block">` chứa Textarea "Ghi chú cho quán", thêm:

```tsx
                  {quote && (
                    <div className="ck__block">
                      <div className="ck-store">
                        {selectedOption ? (
                          <div className="ck-store__row">
                            <span>
                              <span className="ck-store__name">Giao từ: {selectedOption.storeName}</span>
                              <span className="ck-store__meta">
                                {' · '}
                                {selectedOption.distanceKm != null
                                  ? `${selectedOption.distanceKm.toFixed(1)} km · `
                                  : ''}
                                {selectedOption.freeship ? 'Miễn phí ship' : formatCurrency(selectedOption.shippingFee)}
                              </span>
                            </span>
                            <Button type="button" size="sm" variant="ghost" onClick={() => setShowStores((v) => !v)}>
                              {showStores ? 'Đóng' : 'Đổi cơ sở'}
                            </Button>
                          </div>
                        ) : noStoreAvailable ? (
                          <p className="ck-store__warn">
                            Hiện chưa có cơ sở nào phục vụ được địa chỉ và giỏ hàng này — xem lý do bên dưới.
                          </p>
                        ) : (
                          <p className="ck-store__meta">
                            Chọn cơ sở phục vụ đơn hàng (ghim vị trí để hệ thống tự chọn cơ sở gần nhất).
                          </p>
                        )}
                        {(showStores || noStoreAvailable || needsManualChoice) && (
                          <ul className="ck-store__options" role="radiogroup" aria-label="Chọn cơ sở">
                            {quote.options.map((option) => (
                              <li key={option.storeId}>
                                <label
                                  className={`ck-store__option${option.eligible ? '' : ' ck-store__option--off'}`}
                                >
                                  <input
                                    type="radio"
                                    name="store"
                                    disabled={!option.eligible}
                                    checked={selectedOption?.storeId === option.storeId}
                                    onChange={() => {
                                      setChosenStoreId(option.storeId);
                                      setShowStores(false);
                                    }}
                                  />
                                  <span>
                                    <strong>{option.storeName}</strong> — {option.storeAddress}
                                    {option.distanceKm != null && ` · ${option.distanceKm.toFixed(1)} km`}
                                    {option.eligible &&
                                      ` · ${option.freeship ? 'Miễn phí ship' : formatCurrency(option.shippingFee)}`}
                                    {option.reasonMessages.map((message) => (
                                      <span key={message} className="ck-store__reason">
                                        {message}
                                      </span>
                                    ))}
                                  </span>
                                </label>
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                    </div>
                  )}
```

- [ ] **Step 3: Gửi `storeId` + khoá nút khi không có cơ sở**

- Trong `handleSubmit`, trước `setIsSubmitting(true)` thêm:

```tsx
    if (!selectedOption) {
      setApiError(
        noStoreAvailable
          ? 'Hiện chưa có cơ sở nào phục vụ được đơn này.'
          : 'Vui lòng chọn cơ sở phục vụ đơn hàng.'
      );
      return;
    }
```

- Trong `payload` thêm `storeId: selectedOption.storeId,`.
- Nút "Đặt hàng" (submit) thêm `disabled={noStoreAvailable}` (giữ các thuộc tính hiện có).
- Khi đổi địa chỉ (`switchToNewAddress`, `selectAddress`) thêm `setChosenStoreId(null);`.

- [ ] **Step 4: Trang theo dõi đơn**

`OrderTrackingPage.tsx` — ngay sau khối `<p className="track__time">Đặt lúc …</p>` thêm:

```tsx
              {order.storeName && (
                <p className="track__time">
                  Cơ sở phục vụ: {order.storeName}
                  {order.storePhone && (
                    <>
                      {' · '}
                      <a href={`tel:${order.storePhone.replace(/\s/g, '')}`}>☎ {order.storePhone}</a>
                    </>
                  )}
                </p>
              )}
```

- [ ] **Step 5: Build & lint**

Run (`frontend`): `npx tsc -b` → lỗi chỉ còn ở `StaffMenuPage`/`AdminUsersPage`/`AdminSiteSettingsPage` (Task 13–15).
Run: `npx oxlint src/pages/CheckoutPage.tsx src/pages/StoresPage.tsx src/components/store` → không có cảnh báo mới ngoài `set-state-in-effect` sẵn có.

- [ ] **Step 6: Commit (người dùng tự chạy, sau khi Task 15 build xanh — xem Task 15 Step 8)**

---

## Task 13: Frontend — trang quản lý "Cơ sở" (ADMIN) + dọn Cài đặt website

**Files:**
- Create: `frontend/src/pages/admin/AdminStoresPage.tsx`
- Modify: `frontend/src/App.tsx`, `frontend/src/components/layout/navItems.ts`, `frontend/src/pages/admin/AdminSiteSettingsPage.tsx`, `frontend/src/types/siteSettings.ts`, `frontend/src/components/address/AddressMapPicker.tsx`

**Interfaces:**
- Consumes: `storeApi` (Task 10), `AddressMapPicker` (đã có), `Modal/Input/Button/Badge/useToast/useConfirm`.
- Produces: route `/admin/stores`; mục menu ADMIN "Cơ sở".

- [ ] **Step 1: `pages/admin/AdminStoresPage.tsx`**

```tsx
import { useCallback, useEffect, useState } from 'react';
import { MapPinOff, Pencil, Plus, Store as StoreIcon, Trash2 } from 'lucide-react';
import { storeApi } from '../../api/storeApi';
import { Badge, Button, EmptyState, Input, Modal, PageHeader, Skeleton, Textarea, useConfirm, useToast } from '../../components/ui';
import { AddressMapPicker } from '../../components/address/AddressMapPicker';
import { storeStatus } from '../../components/store/storeLabels';
import { formatCurrency } from '../../utils/formatters';
import type { GeocodeResult, GeoPoint } from '../../utils/geocoding';
import type { Store, StorePayload } from '../../types/store';
import '../../styles/components/address-map.css';
import '../../styles/components/stores.css';

const EMPTY_FORM: StorePayload = {
  code: '',
  name: '',
  address: '',
  phone: '',
  latitude: null,
  longitude: null,
  openTime: '06:30',
  closeTime: '22:00',
  deliveryRadiusKm: 5,
  freeShipRadiusKm: 3,
  minOrderAmount: 50000,
  active: true,
};

const toForm = (store: Store): StorePayload => ({
  code: store.code,
  name: store.name,
  address: store.address,
  phone: store.phone ?? '',
  latitude: store.latitude ?? null,
  longitude: store.longitude ?? null,
  openTime: store.openTime,
  closeTime: store.closeTime,
  deliveryRadiusKm: store.deliveryRadiusKm,
  freeShipRadiusKm: store.freeShipRadiusKm,
  minOrderAmount: store.minOrderAmount,
  active: store.active,
});

/** ADMIN — danh sách và thêm/sửa/xoá cơ sở trong chuỗi */
export const AdminStoresPage = () => {
  const toast = useToast();
  const confirm = useConfirm();
  const [stores, setStores] = useState<Store[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [editing, setEditing] = useState<{ id: number | null; form: StorePayload } | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      setStores(await storeApi.adminList());
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không tải được danh sách cơ sở');
    } finally {
      setIsLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    void load();
  }, [load]);

  const setField = <K extends keyof StorePayload>(key: K, value: StorePayload[K]) =>
    setEditing((prev) => (prev ? { ...prev, form: { ...prev.form, [key]: value } } : prev));

  const handlePick = (point: GeoPoint, address: GeocodeResult | null) =>
    setEditing((prev) =>
      prev
        ? {
            ...prev,
            form: {
              ...prev.form,
              latitude: Number(point.latitude.toFixed(6)),
              longitude: Number(point.longitude.toFixed(6)),
              ...(address ? { address: address.fullAddress } : {}),
            },
          }
        : prev
    );

  const handleSave = async () => {
    if (!editing) return;
    const { id, form } = editing;
    if (!form.code.trim() || !form.name.trim() || form.address.trim().length < 5) {
      toast.error('Vui lòng nhập mã, tên và địa chỉ cơ sở');
      return;
    }
    if (form.openTime >= form.closeTime) {
      toast.error('Giờ mở cửa phải trước giờ đóng cửa');
      return;
    }
    setIsSaving(true);
    try {
      const payload = { ...form, phone: form.phone?.trim() || null };
      if (id) {
        await storeApi.adminUpdate(id, payload);
        toast.success('Đã cập nhật cơ sở');
      } else {
        await storeApi.adminCreate(payload);
        toast.success('Đã thêm cơ sở');
      }
      setEditing(null);
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Lưu cơ sở thất bại');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (store: Store) => {
    const accepted = await confirm({
      title: 'Xoá cơ sở',
      message: `Xoá ${store.name}? Cơ sở còn nhân viên hoặc còn đơn chưa xong sẽ không xoá được.`,
      confirmText: 'Xoá',
      danger: true,
    });
    if (!accepted) return;
    try {
      await storeApi.adminDelete(store.id);
      toast.success('Đã xoá cơ sở');
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Xoá cơ sở thất bại');
    }
  };

  const form = editing?.form;
  const pinned: GeoPoint | null =
    form && form.latitude != null && form.longitude != null
      ? { latitude: form.latitude, longitude: form.longitude }
      : null;

  return (
    <>
      <PageHeader
        title="Cơ sở"
        subtitle="Các cửa hàng trong chuỗi: vị trí, giờ mở cửa, bán kính giao, freeship và đơn tối thiểu."
        actions={
          <Button icon={<Plus size={17} />} onClick={() => setEditing({ id: null, form: EMPTY_FORM })}>
            Thêm cơ sở
          </Button>
        }
      />

      {isLoading ? (
        <Skeleton variant="row" />
      ) : stores.length === 0 ? (
        <EmptyState icon={<StoreIcon size={30} />} title="Chưa có cơ sở" description="Bấm Thêm cơ sở để bắt đầu." />
      ) : (
        <section className="card">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Mã</th>
                  <th>Tên / địa chỉ</th>
                  <th>Giờ</th>
                  <th>Giao hàng</th>
                  <th>Nhân sự</th>
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {stores.map((store) => {
                  const status = store.active ? storeStatus(store) : { label: 'Ngừng hoạt động', tone: 'danger' as const };
                  return (
                    <tr key={store.id}>
                      <td>{store.code}</td>
                      <td>
                        <strong>{store.name}</strong>
                        <br />
                        <small>{store.address}</small>
                        {store.latitude == null && (
                          <>
                            <br />
                            <Badge tone="warning">Chưa ghim vị trí</Badge>
                          </>
                        )}
                      </td>
                      <td>
                        {store.openTime}–{store.closeTime}
                      </td>
                      <td>
                        {store.deliveryRadiusKm} km · freeship {store.freeShipRadiusKm} km
                        <br />
                        <small>Đơn từ {formatCurrency(store.minOrderAmount)}</small>
                      </td>
                      <td>{store.staffCount}</td>
                      <td>
                        <Badge tone={status.tone}>{status.label}</Badge>
                      </td>
                      <td>
                        <Button size="sm" variant="ghost" icon={<Pencil size={15} />} onClick={() => setEditing({ id: store.id, form: toForm(store) })}>
                          Sửa
                        </Button>
                        <Button size="sm" variant="ghost" icon={<Trash2 size={15} />} onClick={() => void handleDelete(store)}>
                          Xoá
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        size="lg"
        title={editing?.id ? 'Sửa cơ sở' : 'Thêm cơ sở'}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)}>
              Huỷ
            </Button>
            <Button loading={isSaving} onClick={() => void handleSave()}>
              Lưu cơ sở
            </Button>
          </>
        }
      >
        {form && (
          <div className="addr-fields">
            <div className="addr-fields__row">
              <Input label="Mã cơ sở" required maxLength={20} value={form.code} onChange={(e) => setField('code', e.target.value)} placeholder="CS02" />
              <Input label="Tên cơ sở" required maxLength={100} value={form.name} onChange={(e) => setField('name', e.target.value)} placeholder="Cơ sở Cầu Giấy" />
            </div>
            <AddressMapPicker value={pinned} onPick={handlePick} height={260} />
            {pinned && (
              <Button type="button" size="sm" variant="ghost" icon={<MapPinOff size={15} />} onClick={() => { setField('latitude', null); setField('longitude', null); }}>
                Bỏ ghim (cơ sở sẽ phục vụ mọi địa chỉ, phí theo khu vực)
              </Button>
            )}
            <Textarea label="Địa chỉ" required rows={2} maxLength={500} value={form.address} onChange={(e) => setField('address', e.target.value)} hint="Tự điền khi ghim trên bản đồ — sửa lại nếu chưa đúng." />
            <div className="addr-fields__row">
              <Input label="Số điện thoại" maxLength={20} value={form.phone ?? ''} onChange={(e) => setField('phone', e.target.value)} />
              <label className="ui-check">
                <input type="checkbox" checked={form.active} onChange={(e) => setField('active', e.target.checked)} />
                Đang hoạt động
              </label>
            </div>
            <div className="addr-fields__row">
              <Input label="Giờ mở cửa" type="time" value={form.openTime} onChange={(e) => setField('openTime', e.target.value)} />
              <Input label="Giờ đóng cửa" type="time" value={form.closeTime} onChange={(e) => setField('closeTime', e.target.value)} />
            </div>
            <div className="addr-fields__row">
              <Input label="Bán kính giao (km)" type="number" min={0.5} max={100} step={0.5} value={form.deliveryRadiusKm} onChange={(e) => setField('deliveryRadiusKm', Number(e.target.value))} />
              <Input label="Freeship trong bán kính (km)" type="number" min={0} max={100} step={0.5} value={form.freeShipRadiusKm} onChange={(e) => setField('freeShipRadiusKm', Number(e.target.value))} hint="0 = không freeship theo khoảng cách" />
            </div>
            <Input label="Đơn tối thiểu (đ)" type="number" min={0} step={1000} value={form.minOrderAmount} onChange={(e) => setField('minOrderAmount', Number(e.target.value))} />
          </div>
        )}
      </Modal>
    </>
  );
};
```

- [ ] **Step 2: Route + menu**

- `App.tsx`: import `AdminStoresPage`; trong nhóm `/admin` thêm `<Route path="stores" element={<AdminStoresPage />} />`.
- `navItems.ts`: import icon `Store` từ `lucide-react` (đặt tên `Store as StoreIcon`), thêm vào `ADMIN_NAV` sau mục "Đơn hàng": `{ to: '/admin/stores', label: 'Cơ sở', icon: StoreIcon },`.

- [ ] **Step 3: Dọn Cài đặt website + bản đồ chung**

- `types/siteSettings.ts`: xoá `storeLatitude`, `storeLongitude`, `deliveryMaxRadiusKm` khỏi interface và `DEFAULT_SITE_SETTINGS`.
- `AdminSiteSettingsPage.tsx`:
  - Xoá cả `<section>` "Vị trí cửa hàng & giao hàng", hàm `handleStorePick`, biến `storePoint`, import `AddressMapPicker`, `GeocodeResult`, `GeoPoint`, `MapPinOff`, và khối validate `deliveryMaxRadiusKm`.
  - `CustomKey` chỉ còn `'heroImageUrl'`.
  - Trả ô "Địa chỉ" về mục "Thông tin liên hệ": thêm lại vào `fields` của section `contact`:

```ts
      {
        key: 'contactAddress',
        label: 'Địa chỉ hiển thị ở footer',
        maxLength: 255,
        placeholder: 'Văn phòng / cơ sở chính',
        textarea: true,
      },
```

  và sửa `description` của section `contact` thành `'Hiện ở cột "Liên hệ" ngoài footer. Vị trí và giờ của từng cơ sở quản lý ở trang Cơ sở.'`.
  - Xoá CSS `.asettings__store`, `.asettings__store-pin` trong `admin-site-settings.css`.
- `AddressMapPicker.tsx`: bỏ phần đọc `settings.storeLatitude/storeLongitude` (và `useSiteSettings` nếu không dùng nữa); `initialCenter` = `value ? [value.latitude, value.longitude] : FALLBACK_CENTER`.

- [ ] **Step 4: Kiểm tra** — chạy cùng Task 15.

---

## Task 14: Frontend — Tài khoản, Đơn hàng, Báo cáo, Dashboard theo cơ sở (ADMIN)

**Files:**
- Create: `frontend/src/components/store/StoreScopeSelect.tsx`
- Modify: `frontend/src/pages/admin/AdminUsersPage.tsx`, `frontend/src/pages/admin/AdminOrdersPage.tsx`, `frontend/src/pages/admin/AdminReportsPage.tsx`, `frontend/src/pages/admin/AdminDashboardPage.tsx`

**Interfaces:**
- Consumes: `storeApi.adminList`, `Store` (Task 10), `adminUserApi`, `staffOrderApi.getOrders({storeId})`, `adminReportsApi.getRevenueByStore`, `adminDashboardApi.*(storeId)`.
- Produces: component `StoreScopeSelect({ value, onChange, allowAll?, label? })` — `value: number | null` (null = tất cả cơ sở).

- [ ] **Step 1: `components/store/StoreScopeSelect.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { storeApi } from '../../api/storeApi';
import { Select } from '../ui';
import type { Store } from '../../types/store';

export interface StoreScopeSelectProps {
  value: number | null;
  onChange: (storeId: number | null) => void;
  /** Có lựa chọn "Tất cả cơ sở" (null) */
  allowAll?: boolean;
  label?: string;
}

/** Ô chọn cơ sở dùng chung cho các trang ADMIN (đơn, báo cáo, dashboard, tài khoản) */
export const StoreScopeSelect = ({ value, onChange, allowAll = true, label = 'Cơ sở' }: StoreScopeSelectProps) => {
  const [stores, setStores] = useState<Store[]>([]);

  useEffect(() => {
    let alive = true;
    storeApi
      .adminList()
      .then((data) => {
        if (alive) setStores(data);
      })
      .catch(() => {
        if (alive) setStores([]);
      });
    return () => {
      alive = false;
    };
  }, []);

  return (
    <Select
      label={label}
      value={value == null ? '' : String(value)}
      onChange={(event) => onChange(event.target.value === '' ? null : Number(event.target.value))}
    >
      {allowAll && <option value="">Tất cả cơ sở</option>}
      {!allowAll && value == null && <option value="">— Chọn cơ sở —</option>}
      {stores.map((store) => (
        <option key={store.id} value={store.id}>
          {store.code} · {store.name}
        </option>
      ))}
    </Select>
  );
};
```

- [ ] **Step 2: `AdminUsersPage.tsx`**

- `ROLE_LABEL` thêm `MANAGER: 'Quản lý cơ sở'`; `ROLE_TONE` thêm `MANAGER: 'info'`; `CREATABLE_ROLES` thêm `'MANAGER'`.
- State bộ lọc: thêm `const [storeFilter, setStoreFilter] = useState<number | null>(null);` và truyền `storeId: storeFilter ?? undefined` vào `adminUserApi.getUsers({...})` (đưa `storeFilter` vào deps của effect tải danh sách); render `<StoreScopeSelect value={storeFilter} onChange={(id) => { setStoreFilter(id); setPage(1); }} />` cạnh các bộ lọc hiện có.
- Bảng: thêm cột "Cơ sở" hiển thị `user.storeName ?? '—'`.
- Form tạo (khối role Select ~dòng 478) và form sửa (~dòng 583): thêm ngay sau Select vai trò:

```tsx
            {(['STAFF', 'SHIPPER', 'MANAGER'] as RoleName[]).includes(form.role) && (
              <StoreScopeSelect
                label="Cơ sở làm việc"
                allowAll={false}
                value={form.storeId ?? null}
                onChange={(storeId) => setForm({ ...form, storeId })}
              />
            )}
```

  và trước khi gửi, validate: nếu vai trò thuộc nhóm trên mà `!form.storeId` → `toast.error('Vui lòng chọn cơ sở làm việc')` và dừng. Gửi `storeId: form.storeId ?? null` trong payload tạo/sửa. Form sửa khởi tạo `storeId: user.storeId ?? null`.
- Control đổi vai trò nhanh (~dòng 623–683): khi chọn STAFF/SHIPPER/MANAGER mà user chưa có cơ sở → mở form sửa thay vì gọi `changeRole` trực tiếp (để chọn cơ sở); `adminUserApi.changeRole(id, role)` gửi thêm `storeId: user.storeId` (sửa `adminUserApi.changeRole(id, role, storeId?)` → body `{ role, storeId }`).

- [ ] **Step 3: `AdminOrdersPage.tsx`**

Thêm state `const [storeId, setStoreId] = useState<number | null>(null);`, render `<StoreScopeSelect value={storeId} onChange={(id) => { setStoreId(id); setPage(1); }} />` trong thanh lọc, truyền `storeId: storeId ?? undefined` vào `staffOrderApi.getOrders(...)` và thêm `storeId` vào deps; bảng thêm cột "Cơ sở" = `order.storeName ?? '—'`.

- [ ] **Step 4: `AdminReportsPage.tsx` + `AdminDashboardPage.tsx`**

- Reports: state `storeId`, `<StoreScopeSelect …/>` cạnh bộ lọc ngày; truyền `storeId ?? undefined` vào `getTopProducts` và `downloadCsv`; thêm mục xuất `REVENUE_BY_STORE` ("Doanh thu theo cơ sở") vào danh sách loại báo cáo; thêm bảng "Doanh thu theo cơ sở" (gọi `adminReportsApi.getRevenueByStore({ fromDate, toDate })`, cột: Cơ sở / Số đơn / Doanh thu với `formatCurrency`) — ẩn khi đang lọc 1 cơ sở.
- Dashboard: state `storeId`, `<StoreScopeSelect …/>` ở `PageHeader actions`; truyền vào `getMetrics(storeId ?? undefined)`, `getRevenueChart(days, storeId ?? undefined)`, `getOrderStatusStats(storeId ?? undefined)`; thêm `storeId` vào deps.

- [ ] **Step 5: Kiểm tra** — chạy cùng Task 15.

---

## Task 15: Frontend — khu `/staff` theo cơ sở: MANAGER, Tình trạng món, ADMIN chọn cơ sở

**Files:**
- Create: `frontend/src/context/storeScopeContextDef.ts`, `frontend/src/context/StoreScopeProvider.tsx`, `frontend/src/context/useStoreScope.ts`, `frontend/src/components/staff/StockAdjustModal.tsx`, `frontend/src/pages/staff/StoreStockPage.tsx`, `frontend/src/pages/manager/ManagerReportsPage.tsx`, `frontend/src/pages/manager/ManagerStaffPage.tsx`
- Modify: `frontend/src/App.tsx`, `frontend/src/components/layout/navItems.ts`, `frontend/src/components/layout/DashboardLayout.tsx`, `frontend/src/pages/staff/StaffMenuPage.tsx`, `frontend/src/pages/staff/StaffOrderQueuePage.tsx`, `frontend/src/components/order/AssignShipperModal.tsx`, `frontend/src/components/layout/RequireAuth.tsx`

**Interfaces:**
- Consumes: `storeInventoryApi`, `storeApi.setAccepting`, `managerApi` (Task 10); `StoreScopeSelect` (Task 14); `useAuth().user.storeId/storeName`.
- Produces:
  - Context `StoreScope { storeId: number | null; storeName: string | null; setStoreId(id: number | null): void; canChoose: boolean }` — STAFF/MANAGER/SHIPPER: cơ sở của mình, `canChoose=false`; ADMIN: chọn bằng `StoreScopeSelect`, lưu `localStorage['bmk_admin_store_scope']` (bọc try/catch).
  - `STAFF_NAV` mới: Hàng đợi đơn, **Tình trạng món** (`/staff/menu` → `StoreStockPage`), Đánh giá. `MANAGER_NAV` = STAFF_NAV + Báo cáo cơ sở (`/staff/reports`) + Nhân viên (`/staff/team`). `navForRole(role)`.
  - `roleHomePath('MANAGER') = '/staff'`.
  - Trang sửa thực đơn chung chuyển sang `/admin/menu` (ADMIN), không còn khối tồn kho.

- [ ] **Step 1: Context phạm vi cơ sở**

`context/storeScopeContextDef.ts`:

```ts
import { createContext } from 'react';

export interface StoreScopeContextType {
  /** Cơ sở đang làm việc; null = ADMIN chưa chọn */
  storeId: number | null;
  storeName: string | null;
  setStore: (storeId: number | null, storeName: string | null) => void;
  /** Chỉ ADMIN được chọn cơ sở; nhân sự cơ sở luôn bị khoá về cơ sở mình */
  canChoose: boolean;
}

export const StoreScopeContext = createContext<StoreScopeContextType | undefined>(undefined);
```

`context/useStoreScope.ts`:

```ts
import { useContext } from 'react';
import { StoreScopeContext, type StoreScopeContextType } from './storeScopeContextDef';

export const useStoreScope = (): StoreScopeContextType => {
  const context = useContext(StoreScopeContext);
  if (!context) throw new Error('useStoreScope must be used within a StoreScopeProvider');
  return context;
};
```

`context/StoreScopeProvider.tsx`:

```tsx
import { useMemo, useState, type ReactNode } from 'react';
import { useAuth } from './useAuth';
import { StoreScopeContext } from './storeScopeContextDef';

const STORAGE_KEY = 'bmk_admin_store_scope';

const readSaved = (): { id: number | null; name: string | null } => {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as { id: number | null; name: string | null }) : { id: null, name: null };
  } catch {
    return { id: null, name: null };
  }
};

/** Cơ sở đang làm việc của khu /staff và /shipper */
export const StoreScopeProvider = ({ children }: { children: ReactNode }) => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [adminChoice, setAdminChoice] = useState(readSaved);

  const value = useMemo(
    () => ({
      storeId: isAdmin ? adminChoice.id : user?.storeId ?? null,
      storeName: isAdmin ? adminChoice.name : user?.storeName ?? null,
      canChoose: isAdmin,
      setStore: (id: number | null, name: string | null) => {
        if (!isAdmin) return;
        setAdminChoice({ id, name });
        try {
          window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ id, name }));
        } catch {
          // Chế độ riêng tư chặn localStorage — vẫn dùng được trong phiên
        }
      },
    }),
    [isAdmin, adminChoice, user?.storeId, user?.storeName]
  );

  return <StoreScopeContext.Provider value={value}>{children}</StoreScopeContext.Provider>;
};
```

- [ ] **Step 2: Menu theo vai trò + layout**

`navItems.ts`:
- Import thêm `BarChart3`, `PackageCheck`, `UsersRound` từ `lucide-react`.
- Thay `STAFF_NAV`:

```ts
export const STAFF_NAV: NavItem[] = [
  { to: '/staff/orders', label: 'Hàng đợi Đơn hàng (POS)', icon: ClipboardList },
  { to: '/staff/menu', label: 'Tình trạng món', icon: PackageCheck },
  { to: '/staff/reviews', label: 'Đánh giá của khách', icon: Star },
];

export const MANAGER_NAV: NavItem[] = [
  ...STAFF_NAV,
  { to: '/staff/reports', label: 'Báo cáo cơ sở', icon: BarChart3 },
  { to: '/staff/team', label: 'Nhân viên', icon: UsersRound },
];

export const MANAGER_BRAND: BrandConfig = {
  name: 'BÁNH MỲ KING',
  sub: 'QUẢN LÝ CƠ SỞ',
  icon: ChefHat,
  roleLabel: 'Quản lý cơ sở',
  navTitle: 'VẬN HÀNH CƠ SỞ',
};
```

- `ADMIN_NAV` thêm `{ to: '/admin/menu', label: 'Thực đơn', icon: UtensilsCrossed },` sau "Danh mục món".
- `roleHomePath`: thêm `case 'MANAGER': return '/staff';`.

`DashboardLayout.tsx`:
- Thêm prop tuỳ chọn `showStore?: boolean`.
- Import `useStoreScope`, `StoreScopeSelect`, `storeApi`, `useToast`, `Button`, icon `PauseCircle`, `PlayCircle`.
- Trong sidebar, ngay dưới khối `dash__brand`, khi `showStore`:

```tsx
        {showStore && (
          <div className="dash__store">
            {scope.canChoose ? (
              <StoreScopeSelect
                label="Cơ sở đang xem"
                allowAll={false}
                value={scope.storeId}
                onChange={(id) => scope.setStore(id, null)}
              />
            ) : (
              <span className="dash__store-name">{scope.storeName ?? 'Chưa được gán cơ sở'}</span>
            )}
          </div>
        )}
```

  (`const scope = useStoreScope();`). Ghi chú: `StoreScopeSelect` chỉ trả id; `storeName` của ADMIN hiển thị qua option đang chọn của select nên truyền `null` là đủ.
- Với MANAGER (và ADMIN đã chọn cơ sở), dưới khối trên thêm nút tạm ngưng:

```tsx
        {showStore && (user?.role === 'MANAGER' || user?.role === 'ADMIN') && scope.storeId != null && (
          <Button
            size="sm"
            variant={accepting ? 'danger' : 'success'}
            icon={accepting ? <PauseCircle size={16} /> : <PlayCircle size={16} />}
            onClick={async () => {
              try {
                const store = await storeApi.setAccepting(scope.storeId as number, !accepting);
                setAccepting(store.acceptingOrders);
                toast.success(store.acceptingOrders ? 'Đã mở lại nhận đơn' : 'Đã tạm ngưng nhận đơn');
              } catch (err) {
                toast.error(err instanceof Error ? err.message : 'Thao tác thất bại');
              }
            }}
          >
            {accepting ? 'Tạm ngưng nhận đơn' : 'Mở lại nhận đơn'}
          </Button>
        )}
```

  với `const [accepting, setAccepting] = useState(true);` và effect tải trạng thái: `storeApi.listPublic().then((stores) => setAccepting(stores.find((s) => s.id === scope.storeId)?.acceptingOrders ?? true))` khi `scope.storeId` đổi.
- CSS (thêm vào cuối `styles/layout.css`):

```css
.dash__store {
  padding: 0 16px 12px;
}

.dash__store-name {
  display: block;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: var(--primary-50);
  color: var(--primary-800);
  font-weight: 600;
  font-size: var(--text-sm);
}
```

- [ ] **Step 3: `components/staff/StockAdjustModal.tsx` (tách từ `StaffMenuPage`)**

Di chuyển nguyên `MOVEMENT_LABEL`, `StockAdjustModalProps`, `StockAdjustModal` từ `StaffMenuPage.tsx` (dòng ~361–510) sang file mới và đổi dữ liệu sang theo cơ sở:
- Props: `{ storeId: number; item: StoreStockItem; onClose: () => void; onAdjusted: (updated: StoreStockItem) => void }`.
- Tải sổ kho: `storeInventoryApi.movements(storeId, item.productId)`.
- Lưu: `const updated = await storeInventoryApi.adjustStock(storeId, item.productId, delta, note.trim() || undefined);` rồi `toast.success(`${updated.productName}: tồn kho còn ${updated.stockQuantity}`)`.
- `const current = item.stockQuantity ?? null;`, tiêu đề dùng `item.productName`.
- `export { StockAdjustModal }` (named export). Import CSS `../../styles/components/staff-menu.css` (class `stockadj__*` đang ở đó).

- [ ] **Step 4: `pages/staff/StoreStockPage.tsx`**

```tsx
import { useCallback, useEffect, useMemo, useState } from 'react';
import { PackageCheck, Search } from 'lucide-react';
import { storeInventoryApi } from '../../api/storeInventoryApi';
import { Badge, Button, EmptyState, Input, PageHeader, Skeleton, useToast } from '../../components/ui';
import { StockAdjustModal } from '../../components/staff/StockAdjustModal';
import { useStoreScope } from '../../context/useStoreScope';
import { formatCurrency } from '../../utils/formatters';
import type { StoreStockItem } from '../../types/store';
import '../../styles/components/staff-menu.css';

/** Tình trạng món tại cơ sở: bật/tắt hết món, nhập/điều chỉnh tồn (spec §5) */
export const StoreStockPage = () => {
  const { storeId, storeName } = useStoreScope();
  const toast = useToast();
  const [items, setItems] = useState<StoreStockItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [stockItem, setStockItem] = useState<StoreStockItem | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(async () => {
    if (storeId == null) return;
    setIsLoading(true);
    try {
      setItems(await storeInventoryApi.list(storeId));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không tải được tình trạng món');
    } finally {
      setIsLoading(false);
    }
  }, [storeId, toast]);

  useEffect(() => {
    void load();
  }, [load]);

  const visible = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    return q ? items.filter((i) => i.productName.toLowerCase().includes(q)) : items;
  }, [items, keyword]);

  const replace = (updated: StoreStockItem) =>
    setItems((prev) => prev.map((i) => (i.productId === updated.productId ? updated : i)));

  const toggle = async (item: StoreStockItem) => {
    if (storeId == null) return;
    setBusyId(item.productId);
    try {
      const updated = await storeInventoryApi.setAvailability(storeId, item.productId, !item.available);
      replace(updated);
      toast.success(`${updated.productName}: ${updated.available ? 'mở bán lại' : 'đã báo hết món'}`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Cập nhật thất bại');
    } finally {
      setBusyId(null);
    }
  };

  if (storeId == null) {
    return (
      <EmptyState
        icon={<PackageCheck size={30} />}
        title="Chưa chọn cơ sở"
        description="Chọn cơ sở ở thanh bên để xem tình trạng món."
      />
    );
  }

  return (
    <>
      <PageHeader
        title="Tình trạng món"
        subtitle={`${storeName ?? 'Cơ sở'} — bật/tắt hết món và quản lý tồn kho tại cơ sở. Món, giá do quản trị viên sửa ở thực đơn chung.`}
      />
      <div className="smenu__actions">
        <Input
          type="search"
          icon={<Search size={16} />}
          placeholder="Tìm món theo tên..."
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
        />
      </div>
      {isLoading ? (
        <Skeleton variant="card" />
      ) : (
        <div className="smenu__grid">
          {visible.map((item) => (
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
                <Button
                  variant={item.available ? 'secondary' : 'danger'}
                  disabled={!item.onChainMenu}
                  loading={busyId === item.productId}
                  onClick={() => void toggle(item)}
                >
                  {item.available ? 'Đang bán — báo hết món' : 'Đang hết — mở bán lại'}
                </Button>
              </div>
            </article>
          ))}
        </div>
      )}
      {stockItem && (
        <StockAdjustModal
          storeId={storeId}
          item={stockItem}
          onClose={() => setStockItem(null)}
          onAdjusted={(updated) => {
            replace(updated);
            setStockItem(null);
          }}
        />
      )}
    </>
  );
};
```

(Class `smenu__actions`, `smenu__grid`, `smenu__card`, `smenu__card--out`, `smenu__name`, `smenu__price`, `smenu__stock-row`, `smenu__stock`, `smenu__stock--low` đều có sẵn trong `staff-menu.css` — chính là class `StaffMenuPage.tsx` đang dùng.)

- [ ] **Step 5: `StaffMenuPage` thành trang thực đơn chung của ADMIN**

Trong `StaffMenuPage.tsx`:
- Xoá state `stockProduct`, khối `smenu__stock-row` (dòng ~262–273), khối render `<StockAdjustModal …/>` (~350–357), toàn bộ phần đã chuyển sang `components/staff/StockAdjustModal.tsx`, prop `showInitialStock` và 2 ô "Tồn ban đầu"/"Ngưỡng cảnh báo" trong form (~617–645), các trường `stockQuantity`/`lowStockThreshold` trong payload tạo/sửa (~304, ~330), import `StockMovement*`.
- Nút bật/tắt `available` (dòng ~278) giữ nguyên nhưng đổi nhãn thành `{product.available ? 'Đang bán toàn chuỗi — ngừng bán' : 'Đã ngừng bán — bán lại toàn chuỗi'}`.

- [ ] **Step 6: Trang Manager**

`pages/manager/ManagerStaffPage.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { UsersRound } from 'lucide-react';
import { managerApi } from '../../api/managerApi';
import { Badge, EmptyState, PageHeader, Skeleton } from '../../components/ui';
import type { UserInfoResponse } from '../../types/auth';

const ROLE_LABEL: Record<string, string> = { STAFF: 'Nhân viên', SHIPPER: 'Tài xế', MANAGER: 'Quản lý' };

/** MANAGER — nhân sự của cơ sở mình (chỉ xem; ADMIN thêm/sửa ở trang Tài khoản) */
export const ManagerStaffPage = () => {
  const [people, setPeople] = useState<UserInfoResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    managerApi
      .staff()
      .then((data) => {
        if (alive) setPeople(data);
      })
      .catch((err) => {
        if (alive) setError(err instanceof Error ? err.message : 'Không tải được nhân sự');
      })
      .finally(() => {
        if (alive) setIsLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  return (
    <>
      <PageHeader title="Nhân viên cơ sở" subtitle="Danh sách chỉ xem. Thêm, sửa tài khoản do quản trị viên thực hiện." />
      {isLoading ? (
        <Skeleton variant="row" />
      ) : error || people.length === 0 ? (
        <EmptyState icon={<UsersRound size={30} />} title="Chưa có nhân sự" description={error ?? 'Cơ sở chưa có nhân viên.'} />
      ) : (
        <section className="card">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Họ tên</th>
                  <th>Vai trò</th>
                  <th>Điện thoại</th>
                  <th>Email</th>
                </tr>
              </thead>
              <tbody>
                {people.map((p) => (
                  <tr key={p.id}>
                    <td>{p.fullName}</td>
                    <td>
                      <Badge tone="info">{ROLE_LABEL[p.role] ?? p.role}</Badge>
                    </td>
                    <td>{p.phone ?? '—'}</td>
                    <td>{p.email}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </>
  );
};
```

`pages/manager/ManagerReportsPage.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { BarChart3 } from 'lucide-react';
import { managerApi } from '../../api/managerApi';
import { EmptyState, PageHeader, Skeleton } from '../../components/ui';
import { formatCurrency } from '../../utils/formatters';
import type { DashboardMetrics, TopProduct } from '../../types/admin';
import '../../styles/components/dashboard.css';

/** MANAGER — chỉ số và món bán chạy của cơ sở mình (30 ngày gần nhất) */
export const ManagerReportsPage = () => {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [top, setTop] = useState<TopProduct[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    Promise.all([managerApi.metrics(), managerApi.topProducts({ limit: 10 })])
      .then(([m, t]) => {
        if (!alive) return;
        setMetrics(m);
        setTop(t);
      })
      .catch((err) => {
        if (alive) setError(err instanceof Error ? err.message : 'Không tải được báo cáo');
      })
      .finally(() => {
        if (alive) setIsLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  if (isLoading) return <Skeleton variant="card" />;
  if (error || !metrics) {
    return <EmptyState icon={<BarChart3 size={30} />} title="Chưa có báo cáo" description={error ?? ''} />;
  }

  const tiles = [
    { label: 'Doanh thu hôm nay', value: formatCurrency(metrics.todayRevenue) },
    { label: 'Đơn hôm nay', value: String(metrics.todayOrders) },
    { label: 'Đang chờ xác nhận', value: String(metrics.pendingOrders) },
    { label: 'Đang xử lý', value: String(metrics.processingOrders) },
    { label: 'Tỉ lệ giao thành công', value: `${metrics.successRate}%` },
    { label: 'Doanh thu tích luỹ', value: formatCurrency(metrics.totalRevenue) },
  ];

  return (
    <>
      <PageHeader title="Báo cáo cơ sở" subtitle="Số liệu của riêng cơ sở bạn quản lý." />
      <div className="adash__kpis">
        {tiles.map((tile) => (
          <div key={tile.label} className="kpi">
            <span className="kpi__label">{tile.label}</span>
            <strong className="kpi__value">{tile.value}</strong>
          </div>
        ))}
      </div>
      <section className="card">
        <div className="card__head">
          <h2 className="card__title">Món bán chạy (30 ngày)</h2>
        </div>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Món</th>
                <th>Số lượng</th>
                <th>Doanh thu</th>
              </tr>
            </thead>
            <tbody>
              {top.map((row) => (
                <tr key={`${row.productId}-${row.productName}`}>
                  <td>{row.productName}</td>
                  <td>{row.quantitySold}</td>
                  <td>{formatCurrency(row.revenue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
};
```

(Class `adash__kpis`, `kpi`, `kpi__label`, `kpi__value` lấy đúng theo `AdminDashboardPage.tsx` / `dashboard.css`.)

- [ ] **Step 7: Route, queue, gán shipper**

`App.tsx`:
- Bọc `<BrowserRouter>` bên trong bằng `<StoreScopeProvider>` (đặt ngay bên trong `<AuthProvider>`).
- Nhóm `/staff`: `roles={['STAFF', 'MANAGER', 'ADMIN']}`; layout dùng menu theo vai trò — thay `<DashboardLayout navItems={STAFF_NAV} brand={STAFF_BRAND} />` bằng component nhỏ:

```tsx
const StaffAreaLayout = () => {
  const { user } = useAuth();
  const isManager = user?.role === 'MANAGER';
  return (
    <DashboardLayout
      navItems={isManager ? MANAGER_NAV : STAFF_NAV}
      brand={isManager ? MANAGER_BRAND : STAFF_BRAND}
      showStore
    />
  );
};
```

  (khai báo trong `App.tsx`, import `useAuth`). Route con: `menu` → `<StoreStockPage />`, thêm `reports` → `<ManagerReportsPage />` và `team` → `<ManagerStaffPage />` (2 route này bọc `<RequireRole roles={['MANAGER', 'ADMIN']} area="Quản lý cơ sở" />`).
- Nhóm `/admin` thêm `<Route path="menu" element={<StaffMenuPage />} />`.
- Nhóm `/shipper` layout thêm `showStore`.

`StaffOrderQueuePage.tsx`: lấy `const { storeId } = useStoreScope();`; gọi `staffOrderApi.getOrderQueue(undefined, 0, 50, storeId ?? undefined)` (đổi `100` → `50` cho khớp giới hạn server) và thêm `storeId` vào deps của `fetchOrders`. Khi `user.role === 'ADMIN' && storeId == null` hiển thị `EmptyState` "Chọn cơ sở ở thanh bên để xem hàng đợi".

`components/order/AssignShipperModal.tsx`: gọi `staffOrderApi.getAvailableShippers(order.storeId ?? undefined)`.

`RequireAuth.tsx`: nếu đang chặn vai trò khác CUSTOMER khỏi `/profile`, thêm `MANAGER` cùng nhóm với STAFF (giữ hành vi hiện tại).

- [ ] **Step 8: Build, lint, commit frontend (người dùng tự chạy)**

Run (`frontend`): `npx tsc -b` → không lỗi.
Run: `npm run build` → `✓ built`.
Run: `npx oxlint src` → không có cảnh báo mới ngoài `set-state-in-effect` sẵn có (so với trước Task 10).

```
git add -A frontend/src
git commit -m "feat(co-so): giao diện chuỗi cơ sở — trang Hệ thống cửa hàng, chọn cơ sở khi thanh toán, quản lý Cơ sở, Tình trạng món, khu Quản lý cơ sở, lọc theo cơ sở"
```

---

## Task 16: Kiểm chứng cuối, migration trên bản sao DB, tài liệu cho team

**Files:**
- Modify: `SETUP.md` (thêm mục "Chuỗi cơ sở — khi pull code"), `docs/superpowers/specs/2026-10-01-multi-store-design.md` (trạng thái → "Đã triển khai")

- [ ] **Step 1: Chạy migration trên bản sao DB**

```
mysqldump -h 127.0.0.1 -P 3307 -u root -p banhmyking > backup_truoc_V11.sql
mysql -h 127.0.0.1 -P 3307 -u root -p -e "CREATE DATABASE banhmyking_copy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -h 127.0.0.1 -P 3307 -u root -p banhmyking_copy < backup_truoc_V11.sql
```

Chạy app trỏ vào bản sao: `./mvnw -B spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:mysql://localhost:3307/banhmyking_copy?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true"`
Expected: log `Successfully applied 3 migrations ... now at version v13`; truy vấn:

```sql
SELECT code, name, address, latitude FROM stores;
SELECT COUNT(*) FROM orders WHERE store_id IS NULL;            -- 0
SELECT COUNT(*) FROM users WHERE role IN ('STAFF','SHIPPER') AND store_id IS NULL; -- 0
SELECT COUNT(*) FROM store_products;                            -- = số món từng quản tồn
```

- [ ] **Step 2: Toàn bộ test + build**

Run: `./mvnw -B test` → BUILD SUCCESS.
Run (`frontend`): `npm run build` → `✓ built`.

- [ ] **Step 3: Thử qua HTTP với 2 cơ sở (DB dev, profile dev có seed)**

Viết script Python tạm (scratchpad, không đưa vào repo) kiểm:
1. `staff2@gmail.com` (CS02) `GET /admin/orders/{mã đơn của CS01}` → 404.
2. `manager@gmail.com` `PATCH /manager/stores/{CS01}/accepting {accepting:false}` → 200; `customer` `GET /delivery/quote?lat&lng` gần CS01 → CS01 có `reasons` chứa `NOT_ACCEPTING`, `recommendedStoreId` ≠ CS01; mở lại → CS01 được đề xuất.
3. Đặt đơn với `storeId` của cơ sở có món bị báo hết (`PATCH /store-inventory/{s}/products/{p}/availability {available:false}`) → 400 kèm "tạm hết".
4. `admin` `GET /admin/dashboard/metrics?storeId=` từng cơ sở → tổng `totalOrders` = giá trị không lọc.
5. `manager` `PUT /admin/orders/{mã PENDING}/store {storeId: CS02, reason}` → 200, lịch sử có dòng "Chuyển từ".
Dọn dữ liệu test (huỷ đơn test, mở lại món, mở lại nhận đơn).

- [ ] **Step 4: Danh sách bấm thử giao diện (gửi người dùng)**

- `/stores`: bản đồ có ghim, nút "Tìm cơ sở gần tôi" sắp xếp lại.
- Thanh toán: ghim địa chỉ → thẻ "Giao từ …"; bấm "Đổi cơ sở" thấy lý do; cơ sở tạm ngưng không chọn được.
- Admin → Cơ sở: thêm cơ sở bằng bản đồ; Admin → Tài khoản: tạo MANAGER phải chọn cơ sở.
- Đăng nhập `manager@gmail.com`: thanh bên hiện tên cơ sở, nút "Tạm ngưng nhận đơn", mục Báo cáo cơ sở, Nhân viên.
- Đăng nhập `staff@gmail.com`: "Tình trạng món" báo hết món/nhập tồn; không còn sửa giá.
- Admin vào `/staff`: chọn cơ sở ở thanh bên mới thấy hàng đợi.

- [ ] **Step 5: `SETUP.md` — thêm mục**

```markdown
## Chuỗi cơ sở (từ nhánh feature/multi-store)

1. **Sao lưu DB trước khi pull** (V11–V13 bỏ cột tồn kho cũ):
   `mysqldump -h 127.0.0.1 -P 3307 -u root -p banhmyking > backup_truoc_V11.sql`
2. `git pull`, chạy backend → log `now at version v13`.
3. `cd frontend && npm install && npm run dev`.
4. ADMIN → **Cơ sở**: kiểm tra "Cơ sở 1" (tạo từ dữ liệu cũ), ghim vị trí, đặt đơn tối thiểu; thêm cơ sở khác.
5. ADMIN → **Tài khoản**: gán cơ sở cho từng STAFF/SHIPPER; tạo tài khoản Quản lý cơ sở (MANAGER).
6. Thực đơn chung (món, giá) giờ ở ADMIN → **Thực đơn**; nhân viên chỉ báo hết món / nhập tồn ở **Tình trạng món**.
```

- [ ] **Step 6: Commit (người dùng tự chạy)**

```
git add SETUP.md docs/superpowers/specs/2026-10-01-multi-store-design.md docs/superpowers/plans/2026-10-01-multi-store.md
git commit -m "docs(co-so): hướng dẫn team khi pull chuỗi cơ sở, cập nhật trạng thái thiết kế"
```
