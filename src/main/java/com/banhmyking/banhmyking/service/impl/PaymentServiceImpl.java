package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.payment.PaymentResponse;
import com.banhmyking.banhmyking.dto.payment.ProcessPaymentRequest;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.event.OrderChangedEvent;
import com.banhmyking.banhmyking.entity.OrderStatusHistory;
import com.banhmyking.banhmyking.entity.Payment;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.PaymentMethod;
import com.banhmyking.banhmyking.enums.PaymentStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.dto.payment.SepayWebhookRequest;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.OrderStatusHistoryRepository;
import com.banhmyking.banhmyking.repository.PaymentRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.InventoryService;
import com.banhmyking.banhmyking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final InventoryService inventoryService;
    private final com.banhmyking.banhmyking.security.StoreAccessGuard storeAccessGuard;
    /** Phát OrderChangedEvent khi tiền về đổi trạng thái đơn (realtime) */
    private final ApplicationEventPublisher eventPublisher;

    private static final Pattern PATTERN_HYPHEN = Pattern.compile("BMK-\\d{8}-[A-Za-z0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_FLEXIBLE = Pattern
            .compile("BMK[\\s\\-_]*(\\d{8})[\\s\\-_]*([A-Za-z0-9]{4,10})", Pattern.CASE_INSENSITIVE);

    @Value("${sepay.api-key:}")
    private String sepayApiKey;

    @Value("${sepay.account-number:}")
    private String sepayAccountNumber;

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderCode(Long userId, String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        assertCanViewOrderPayment(order, actor, userId, "Không tìm thấy đơn hàng với mã: " + orderCode);

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thông tin thanh toán cho đơn hàng: " + orderCode));

        return toPaymentResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thông tin thanh toán với ID: " + paymentId));

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        assertCanViewOrderPayment(payment.getOrder(), actor, userId,
                "Không tìm thấy thông tin thanh toán với ID: " + paymentId);

        return toPaymentResponse(payment);
    }

    /**
     * Chặn đọc payment của đơn không liên quan:
     * CUSTOMER chỉ xem đơn của mình (mask NOT_FOUND), SHIPPER chỉ xem đơn được phân
     * công (403).
     * STAFF/MANAGER chỉ xem đơn thuộc cơ sở của mình (404 nếu khác cơ sở); ADMIN xem toàn chuỗi.
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
        if (order != null) {
            storeAccessGuard.requireOrderAccess(actor, order);
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

        // Chỉ COD mới "thu tiền khi giao". Đơn chuyển khoản/ví chưa có tiền về thì không được
        // tự ghi PAID chỉ vì đơn đã giao — tiền chỉ được ghi nhận qua webhook hoặc đối soát tay.
        boolean isCod = payment.getMethod() == null || payment.getMethod() == PaymentMethod.COD;
        if (isCod && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
            Payment saved = paymentRepository.save(payment);
            log.info("Payment ID {} for orderId {} updated to PAID at {}",
                    saved.getId(), orderId, saved.getPaidAt());
            return saved;
        }

        return payment;
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(Long userId, String orderCode, ProcessPaymentRequest request) {
        log.info("User {} processing payment for order {}", userId, orderCode);

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        Order order = orderRepository.findByOrderCodeWithDetails(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));

        assertCanViewOrderPayment(order, actor, userId, "Không tìm thấy đơn hàng với mã: " + orderCode);

        // 1. Chặn thanh toán cho đơn hàng đã hủy hoặc kết thúc thất bại
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FAILED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Không thể thanh toán cho đơn hàng đã ở trạng thái " + order.getStatus());
        }

        // 2. Mô phỏng lỗi giao dịch nếu cờ simulateFailure = true (AC 3)
        if (request != null && Boolean.TRUE.equals(request.getSimulateFailure())) {
            log.warn("Simulated payment rejection for order {}", orderCode);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Giao dịch thanh toán bị từ chối: Thẻ không đủ số dư hoặc tài khoản thanh toán bị khóa");
        }

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseGet(() -> {
                    Payment p = new Payment();
                    p.setOrder(order);
                    p.setAmount(order.getTotal());
                    return p;
                });

        // 3. Tính lũy/Idempotent: Nếu đơn đã thanh toán PAID trước đó, trả về thông tin
        // hiện tại
        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Đơn hàng {} đã hoàn tất thanh toán trước đó", orderCode);
            return toPaymentResponse(payment);
        }
        // Đã hoàn tiền thì không được đưa ngược về PENDING/PAID (sổ tiền sẽ sai).
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Đơn hàng " + orderCode + " đã được hoàn tiền, không thể thanh toán lại");
        }

        PaymentMethod method = request != null && request.getMethod() != null
                ? request.getMethod()
                : (payment.getMethod() != null ? payment.getMethod() : PaymentMethod.COD);

        payment.setMethod(method);

        if (method == PaymentMethod.BANK_TRANSFER || method == PaymentMethod.E_WALLET) {
            // Chuyển khoản/ví điện tử: khách và shipper KHÔNG được tự xác nhận đã trả tiền.
            // Tiền vào chỉ được ghi nhận bởi webhook SePay đã xác thực (processSepayWebhook)
            // hoặc do STAFF/ADMIN đối soát tay (tiền về tài khoản khác, SePay chưa cấu hình...).
            boolean canReconcileManually = storeAccessGuard.isOperator(actor);
            if (!canReconcileManually) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "Đơn chuyển khoản được xác nhận tự động khi hệ thống nhận đủ tiền. "
                                + "Vui lòng chuyển khoản đúng số tiền và nội dung, đơn sẽ tự cập nhật.");
            }
            storeAccessGuard.requireOrderAccess(actor, order);

            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
            String txnId = (request != null && request.getTransactionRef() != null
                    && !request.getTransactionRef().trim().isEmpty())
                            ? request.getTransactionRef().trim()
                            // Đối soát tay: ghi rõ tiền tố MANUAL để không nhầm với mã giao dịch ngân hàng
                            : "MANUAL-" + System.currentTimeMillis();
            payment.setGatewayTxnId(txnId);

            // Cập nhật trạng thái đơn hàng sang CONFIRMED nếu đang PENDING
            if (order.getStatus() == OrderStatus.PENDING) {
                confirmPaidOrder(order, actor.getFullName() + " đối soát chuyển khoản, giao dịch " + txnId);
            }
        } else {
            // COD: Giữ trạng thái PENDING chờ shipper giao
            payment.setStatus(PaymentStatus.PENDING);
            payment.setPaidAt(null);
            orderRepository.save(order);
        }

        Payment savedPayment = paymentRepository.save(payment);
        order.setPayment(savedPayment);
        return toPaymentResponse(savedPayment);
    }

    @Override
    @Transactional
    public PaymentResponse processSepayWebhook(String authHeader, SepayWebhookRequest request) {
        log.info("Received SePay webhook: gateway={}, ref={}, amount={}, content={}",
                request != null ? request.getGateway() : "null",
                request != null ? request.getReferenceCode() : "null",
                request != null ? request.getTransferAmount() : "null",
                request != null ? request.getContent() : "null");

        // 1. Xác thực API Key SePay — FAIL-CLOSED: chưa cấu hình key thì TỪ CHỐI,
        // tuyệt đối không bỏ qua kiểm tra (nếu bỏ qua, webhook giả mạo sẽ đánh dấu đơn là PAID).
        // Hỗ trợ case-insensitive tiền tố Apikey/Bearer.
        if (sepayApiKey == null || sepayApiKey.trim().isEmpty()) {
            log.error("SePay webhook bị từ chối: chưa cấu hình sepay.api-key (biến môi trường SEPAY_API_KEY)");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Webhook SePay chưa được cấu hình API Key");
        }
        String expectedKey = sepayApiKey.trim();
        String cleanAuth = authHeader != null ? authHeader.trim() : "";
        // Bóc tiền tố Apikey/Bearer (không phải bí mật) rồi so phần key bằng so sánh
        // constant-time — equals() thường thoát sớm ở ký tự khác đầu tiên nên lộ dần key.
        String providedKey = cleanAuth;
        int space = cleanAuth.indexOf(' ');
        if (space > 0) {
            String scheme = cleanAuth.substring(0, space);
            if ("Apikey".equalsIgnoreCase(scheme) || "Bearer".equalsIgnoreCase(scheme)) {
                providedKey = cleanAuth.substring(space + 1).trim();
            }
        }
        if (!constantTimeEquals(providedKey, expectedKey)) {
            log.warn("SePay webhook rejected: Invalid Authorization header");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "API Key SePay không hợp lệ");
        }

        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Payload webhook SePay không được để trống");
        }

        // 2. Chỉ xử lý giao dịch tiền vào ("in")
        if (request.getTransferType() != null && !"in".equalsIgnoreCase(request.getTransferType())) {
            log.info("Bỏ qua giao dịch không phải tiền vào: type={}", request.getTransferType());
            return null;
        }

        // 3. Trích xuất mã đơn hàng từ nội dung chuyển khoản
        Order order = findOrderFromContent(request.getContent())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy đơn hàng tương ứng với nội dung chuyển khoản: " + request.getContent()));

        // 4. Chỉ đơn chuyển khoản mới được webhook xác nhận. Tiền vào tài khoản cho một
        // đơn COD/ví không có nghĩa là đơn đó đã được trả — bỏ qua, không đánh dấu PAID.
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseGet(() -> {
                    Payment p = new Payment();
                    p.setOrder(order);
                    p.setAmount(order.getTotal());
                    return p;
                });
        if (payment.getMethod() != null && payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            log.warn("SePay webhook bỏ qua: đơn {} dùng phương thức {}, không phải chuyển khoản",
                    order.getOrderCode(), payment.getMethod());
            return toPaymentResponse(payment);
        }

        // 5. Phải khớp tiền tuyệt đối: thiếu thì chưa đủ điều kiện xác nhận, thừa thì phải
        // đối soát tay — không tự nhận để tránh ghi sai số tiền vào sổ.
        if (request.getTransferAmount() == null || request.getTransferAmount().compareTo(order.getTotal()) != 0) {
            log.warn("SePay webhook lệch tiền: đơn {} cần {}, nhận {}",
                    order.getOrderCode(), order.getTotal(), request.getTransferAmount());
            return toPaymentResponse(payment);
        }

        // 6. Dedup theo mã giao dịch — webhook gửi lại lần hai không được đổi trạng thái thêm.
        String txnRef = (request.getReferenceCode() != null && !request.getReferenceCode().trim().isEmpty())
                ? request.getReferenceCode().trim()
                : "SEPAY-" + (request.getId() != null ? request.getId() : System.currentTimeMillis());
        if (paymentRepository.existsByGatewayTxnId(txnRef)) {
            log.info("Giao dịch SePay {} đã ghi nhận trước đó, bỏ qua", txnRef);
            return toPaymentResponse(payment);
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Đơn hàng {} đã thanh toán trước đó", order.getOrderCode());
            return toPaymentResponse(payment);
        }

        // 7. Đơn đã huỷ/giao thất bại mà tiền vẫn về → ghi nhận đã thu rồi hoàn ngay,
        // không giữ tiền của khách.
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FAILED) {
            log.warn("Nhận tiền cho đơn {} đang ở trạng thái {} — tự động hoàn tiền",
                    order.getOrderCode(), order.getStatus());
            payment.setMethod(PaymentMethod.BANK_TRANSFER);
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
            payment.setGatewayTxnId(txnRef);
            Payment refunded = refundPayment(order, request.getTransferAmount(),
                    "Đơn đã " + order.getStatus() + " nhưng vẫn nhận được tiền chuyển khoản", null);
            return toPaymentResponse(refunded != null ? refunded : payment);
        }

        // 8. Ghi nhận PAID và chuyển đơn sang CONFIRMED
        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setGatewayTxnId(txnRef);

        Payment savedPayment = paymentRepository.save(payment);
        order.setPayment(savedPayment);

        if (order.getStatus() == OrderStatus.PENDING) {
            confirmPaidOrder(order, "Webhook SePay xác nhận đủ tiền, giao dịch " + txnRef);
        }

        log.info("SePay payment confirmed successfully for order {} with txnRef {}",
                order.getOrderCode(), txnRef);
        return toPaymentResponse(savedPayment);
    }

    /**
     * Đã thu đủ tiền cho đơn PENDING → giữ hàng (trừ tồn) rồi chuyển CONFIRMED, giống luồng
     * STAFF xác nhận ở OrderServiceImpl.updateOrderStatus. Thiếu hàng thì KHÔNG ném lỗi (tiền đã về,
     * không được rollback) mà giữ đơn PENDING kèm ghi chú để nhân viên xử lý (huỷ + hoàn tiền).
     */
    private void confirmPaidOrder(Order order, String note) {
        if (inventoryService.tryDecreaseForOrder(order)) {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            recordPaymentHistory(order, OrderStatus.PENDING, OrderStatus.CONFIRMED, note);
        } else {
            log.warn("Đơn {} đã thu tiền nhưng không đủ tồn kho để xác nhận", order.getOrderCode());
            recordPaymentHistory(order, OrderStatus.PENDING, OrderStatus.PENDING,
                    note + ". Không đủ tồn kho để xác nhận — cần nhân viên xử lý (huỷ và hoàn tiền)");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Payment refundPayment(Order order, BigDecimal amount, String reason, Long actorId) {
        if (order == null || order.getId() == null) {
            return null;
        }
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        if (payment == null) {
            log.warn("Không có bản ghi thanh toán để hoàn cho đơn {}", order.getOrderCode());
            return null;
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Đơn {} đã được hoàn tiền trước đó", order.getOrderCode());
            return payment;
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Chỉ hoàn tiền cho đơn đã thu tiền (trạng thái hiện tại: " + payment.getStatus() + ")");
        }

        BigDecimal refundAmount = amount != null ? amount : payment.getAmount();
        if (payment.getAmount() != null && refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Số tiền hoàn (" + refundAmount + ") vượt quá số tiền đã thu (" + payment.getAmount() + ")");
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundAmount(refundAmount);
        payment.setRefundReason(reason);
        payment.setRefundedAt(LocalDateTime.now());
        if (actorId != null) {
            payment.setRefundedBy(userRepository.findById(actorId).orElse(null));
        }
        Payment saved = paymentRepository.save(payment);
        log.info("Refunded order {} amount {} by actor {}", order.getOrderCode(), refundAmount, actorId);
        return saved;
    }

    /** Ghi order_status_history cho các chuyển trạng thái do tiền về (không có người bấm). */
    private void recordPaymentHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(null);
        history.setNote(note);
        orderStatusHistoryRepository.save(history);
        eventPublisher.publishEvent(OrderChangedEvent.of(order, fromStatus, toStatus, null, null, null));
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private Optional<Order> findOrderFromContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return Optional.empty();
        }

        // 1. Regex tìm mã đơn có gạch ngang chuẩn: BMK-yyyyMMdd-XXXXX
        Matcher matcherHyphen = PATTERN_HYPHEN.matcher(content);
        if (matcherHyphen.find()) {
            String code = matcherHyphen.group().toUpperCase();
            Optional<Order> orderOpt = orderRepository.findByOrderCodeWithDetails(code);
            if (orderOpt.isPresent())
                return orderOpt;
        }

        // 2. Regex tìm mã đơn linh hoạt (có dấu cách, gạch dưới, hoặc viết liền không
        // dấu: BMK 20260914 XXXXX / BMK20260914XXXXX)
        Matcher matcherFlexible = PATTERN_FLEXIBLE.matcher(content);
        if (matcherFlexible.find()) {
            String datePart = matcherFlexible.group(1);
            String suffix = matcherFlexible.group(2).toUpperCase();
            String formatted = "BMK-" + datePart + "-" + suffix;
            Optional<Order> orderOpt = orderRepository.findByOrderCodeWithDetails(formatted);
            if (orderOpt.isPresent())
                return orderOpt;

            String raw = "BMK" + datePart + suffix;
            orderOpt = orderRepository.findByOrderCodeWithDetails(raw);
            if (orderOpt.isPresent())
                return orderOpt;
        }

        return Optional.empty();
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
                .refundAmount(payment.getRefundAmount())
                .refundReason(payment.getRefundReason())
                .refundedAt(payment.getRefundedAt())
                .build();
    }
}