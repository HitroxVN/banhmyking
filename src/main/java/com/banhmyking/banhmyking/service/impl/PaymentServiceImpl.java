package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.payment.PaymentResponse;
import com.banhmyking.banhmyking.dto.payment.ProcessPaymentRequest;
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
import com.banhmyking.banhmyking.dto.payment.SepayWebhookRequest;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.PaymentRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

        PaymentMethod method = request != null && request.getMethod() != null
                ? request.getMethod()
                : (payment.getMethod() != null ? payment.getMethod() : PaymentMethod.COD);

        payment.setMethod(method);

        if (method == PaymentMethod.BANK_TRANSFER || method == PaymentMethod.E_WALLET) {
            // Chuyển khoản/ví điện tử: khách và shipper KHÔNG được tự xác nhận đã trả tiền.
            // Tiền vào chỉ được ghi nhận bởi webhook SePay đã xác thực (processSepayWebhook)
            // hoặc do STAFF/ADMIN đối soát tay (tiền về tài khoản khác, SePay chưa cấu hình...).
            boolean canReconcileManually = actor.getRole() == RoleName.STAFF
                    || actor.getRole() == RoleName.ADMIN;
            if (!canReconcileManually) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "Đơn chuyển khoản được xác nhận tự động khi hệ thống nhận đủ tiền. "
                                + "Vui lòng chuyển khoản đúng số tiền và nội dung, đơn sẽ tự cập nhật.");
            }

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
                order.setStatus(OrderStatus.CONFIRMED);
                orderRepository.save(order);
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
        boolean validHeader = cleanAuth.equalsIgnoreCase("Apikey " + expectedKey)
                || cleanAuth.equalsIgnoreCase("Bearer " + expectedKey)
                || cleanAuth.equals(expectedKey);
        if (!validHeader) {
            log.warn("SePay webhook rejected: Invalid Authorization header: {}", authHeader);
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

        // 4. Kiểm tra số tiền chuyển khoản
        if (request.getTransferAmount() == null || request.getTransferAmount().compareTo(order.getTotal()) < 0) {
            log.warn("SePay webhook amount mismatch: order {} expects {}, but received {}",
                    order.getOrderCode(), order.getTotal(), request.getTransferAmount());
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    String.format("Số tiền chuyển khoản (%s) không đủ cho đơn hàng %s (%s)",
                            request.getTransferAmount(), order.getOrderCode(), order.getTotal()));
        }

        // 5. Cập nhật Payment sang PAID (hoặc trả về ngay nếu đã PAID - Idempotent)
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseGet(() -> {
                    Payment p = new Payment();
                    p.setOrder(order);
                    p.setAmount(order.getTotal());
                    return p;
                });

        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Giao dịch SePay trùng lặp cho đơn hàng {} đã thanh toán trước đó", order.getOrderCode());
            return toPaymentResponse(payment);
        }

        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        String txnRef = (request.getReferenceCode() != null && !request.getReferenceCode().trim().isEmpty())
                ? request.getReferenceCode().trim()
                : "SEPAY-" + (request.getId() != null ? request.getId() : System.currentTimeMillis());
        payment.setGatewayTxnId(txnRef);

        Payment savedPayment = paymentRepository.save(payment);
        order.setPayment(savedPayment);

        // 6. Cập nhật Order sang CONFIRMED
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
        } else if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FAILED) {
            log.warn("Nhận được tiền thanh toán cho đơn hàng đã ở trạng thái hủy/thất bại: orderCode={}, status={}",
                    order.getOrderCode(), order.getStatus());
        }

        log.info("SePay payment confirmed successfully for order {} with txnRef {}",
                order.getOrderCode(), txnRef);
        return toPaymentResponse(savedPayment);
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
                .build();
    }
}