package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.payment.PaymentResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Payment;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.PaymentMethod;
import com.banhmyking.banhmyking.enums.PaymentStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.PaymentRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User customer;
    private User otherCustomer;
    private User shipper;
    private User otherShipper;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setId(10L);
        customer.setRole(RoleName.CUSTOMER);
        customer.setFullName("Khách Hàng A");

        otherCustomer = new User();
        otherCustomer.setId(99L);
        otherCustomer.setRole(RoleName.CUSTOMER);
        otherCustomer.setFullName("Khách Hàng Khác");

        shipper = new User();
        shipper.setId(20L);
        shipper.setRole(RoleName.SHIPPER);
        shipper.setFullName("Tài Xế B");

        otherShipper = new User();
        otherShipper.setId(30L);
        otherShipper.setRole(RoleName.SHIPPER);
        otherShipper.setFullName("Tài Xế Khác");

        testOrder = new Order();
        testOrder.setId(100L);
        testOrder.setOrderCode("BMK-20260912-TEST1");
        testOrder.setUser(customer);
        testOrder.setShipper(shipper);
        testOrder.setStatus(OrderStatus.PENDING);
        testOrder.setTotal(BigDecimal.valueOf(115000));
        testOrder.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("AC 2: Tự động tạo bản ghi Payment với trạng thái PENDING khi chốt đơn")
    void createPendingPayment_success() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        Payment created = paymentService.createPendingPayment(testOrder, PaymentMethod.COD, BigDecimal.valueOf(115000));

        assertThat(created).isNotNull();
        assertThat(created.getMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(created.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(115000));
        assertThat(created.getOrder()).isEqualTo(testOrder);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getMethod()).isEqualTo(PaymentMethod.COD);
    }

    @Test
    @DisplayName("Không được reset payment đã PAID về PENDING khi tái sử dụng cổng thanh toán")
    void createPendingPayment_whenAlreadyPaid_shouldThrowConflictAndNotTouchRecord() {
        Payment paid = new Payment();
        paid.setId(7L);
        paid.setOrder(testOrder);
        paid.setStatus(PaymentStatus.PAID);
        paid.setMethod(PaymentMethod.BANK_TRANSFER);
        paid.setAmount(BigDecimal.valueOf(115000));
        paid.setPaidAt(java.time.LocalDateTime.now());

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> paymentService.createPendingPayment(testOrder, PaymentMethod.COD, BigDecimal.valueOf(115000)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã thanh toán");

        // Bản ghi PAID + paidAt phải nguyên vẹn
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Payment FAILED vẫn được phép tạo lại (cho khách thử lại thanh toán)")
    void createPendingPayment_whenFailed_canRetry() {
        Payment failed = new Payment();
        failed.setId(8L);
        failed.setOrder(testOrder);
        failed.setStatus(PaymentStatus.FAILED);
        failed.setAmount(BigDecimal.valueOf(115000));

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(failed));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment retried = paymentService.createPendingPayment(testOrder, PaymentMethod.BANK_TRANSFER, BigDecimal.valueOf(115000));

        assertThat(retried.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(retried.getId()).isEqualTo(8L); // dùng lại bản ghi cũ
    }

    @Test
    @DisplayName("AC 3: Cập nhật Payment sang PAID ngay khi Shipper xác nhận DELIVERED")
    void markPaymentAsPaid_codPending_shouldTransitionToPaid() {
        Payment pendingPayment = new Payment();
        pendingPayment.setId(1L);
        pendingPayment.setOrder(testOrder);
        pendingPayment.setMethod(PaymentMethod.COD);
        pendingPayment.setStatus(PaymentStatus.PENDING);
        pendingPayment.setAmount(BigDecimal.valueOf(115000));
        testOrder.setPayment(pendingPayment);

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment paidPayment = paymentService.markPaymentAsPaid(100L);

        assertThat(paidPayment).isNotNull();
        assertThat(paidPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paidPayment.getPaidAt()).isNotNull();

        verify(paymentRepository).save(pendingPayment);
    }

    @Test
    @DisplayName("AC 3: markPaymentAsPaid idempotent khi payment đã PAID")
    void markPaymentAsPaid_alreadyPaid_shouldDoNothing() {
        Payment paidPayment = new Payment();
        paidPayment.setId(1L);
        paidPayment.setOrder(testOrder);
        paidPayment.setMethod(PaymentMethod.COD);
        paidPayment.setStatus(PaymentStatus.PAID);
        paidPayment.setPaidAt(LocalDateTime.now().minusHours(1));
        paidPayment.setAmount(BigDecimal.valueOf(115000));
        testOrder.setPayment(paidPayment);

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(paidPayment));

        Payment result = paymentService.markPaymentAsPaid(100L);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("getPaymentByOrderCode: Customer xem thông tin thanh toán của chính mình thành công")
    void getPaymentByOrderCode_asOwnerCustomer_success() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrder(testOrder);
        payment.setMethod(PaymentMethod.COD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(BigDecimal.valueOf(115000));
        payment.setCreatedAt(LocalDateTime.now());
        testOrder.setPayment(payment);

        when(orderRepository.findByOrderCode("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByOrderCode(10L, "BMK-20260912-TEST1");

        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).isEqualTo("BMK-20260912-TEST1");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.getMethod()).isEqualTo(PaymentMethod.COD);
    }

    @Test
    @DisplayName("getPaymentByOrderCode: Customer khác xem thanh toán đơn hàng bị chặn ResourceNotFound (chống IDOR)")
    void getPaymentByOrderCode_asOtherCustomer_shouldThrowNotFound() {
        when(orderRepository.findByOrderCode("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));
        when(userRepository.findById(99L)).thenReturn(Optional.of(otherCustomer));

        assertThatThrownBy(() -> paymentService.getPaymentByOrderCode(99L, "BMK-20260912-TEST1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getPaymentByOrderCode: Shipper khác (không được phân công) xem thanh toán bị 403 Forbidden")
    void getPaymentByOrderCode_asOtherShipper_shouldThrowForbidden() {
        when(userRepository.findById(30L)).thenReturn(Optional.of(otherShipper));
        when(orderRepository.findByOrderCode("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() -> paymentService.getPaymentByOrderCode(30L, "BMK-20260912-TEST1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("getPaymentByOrderCode: Shipper được gán có quyền xem thông tin thanh toán")
    void getPaymentByOrderCode_asAssignedShipper_success() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrder(testOrder);
        payment.setMethod(PaymentMethod.COD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(BigDecimal.valueOf(115000));
        payment.setCreatedAt(LocalDateTime.now());
        testOrder.setPayment(payment);

        when(userRepository.findById(20L)).thenReturn(Optional.of(shipper));
        when(orderRepository.findByOrderCode("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPaymentByOrderCode(20L, "BMK-20260912-TEST1");

        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).isEqualTo("BMK-20260912-TEST1");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }
}
