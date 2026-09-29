-- V7__batch_schema.sql — tồn kho, nhóm option, ảnh sản phẩm, thông báo, kiểm duyệt review,
-- wishlist + điểm tích luỹ, cột đơn giao, index/constraint còn thiếu.

-- ============================ TỒN KHO =============================
-- stock_quantity NULL = không quản tồn.
ALTER TABLE products
    ADD COLUMN stock_quantity      INT NULL,
    ADD COLUMN low_stock_threshold INT NOT NULL DEFAULT 5;

CREATE TABLE inventory_movements (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    change_qty INT          NOT NULL,
    reason     VARCHAR(30)  NOT NULL,
    order_id   BIGINT       NULL,
    note       VARCHAR(300) NULL,
    created_by BIGINT       NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_inv_mov_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inv_mov_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_inv_mov_user FOREIGN KEY (created_by) REFERENCES users (id),
    INDEX idx_inv_mov_product_created (product_id, created_at),
    INDEX idx_inv_mov_order_reason (order_id, reason)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ======================== BỘ ẢNH SẢN PHẨM =========================
CREATE TABLE product_images (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    INDEX idx_product_images_product (product_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =========================== NHÓM OPTION ==========================
CREATE TABLE option_groups (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    is_required BOOLEAN      NOT NULL DEFAULT FALSE,
    max_choices INT          NOT NULL DEFAULT 0,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6),
    CONSTRAINT fk_option_groups_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    INDEX idx_option_groups_product (product_id, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE product_options
    ADD COLUMN group_id BIGINT NULL,
    ADD CONSTRAINT fk_product_options_group FOREIGN KEY (group_id) REFERENCES option_groups (id) ON DELETE CASCADE;

-- ============================ THÔNG BÁO ===========================
CREATE TABLE notifications (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(500) NULL,
    order_id   BIGINT       NULL,
    read_at    DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_order FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX idx_notifications_user_read (user_id, read_at, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ======================== KIỂM DUYỆT REVIEW =======================
ALTER TABLE reviews
    ADD COLUMN status     VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    ADD COLUMN reply      TEXT        NULL,
    ADD COLUMN replied_at DATETIME(6) NULL;

CREATE TABLE review_images (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id  BIGINT       NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_review_images_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    INDEX idx_review_images_review (review_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ======================== WISHLIST + ĐIỂM =========================
CREATE TABLE favorites (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT uk_favorites_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorites_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE loyalty_points (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    order_id      BIGINT       NULL,
    change_points INT          NOT NULL,
    reason        VARCHAR(30)  NOT NULL,
    note          VARCHAR(300) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6),
    CONSTRAINT fk_loyalty_points_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_loyalty_points_order FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX idx_loyalty_points_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ========================== CỘT ĐƠN GIAO ==========================
ALTER TABLE orders
    ADD COLUMN delivery_slot  VARCHAR(50)  NULL,
    ADD COLUMN delivery_photo VARCHAR(500) NULL;

-- ============================== INDEX =============================
ALTER TABLE promotions
    ADD INDEX idx_promotions_active_window (is_active, starts_at, ends_at);

ALTER TABLE orders
    ADD INDEX idx_orders_shipper_status (shipper_id, status);

ALTER TABLE products
    ADD INDEX idx_products_category_price (category_id, price);

-- ======================= FK ON DELETE CASCADE =====================
-- MySQL không cho DROP và ADD cùng tên FK trong một câu ALTER (lỗi 1826), nên phải tách đôi.
ALTER TABLE order_status_history DROP FOREIGN KEY fk_osh_order;
ALTER TABLE order_status_history
    ADD CONSTRAINT fk_osh_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE;

ALTER TABLE payments DROP FOREIGN KEY fk_payments_order;
ALTER TABLE payments
    ADD CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE;

ALTER TABLE reviews DROP FOREIGN KEY fk_reviews_order_item;
ALTER TABLE reviews
    ADD CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE CASCADE;

ALTER TABLE promotion_usages DROP FOREIGN KEY fk_pu_order;
ALTER TABLE promotion_usages
    ADD CONSTRAINT fk_pu_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE;

-- ============================== CHECK =============================
ALTER TABLE orders
    ADD CONSTRAINT chk_orders_amounts
        CHECK (subtotal >= 0 AND shipping_fee >= 0 AND discount_amount >= 0 AND total >= 0);

ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_quantity CHECK (quantity > 0);

ALTER TABLE promotions
    ADD CONSTRAINT chk_promotions_usage
        CHECK (used_count >= 0 AND (max_usage <= 0 OR used_count <= max_usage));

ALTER TABLE products
    ADD CONSTRAINT chk_products_stock CHECK (stock_quantity IS NULL OR stock_quantity >= 0);
