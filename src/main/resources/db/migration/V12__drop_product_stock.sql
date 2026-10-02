-- V12: tồn kho đã chuyển sang store_products (V11) — bỏ cột tồn ở products.
-- Mọi biến động kho giờ luôn ghi store_id → siết NOT NULL.
ALTER TABLE products DROP CONSTRAINT chk_products_stock;
ALTER TABLE products
    DROP COLUMN stock_quantity,
    DROP COLUMN low_stock_threshold;

ALTER TABLE inventory_movements MODIFY COLUMN store_id BIGINT NOT NULL;
