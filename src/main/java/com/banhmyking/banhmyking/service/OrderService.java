package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.order.CreateOrderRequest;
import com.banhmyking.banhmyking.dto.order.OrderResponse;

import java.util.List;

public interface OrderService {

    /**
     * Tạo đơn hàng từ giỏ hàng hiện tại của user.
     * Toàn bộ thao tác thực hiện trong 1 transaction.
     * Snapshot chính xác tên, giá món, topping, địa chỉ và cột tiền.
     * Tính tiền qua PriceCalculator.
     * Tự động xóa sạch giỏ hàng sau khi tạo đơn thành công.
     */
    OrderResponse createFromCart(Long userId, CreateOrderRequest request);

    /**
     * Lấy chi tiết đơn hàng theo mã orderCode.
     */
    OrderResponse getOrderByCode(Long userId, String orderCode);

    /**
     * Lấy danh sách các đơn hàng của user.
     */
    List<OrderResponse> getUserOrders(Long userId);

    /**
     * Hủy đơn hàng tuân thủ phân quyền (AC 3):
     * - Customer: PENDING hoặc CONFIRMED, có check ownership.
     * - Staff/Admin: Tới bước READY_FOR_PICKUP, bắt buộc phải kèm lý do hủy.
     * Tự động ghi 1 dòng vào order_status_history (AC 2).
     */
    OrderResponse cancelOrder(Long userId, String orderCode, com.banhmyking.banhmyking.dto.order.CancelOrderRequest request);

    /**
     * Cập nhật trạng thái đơn hàng (AC 1):
     * - Kiểm tra bằng OrderStatusValidator, chặn nhảy trạng thái.
     * - FAILED chỉ từ DELIVERING.
     * Tự động ghi 1 dòng vào order_status_history (AC 2).
     */
    OrderResponse updateOrderStatus(Long userId, String orderCode, com.banhmyking.banhmyking.dto.order.UpdateOrderStatusRequest request);

    /**
     * Lấy lịch sử các lần chuyển trạng thái của đơn hàng.
     */
    List<com.banhmyking.banhmyking.dto.order.OrderStatusHistoryResponse> getOrderStatusHistory(Long userId, String orderCode);
}
