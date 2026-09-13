package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.payment.PaymentResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Payment;
import com.banhmyking.banhmyking.enums.PaymentMethod;

import java.math.BigDecimal;

public interface PaymentService {

    /**
     * Tra cứu thông tin thanh toán theo mã đơn hàng (có kiểm tra quyền sở hữu).
     */
    PaymentResponse getPaymentByOrderCode(Long userId, String orderCode);

    /**
     * Tra cứu thông tin thanh toán theo Payment ID.
     */
    PaymentResponse getPaymentById(Long userId, Long paymentId);

    /**
     * Tự động khởi tạo bản ghi Payment với trạng thái PENDING khi chốt đơn.
     */
    Payment createPendingPayment(Order order, PaymentMethod method, BigDecimal amount);

    /**
     * Cập nhật Payment sang PAID khi đơn hàng giao thành công (DELIVERED).
     */
    Payment markPaymentAsPaid(Long orderId);
}
