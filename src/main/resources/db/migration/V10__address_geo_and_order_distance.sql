-- V10: chọn địa chỉ trên bản đồ + server tự tính phí ship theo khoảng cách (plan.md §2.2, §5.1 P0)
-- Mọi cột mới đều NULL được: địa chỉ/đơn cũ không có toạ độ vẫn dùng bình thường,
-- phí ship khi đó tính theo khu vực như trước.

-- Sổ địa chỉ: tách ô theo địa giới 2 cấp (sau sắp xếp 2025) + toạ độ ghim trên bản đồ.
-- full_address vẫn là ô tổng (người dùng sửa tay được), các ô tách chỉ để điền/hiển thị.
ALTER TABLE addresses
    ADD COLUMN street    VARCHAR(255)  NULL AFTER full_address,
    ADD COLUMN ward      VARCHAR(100)  NULL AFTER street,
    ADD COLUMN province  VARCHAR(100)  NULL AFTER ward,
    ADD COLUMN latitude  DECIMAL(9, 6) NULL AFTER province,
    ADD COLUMN longitude DECIMAL(9, 6) NULL AFTER latitude;

-- Đơn hàng: snapshot khoảng cách để kiểm toán phí ship, và toạ độ giao để shipper chỉ đường.
ALTER TABLE orders
    ADD COLUMN distance_km        DECIMAL(6, 2) NULL AFTER shipping_address,
    ADD COLUMN delivery_latitude  DECIMAL(9, 6) NULL AFTER distance_km,
    ADD COLUMN delivery_longitude DECIMAL(9, 6) NULL AFTER delivery_latitude;
