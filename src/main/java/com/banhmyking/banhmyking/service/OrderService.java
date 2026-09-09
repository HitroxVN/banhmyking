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
}
