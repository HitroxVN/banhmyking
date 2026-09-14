package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.payment.PaymentResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Payment;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.PaymentMethod;
import com.banhmyking.banhmyking.enums.PaymentStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.PaymentRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderCode(Long userId, String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        assertCanViewOrderPayment(order, actor, userId, "Không tìm thấy đơn hàng với mã: " + orderCode);

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin thanh toán cho đơn hàng: " + orderCode));

        return toPaymentResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin thanh toán với ID: " + paymentId));

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        assertCanViewOrderPayment(payment.getOrder(), actor, userId,
                "Không tìm thấy thông tin thanh toán với ID: " + paymentId);

        return toPaymentResponse(payment);
    }

    /**
     * Chặn đọc payment của đơn không liên quan:
     * CUSTOMER chỉ xem đơn của mình (mask NOT_FOUND), SHIPPER chỉ xem đơn được phân công (403).
     * STAFF/ADMIN xem tự do.
     */
    private void assertCanViewOrderPayment(Order order, User actor, Long userId, String notFoundMessage) {
        if (actor.getRole() == RoleName.CUSTOMER
                && (order == null || order.getUser() == null || !order.getUser().getId().equals(userId))) {
            throw new ResourceNotFoundException(notFoundMessage);
        }
        if (actor.getRole() == RoleName.SHIPPER
                && (order == null || order.getShipper() == null || !order.getShipper().getId().equals(userId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không được phân công giao đơn hàng này");
        }
    }

    @Override
    @Transactional
    public Payment createPendingPayment(Order order, PaymentMethod method, BigDecimal amount) {
        log.info("Creating pending payment for order {} with method {} and amount {}",
                order.getOrderCode(), method, amount);

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseGet(() -> {
                    Payment p = new Payment();
                    p.setOrder(order);
                    return p;
                });

        // Không được reset thanh toán đã hoàn tất về PENDING (mất bằng chứng đã thu tiền).
        // PAID/REFUNDED là trạng thái cuối — tái sử dụng cổng thanh toán chỉ cho phép từ PENDING/FAILED.
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Đơn " + order.getOrderCode() + " đã thanh toán, không thể tạo lại phiếu chờ");
        }

        payment.setMethod(method != null ? method : PaymentMethod.COD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(amount);
        payment.setPaidAt(null);

        Payment saved = paymentRepository.save(payment);
        order.setPayment(saved);
        log.info("Payment ID {} created/updated for order {} with status PENDING", saved.getId(), order.getOrderCode());
        return saved;
    }

    @Override
    @Transactional
    public Payment markPaymentAsPaid(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElse(null);

        if (payment == null) {
            log.warn("Payment not found for orderId {}", orderId);
            return null;
        }

        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
            Payment saved = paymentRepository.save(payment);
            log.info("Payment ID {} for orderId {} updated to PAID at {}",
                    saved.getId(), orderId, saved.getPaidAt());
            return saved;
        }

        return payment;
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        if (payment == null) {
            return null;
        }

        Order order = payment.getOrder();
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(order != null ? order.getId() : null)
                .orderCode(order != null ? order.getOrderCode() : null)
                .method(payment.getMethod())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .paidAt(payment.getPaidAt())
                .gatewayTxnId(payment.getGatewayTxnId())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
