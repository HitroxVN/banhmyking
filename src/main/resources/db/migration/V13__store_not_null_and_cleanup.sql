-- V13: mọi đơn mới luôn có cơ sở (Task 5) → siết NOT NULL.
-- Vị trí quán chuyển sang bảng stores (V11) → bỏ 3 khoá cài đặt cũ.
ALTER TABLE orders MODIFY COLUMN store_id BIGINT NOT NULL;

DELETE FROM site_settings WHERE setting_key IN ('storeLatitude', 'storeLongitude', 'deliveryMaxRadiusKm');
