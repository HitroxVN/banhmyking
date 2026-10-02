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
