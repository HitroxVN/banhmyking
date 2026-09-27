-- V6__refund_and_idempotency.sql — khép kín dòng tiền + chống double-submit
-- 1) payments: lưu vết hoàn tiền (số tiền / lý do / ai / lúc nào) + unique gateway_txn_id để webhook dedup.
-- 2) orders: idempotency_key để chặn khách bấm "Đặt hàng" hai lần thành hai đơn.

ALTER TABLE payments
    ADD COLUMN refund_amount DECIMAL(12, 2) NULL,
    ADD COLUMN refund_reason VARCHAR(300) NULL,
    ADD COLUMN refunded_at   DATETIME(6)   NULL,
    ADD COLUMN refunded_by   BIGINT        NULL,
    ADD CONSTRAINT fk_payments_refunded_by FOREIGN KEY (refunded_by) REFERENCES users (id),
    -- NULL cho phép nhiều dòng (COD chưa có mã giao dịch); chỉ chặn trùng mã thật.
    ADD CONSTRAINT uk_payments_gateway_txn UNIQUE (gateway_txn_id);

ALTER TABLE orders
    ADD COLUMN idempotency_key VARCHAR(64) NULL,
    ADD CONSTRAINT uk_orders_idempotency UNIQUE (idempotency_key);
