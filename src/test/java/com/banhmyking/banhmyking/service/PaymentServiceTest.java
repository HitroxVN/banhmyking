package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.payment.PaymentResponse;
import com.banhmyking.banhmyking.dto.payment.ProcessPaymentRequest;
import com.banhmyking.banhmyking.dto.payment.SepayWebhookRequest;
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
import org.springframework.test.util.ReflectionTestUtils;

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

    /** Key SePay giả lập — webhook là fail-closed nên test phải cấu hình key và gửi kèm header. */
    private static final String SEPAY_KEY = "test-sepay-key";
    private static final String SEPAY_AUTH = "Apikey " + SEPAY_KEY;

    private User customer;
    private User otherCustomer;
    private User shipper;
    private User otherShipper;
    private User staff;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        // Webhook SePay là fail-closed: phải có key cấu hình mới nhận request
        ReflectionTestUtils.setField(paymentService, "sepayApiKey", SEPAY_KEY);
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

        staff = new User();
        staff.setId(40L);
        staff.setRole(RoleName.STAFF);
        staff.setFullName("Nhân Viên C");

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

    @Test
    @DisplayName("processPayment: STAFF đối soát chuyển khoản -> Payment PAID và Order CONFIRMED")
    void processPayment_withBankTransfer_byStaff_success() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrder(testOrder);
        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(BigDecimal.valueOf(115000));
        testOrder.setPayment(payment);
        testOrder.setStatus(OrderStatus.PENDING);

        when(userRepository.findById(40L)).thenReturn(Optional.of(staff));
        when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        com.banhmyking.banhmyking.dto.payment.ProcessPaymentRequest req =
                com.banhmyking.banhmyking.dto.payment.ProcessPaymentRequest.builder()
                        .method(PaymentMethod.BANK_TRANSFER)
                        .transactionRef("TXN-TEST-12345")
                        .build();

        PaymentResponse res = paymentService.processPayment(40L, "BMK-20260912-TEST1", req);

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(res.getGatewayTxnId()).isEqualTo("TXN-TEST-12345");
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("processPayment: KHÁCH tự xác nhận chuyển khoản -> 403, tiền chưa vào thì đơn không được đánh PAID")
    void processPayment_withBankTransfer_byCustomer_forbidden() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrder(testOrder);
        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(BigDecimal.valueOf(115000));
        testOrder.setPayment(payment);
        testOrder.setStatus(OrderStatus.PENDING);

        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));

        com.banhmyking.banhmyking.dto.payment.ProcessPaymentRequest req =
                com.banhmyking.banhmyking.dto.payment.ProcessPaymentRequest.builder()
                        .method(PaymentMethod.BANK_TRANSFER)
                        .transactionRef("TXN-KHAI-MAN-99999")
                        .build();

        assertThatThrownBy(() -> paymentService.processPayment(10L, "BMK-20260912-TEST1", req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chuyển khoản");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaidAt()).isNull();
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("processPayment: Cờ simulateFailure = true -> Ném lỗi nghiệp vụ từ chối giao dịch")
    void processPayment_withSimulateFailure_throwsBusinessException() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));

        com.banhmyking.banhmyking.dto.payment.ProcessPaymentRequest req =
                com.banhmyking.banhmyking.dto.payment.ProcessPaymentRequest.builder()
                        .method(PaymentMethod.E_WALLET)
                        .simulateFailure(true)
                        .build();

        assertThatThrownBy(() -> paymentService.processPayment(10L, "BMK-20260912-TEST1", req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("từ chối");
    }

    @Test
    @DisplayName("processPayment: Chọn COD -> Payment giữ PENDING")
    void processPayment_withCod_keepsPending() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrder(testOrder);
        payment.setAmount(BigDecimal.valueOf(115000));
        testOrder.setPayment(payment);

        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        com.banhmyking.banhmyking.dto.payment.ProcessPaymentRequest req =
                com.banhmyking.banhmyking.dto.payment.ProcessPaymentRequest.builder()
                        .method(PaymentMethod.COD)
                        .build();

        PaymentResponse res = paymentService.processPayment(10L, "BMK-20260912-TEST1", req);

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(res.getMethod()).isEqualTo(PaymentMethod.COD);
    }

    @Test
    @DisplayName("processSepayWebhook: Webhook SePay thành công chuyển Payment sang PAID và Order sang CONFIRMED")
    void processSepayWebhook_success() {
        when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        SepayWebhookRequest req = SepayWebhookRequest.builder()
                .id(92704L)
                .gateway("Techcombank")
                .accountNumber("8888332999")
                .content("Thanh toan don BMK-20260912-TEST1 banh my king")
                .transferType("in")
                .transferAmount(BigDecimal.valueOf(115000))
                .referenceCode("FT26258012345678")
                .build();

        PaymentResponse res = paymentService.processSepayWebhook(SEPAY_AUTH, req);

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(res.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(res.getGatewayTxnId()).isEqualTo("FT26258012345678");
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("processSepayWebhook: Chưa cấu hình API Key -> từ chối (fail-closed, không được bỏ qua kiểm tra)")
    void processSepayWebhook_missingApiKeyConfig_rejected() {
        ReflectionTestUtils.setField(paymentService, "sepayApiKey", "");

        SepayWebhookRequest req = SepayWebhookRequest.builder()
                .id(92707L)
                .gateway("Techcombank")
                .content("BMK-20260912-TEST1")
                .transferType("in")
                .transferAmount(BigDecimal.valueOf(115000))
                .referenceCode("FT26258012345678")
                .build();

        assertThatThrownBy(() -> paymentService.processSepayWebhook(SEPAY_AUTH, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key");

        verifyNoInteractions(orderRepository, paymentRepository);
    }

    @Test
    @DisplayName("processSepayWebhook: Sai API Key -> từ chối, không đụng tới đơn hàng")
    void processSepayWebhook_wrongApiKey_rejected() {
        SepayWebhookRequest req = SepayWebhookRequest.builder()
                .id(92708L)
                .gateway("Techcombank")
                .content("BMK-20260912-TEST1")
                .transferType("in")
                .transferAmount(BigDecimal.valueOf(115000))
                .build();

        assertThatThrownBy(() -> paymentService.processSepayWebhook("Apikey key-sai", req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API Key");

        verifyNoInteractions(orderRepository, paymentRepository);
    }

    @Test
    @DisplayName("processSepayWebhook: Nhận dạng mã đơn không có gạch ngang BMK20260912TEST1")
    void processSepayWebhook_success_withNoHyphen() {
        when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        SepayWebhookRequest req = SepayWebhookRequest.builder()
                .id(92705L)
                .gateway("Techcombank")
                .content("BMK20260912TEST1 chuyen tien")
                .transferType("in")
                .transferAmount(BigDecimal.valueOf(120000)) // chuyển dư tiền
                .referenceCode("FT26999")
                .build();

        PaymentResponse res = paymentService.processSepayWebhook(SEPAY_AUTH, req);

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("processSepayWebhook: Chuyển thiếu tiền -> ném BusinessException")
    void processSepayWebhook_amountMismatch_throwsException() {
        when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));

        SepayWebhookRequest req = SepayWebhookRequest.builder()
                .id(92706L)
                .gateway("Techcombank")
                .content("BMK-20260912-TEST1")
                .transferType("in")
                .transferAmount(BigDecimal.valueOf(50000)) // Đơn cần 115k nhưng chỉ chuyển 50k
                .build();

        assertThatThrownBy(() -> paymentService.processSepayWebhook(SEPAY_AUTH, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không đủ cho đơn hàng");
    }

    @Test
    @DisplayName("processSepayWebhook: Giao dịch tiền ra (out) -> bỏ qua trả về null")
    void processSepayWebhook_transferTypeOut_ignored() {
        SepayWebhookRequest req = SepayWebhookRequest.builder()
                .id(92707L)
                .transferType("out")
                .transferAmount(BigDecimal.valueOf(100000))
                .build();

        PaymentResponse res = paymentService.processSepayWebhook(SEPAY_AUTH, req);
        assertThat(res).isNull();
    }

    @Test
    @DisplayName("processSepayWebhook: Nhận dạng mã đơn có khoảng trắng BMK 20260912 TEST1")
    void processSepayWebhook_success_withSpaces() {
        when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        SepayWebhookRequest req = SepayWebhookRequest.builder()
                .id(92708L)
                .gateway("Techcombank")
                .content("CK BMK 20260912 TEST1 thanh toan")
                .transferType("in")
                .transferAmount(BigDecimal.valueOf(115000))
                .referenceCode("FT998877")
                .build();

        PaymentResponse res = paymentService.processSepayWebhook(SEPAY_AUTH, req);

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(testOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("processPayment: Đơn hàng đã hủy (CANCELLED) -> ném BusinessException chặn thanh toán")
    void processPayment_cancelledOrder_throwsException() {
        testOrder.setStatus(OrderStatus.CANCELLED);
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));

        ProcessPaymentRequest req = ProcessPaymentRequest.builder()
                .method(PaymentMethod.BANK_TRANSFER)
                .build();

        assertThatThrownBy(() -> paymentService.processPayment(10L, "BMK-20260912-TEST1", req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không thể thanh toán cho đơn hàng đã ở trạng thái CANCELLED");
    }

    @Test
    @DisplayName("processPayment: Đơn hàng đã thanh toán PAID trước đó -> Trả về kết quả hiện tại (Idempotent)")
    void processPayment_alreadyPaid_returnsExistingPayment() {
        Payment paidPayment = new Payment();
        paidPayment.setId(1L);
        paidPayment.setOrder(testOrder);
        paidPayment.setMethod(PaymentMethod.BANK_TRANSFER);
        paidPayment.setStatus(PaymentStatus.PAID);
        paidPayment.setGatewayTxnId("TXN-ORIGINAL-999");
        paidPayment.setAmount(BigDecimal.valueOf(115000));
        testOrder.setPayment(paidPayment);
        testOrder.setStatus(OrderStatus.CONFIRMED);

        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(paidPayment));

        ProcessPaymentRequest req = ProcessPaymentRequest.builder()
                .method(PaymentMethod.BANK_TRANSFER)
                .transactionRef("TXN-NEW-REF")
                .build();

        PaymentResponse res = paymentService.processPayment(10L, "BMK-20260912-TEST1", req);

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(res.getGatewayTxnId()).isEqualTo("TXN-ORIGINAL-999"); // Không bị ghi đè mã giao dịch cũ
    }

    @Test
    @DisplayName("processSepayWebhook: Webhook SePay gửi lặp lại khi đã PAID -> Trả về kết quả (Idempotent)")
    void processSepayWebhook_alreadyPaid_returnsExistingPayment() {
        Payment paidPayment = new Payment();
        paidPayment.setId(1L);
        paidPayment.setOrder(testOrder);
        paidPayment.setMethod(PaymentMethod.BANK_TRANSFER);
        paidPayment.setStatus(PaymentStatus.PAID);
        paidPayment.setGatewayTxnId("FT26258012345678");
        paidPayment.setAmount(BigDecimal.valueOf(115000));
        testOrder.setPayment(paidPayment);
        testOrder.setStatus(OrderStatus.CONFIRMED);

        when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-TEST1")).thenReturn(Optional.of(testOrder));
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(paidPayment));

        SepayWebhookRequest req = SepayWebhookRequest.builder()
                .id(92709L)
                .gateway("Techcombank")
                .content("BMK-20260912-TEST1")
                .transferType("in")
                .transferAmount(BigDecimal.valueOf(115000))
                .referenceCode("FT26258012345678")
                .build();

        PaymentResponse res = paymentService.processSepayWebhook(SEPAY_AUTH, req);

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(res.getGatewayTxnId()).isEqualTo("FT26258012345678");
    }
}
