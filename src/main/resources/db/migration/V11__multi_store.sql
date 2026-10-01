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
