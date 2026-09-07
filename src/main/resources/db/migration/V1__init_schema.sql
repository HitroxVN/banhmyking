-- V1__init_schema.sql — toàn bộ schema, khớp entity pass 2026-09-07
-- Quy ước: BIGINT PK, DECIMAL(12,2) tiền, utf8mb4, enum VARCHAR, xoá mềm is_deleted

-- ============================== AUTH ==============================

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255)  NOT NULL,
    password      VARCHAR(255)  NOT NULL,
    full_name     VARCHAR(100)  NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(20)   NOT NULL,
    is_deleted    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    token_hash  VARCHAR(64)   NOT NULL,
    expires_at  DATETIME(6)   NOT NULL,
    revoked_at  DATETIME(6),
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE addresses (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT        NOT NULL,
    receiver_name  VARCHAR(100)  NOT NULL,
    receiver_phone VARCHAR(20)   NOT NULL,
    full_address   VARCHAR(500)  NOT NULL,
    is_default     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================ CATALOG =============================

CREATE TABLE categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(500),
    sort_order  INT           NOT NULL DEFAULT 0,
    is_deleted  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6),
    CONSTRAINT uk_categories_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE products (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id  BIGINT         NOT NULL,
    name         VARCHAR(200)   NOT NULL,
    description  TEXT,
    image_url    VARCHAR(500),
    price        DECIMAL(12, 2) NOT NULL,
    is_available BOOLEAN        NOT NULL DEFAULT TRUE,
    is_featured  BOOLEAN        NOT NULL DEFAULT FALSE,
    is_deleted   BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    INDEX idx_products_category (category_id),
    INDEX idx_products_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE product_options (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT         NOT NULL,
    name         VARCHAR(100)   NOT NULL,
    extra_price  DECIMAL(12, 2) NOT NULL,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6),
    CONSTRAINT fk_product_options_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================== CART ==============================

CREATE TABLE carts (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT uk_carts_user UNIQUE (user_id),
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE cart_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE cart_item_options (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_item_id      BIGINT      NOT NULL,
    product_option_id BIGINT      NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6),
    CONSTRAINT fk_cart_item_options_item FOREIGN KEY (cart_item_id) REFERENCES cart_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_options_option FOREIGN KEY (product_option_id) REFERENCES product_options (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================= ORDER ==============================

CREATE TABLE orders (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_code       VARCHAR(30)      NOT NULL,
    user_id          BIGINT           NOT NULL,
    shipper_id       BIGINT,
    status           VARCHAR(30)      NOT NULL,
    receiver_name    VARCHAR(100)     NOT NULL,
    receiver_phone   VARCHAR(20)      NOT NULL,
    shipping_address VARCHAR(500)     NOT NULL,
    subtotal         DECIMAL(12, 2)   NOT NULL,
    shipping_fee     DECIMAL(12, 2)   NOT NULL,
    discount_amount  DECIMAL(12, 2)   NOT NULL,
    total            DECIMAL(12, 2)   NOT NULL,
    promotion_code   VARCHAR(50),
    note             VARCHAR(500),
    cancel_reason    VARCHAR(300),
    delivered_at     DATETIME(6),
    created_at       DATETIME(6)      NOT NULL,
    updated_at       DATETIME(6),
    CONSTRAINT uk_orders_code UNIQUE (order_code),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_shipper FOREIGN KEY (shipper_id) REFERENCES users (id),
    INDEX idx_orders_user_created (user_id, created_at),
    INDEX idx_orders_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE order_items (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT         NOT NULL,
    product_id   BIGINT,
    product_name VARCHAR(200)   NOT NULL,
    unit_price   DECIMAL(12, 2) NOT NULL,
    quantity     INT            NOT NULL,
    line_total   DECIMAL(12, 2) NOT NULL,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE order_item_options (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT         NOT NULL,
    option_name   VARCHAR(100)   NOT NULL,
    option_price  DECIMAL(12, 2) NOT NULL,
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6),
    CONSTRAINT fk_order_item_options_item FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE order_status_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT       NOT NULL,
    from_status VARCHAR(30)  NOT NULL,
    to_status   VARCHAR(30)  NOT NULL,
    changed_by  BIGINT,
    note        VARCHAR(300),
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6),
    CONSTRAINT fk_osh_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_osh_changed_by FOREIGN KEY (changed_by) REFERENCES users (id),
    INDEX idx_osh_order (order_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE payments (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id       BIGINT         NOT NULL,
    method         VARCHAR(20)    NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    amount         DECIMAL(12, 2) NOT NULL,
    gateway_txn_id VARCHAR(100),
    paid_at        DATETIME(6),
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6),
    CONSTRAINT uk_payments_order UNIQUE (order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ========================== PROMOTION =============================

CREATE TABLE promotions (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                 VARCHAR(50)     NOT NULL,
    description          VARCHAR(200)    NOT NULL,
    discount_type        VARCHAR(20)     NOT NULL,
    value                DECIMAL(12, 2)  NOT NULL,
    max_discount_amount  DECIMAL(12, 2),
    min_order_amount     DECIMAL(12, 2)  NOT NULL DEFAULT 0,
    starts_at            DATETIME(6)     NOT NULL,
    ends_at              DATETIME(6)     NOT NULL,
    max_usage            INT             NOT NULL DEFAULT 0,
    used_count           INT             NOT NULL DEFAULT 0,
    is_active            BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at           DATETIME(6)     NOT NULL,
    updated_at           DATETIME(6),
    CONSTRAINT uk_promotions_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE promotion_usages (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    promotion_id     BIGINT         NOT NULL,
    user_id          BIGINT         NOT NULL,
    order_id         BIGINT         NOT NULL,
    discount_applied DECIMAL(12, 2) NOT NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6),
    CONSTRAINT uk_promotion_user UNIQUE (promotion_id, user_id),
    CONSTRAINT fk_pu_promotion FOREIGN KEY (promotion_id) REFERENCES promotions (id),
    CONSTRAINT fk_pu_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_pu_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =========================== REVIEW ===============================

CREATE TABLE reviews (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id    BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    order_item_id BIGINT      NOT NULL,
    rating        INT         NOT NULL,
    comment       TEXT,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6),
    CONSTRAINT uk_review_order_item UNIQUE (order_item_id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
