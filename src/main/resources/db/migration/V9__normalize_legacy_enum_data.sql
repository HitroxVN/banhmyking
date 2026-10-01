-- V9: chuẩn hoá dữ liệu cũ nhập tay (từ bản dump trước khi có Flyway) về đúng giá trị enum Java.
-- Một dòng sai (vd payments.method = 'VNPAY') làm Hibernate ném "No enum constant" → cả API danh sách
-- đơn của staff trả 500, đơn mới cũng không hiện. Chỉ UPDATE dữ liệu, không đổi cấu trúc;
-- DB sạch hoặc đã sửa tay thì các câu dưới không đụng dòng nào → chạy lại ở mọi máy đều an toàn.

-- payments.method: các ví điện tử cũ gom về E_WALLET
UPDATE payments
SET method = 'E_WALLET'
WHERE method IN ('MOMO', 'VNPAY', 'ZALOPAY', 'SHOPEEPAY', 'WALLET', 'EWALLET');

-- payments.status: "thành công" của dữ liệu cũ = đã thu tiền
UPDATE payments
SET status = 'PAID'
WHERE status IN ('SUCCESS', 'COMPLETED', 'DONE');

-- promotions.discount_type: 'FIXED' cũ — mã freeship thành FREE_SHIP, còn lại là giảm số tiền cố định
UPDATE promotions
SET discount_type = 'FREE_SHIP'
WHERE discount_type = 'FIXED' AND UPPER(code) LIKE '%FREESHIP%';

UPDATE promotions
SET discount_type = 'FIXED_AMOUNT'
WHERE discount_type = 'FIXED';
