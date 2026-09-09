package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.order.AssignShipperRequest;
import com.banhmyking.banhmyking.dto.order.CancelOrderRequest;
import com.banhmyking.banhmyking.dto.order.ConfirmDeliveryRequest;
import com.banhmyking.banhmyking.dto.order.CreateOrderRequest;
import com.banhmyking.banhmyking.dto.order.OrderItemOptionResponse;
import com.banhmyking.banhmyking.dto.order.OrderItemResponse;
import com.banhmyking.banhmyking.dto.order.OrderResponse;
import com.banhmyking.banhmyking.dto.order.OrderStatusHistoryResponse;
import com.banhmyking.banhmyking.dto.order.PriceBreakdown;
import com.banhmyking.banhmyking.dto.order.UpdateOrderStatusRequest;
import com.banhmyking.banhmyking.repository.specification.OrderSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import com.banhmyking.banhmyking.entity.Address;
import com.banhmyking.banhmyking.entity.Cart;
import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.CartItemOption;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.OrderItemOption;
import com.banhmyking.banhmyking.entity.OrderStatusHistory;
import com.banhmyking.banhmyking.entity.Payment;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Promotion;
import com.banhmyking.banhmyking.entity.PromotionUsage;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.PaymentMethod;
import com.banhmyking.banhmyking.enums.PaymentStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.AddressRepository;
import com.banhmyking.banhmyking.repository.CartRepository;
import com.banhmyking.banhmyking.repository.OrderItemRepository;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.OrderStatusHistoryRepository;
import com.banhmyking.banhmyking.repository.PaymentRepository;
import com.banhmyking.banhmyking.repository.PromotionRepository;
import com.banhmyking.banhmyking.repository.PromotionUsageRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.CartService;
import com.banhmyking.banhmyking.service.OrderService;
import com.banhmyking.banhmyking.service.PriceCalculator;
import com.banhmyking.banhmyking.util.OrderCodeGenerator;
import com.banhmyking.banhmyking.validator.OrderStatusValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartService cartService;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final PaymentRepository paymentRepository;
    private final PriceCalculator priceCalculator;
    private final OrderCodeGenerator orderCodeGenerator;
    private final OrderStatusValidator orderStatusValidator;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createFromCart(Long userId, CreateOrderRequest request) {
        log.info("Creating order from cart for user {}", userId);

        // 1. Kiểm tra User
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        // 2. Validate giỏ hàng hợp lệ (AC 3)
        Cart cart = cartRepository.findByUserIdWithDetails(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "Giỏ hàng đang trống, không thể tạo đơn hàng"));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Giỏ hàng đang trống, không thể tạo đơn hàng");
        }

        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            if (product == null || product.isDeleted()) {
                throw new ResourceNotFoundException("Một món ăn trong giỏ hàng không còn tồn tại");
            }
            if (!product.isAvailable()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Món ăn '" + product.getName() + "' hiện không khả dụng (hết hàng hoặc tạm ngưng bán)");
            }
        }

        // 3. Resolve & Snapshot thông tin giao hàng (AC 4)
        String receiverName;
        String receiverPhone;
        String shippingAddress;

        if (request.getAddressId() != null) {
            Address address = addressRepository.findByIdAndUserId(request.getAddressId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ giao hàng với ID: " + request.getAddressId()));
            receiverName = address.getReceiverName();
            receiverPhone = address.getReceiverPhone();
            shippingAddress = address.getFullAddress();
        } else {
            if (request.getReceiverName() == null || request.getReceiverName().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tên người nhận không được để trống");
            }
            if (request.getReceiverPhone() == null || request.getReceiverPhone().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số điện thoại nhận hàng không được để trống");
            }
            if (request.getShippingAddress() == null || request.getShippingAddress().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Địa chỉ nhận hàng không được để trống");
            }
            receiverName = request.getReceiverName().trim();
            receiverPhone = request.getReceiverPhone().trim();
            shippingAddress = request.getShippingAddress().trim();
        }

        // 4. Resolve Promotion (nếu có)
        Promotion promotion = null;
        if (request.getPromotionCode() != null && !request.getPromotionCode().trim().isEmpty()) {
            String code = request.getPromotionCode().trim().toUpperCase();
            promotion = promotionRepository.findByCodeAndActiveTrue(code)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã khuyến mãi '" + code + "' không tồn tại hoặc đã hết hiệu lực"));

            if (promotionUsageRepository.findByPromotionIdAndUserId(promotion.getId(), userId).isPresent()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Bạn đã sử dụng mã khuyến mãi '" + code + "' trước đó");
            }
        }

        // 5. Tính tiền qua PriceCalculator (AC 5)
        PriceBreakdown priceBreakdown = priceCalculator.calculate(cart, promotion);

        // 6. Sinh mã đơn hàng qua OrderCodeGenerator có retry 2–3 lần (AC 1)
        String orderCode = orderCodeGenerator.generateUniqueCode(orderRepository::existsByOrderCode, 3);

        // 7. Tạo Order và Snapshot chính xác dữ liệu (AC 4)
        Order order = new Order();
        order.setOrderCode(orderCode);
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setShippingAddress(shippingAddress);
        order.setSubtotal(priceBreakdown.getSubtotal());
        order.setShippingFee(priceBreakdown.getShippingFee());
        order.setDiscountAmount(priceBreakdown.getDiscountAmount());
        order.setTotal(priceBreakdown.getTotal());
        order.setPromotionCode(promotion != null ? promotion.getCode() : null);
        order.setNote(request.getNote());

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());     // Snapshot tên
            orderItem.setUnitPrice(product.getPrice());       // Snapshot giá gốc
            orderItem.setQuantity(cartItem.getQuantity());

            BigDecimal optionsSum = BigDecimal.ZERO;
            if (cartItem.getSelectedOptions() != null) {
                for (CartItemOption cio : cartItem.getSelectedOptions()) {
                    if (cio.getProductOption() != null) {
                        OrderItemOption oio = new OrderItemOption();
                        oio.setOrderItem(orderItem);
                        oio.setOptionName(cio.getProductOption().getName());      // Snapshot tên topping
                        oio.setOptionPrice(cio.getProductOption().getExtraPrice()); // Snapshot giá topping
                        orderItem.getOptions().add(oio);

                        optionsSum = optionsSum.add(cio.getProductOption().getExtraPrice());
                    }
                }
            }

            BigDecimal unitPriceWithOptions = product.getPrice().add(optionsSum);
            BigDecimal lineTotal = unitPriceWithOptions.multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            orderItem.setLineTotal(lineTotal); // Snapshot line_total

            order.getItems().add(orderItem);
        }

        // Lưu Order (cascade lưu order_items và order_item_options)
        order = orderRepository.save(order);

        // Khởi tạo Payment
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.COD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(order.getTotal());
        paymentRepository.save(payment);
        order.setPayment(payment);

        // Ghi nhận lượt dùng khuyến mãi (nếu có)
        if (promotion != null) {
            int used = promotion.getUsedCount() != null ? promotion.getUsedCount() : 0;
            promotion.setUsedCount(used + 1);
            promotionRepository.save(promotion);

            PromotionUsage usage = new PromotionUsage();
            usage.setPromotion(promotion);
            usage.setUser(user);
            usage.setOrder(order);
            usage.setDiscountApplied(priceBreakdown.getDiscountAmount());
            promotionUsageRepository.save(usage);
        }

        // 8. Tự động xóa sạch giỏ hàng sau khi tạo đơn thành công (AC 6)
        log.info("Order {} created successfully. Clearing cart for user {}", order.getOrderCode(), userId);
        cartService.clearCart(userId);

        return toOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByCode(Long userId, String orderCode) {
        Order order = orderRepository.findByOrderCodeWithDetails(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        if (actor.getRole() == RoleName.CUSTOMER && !order.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode);
        }

        if (actor.getRole() == RoleName.SHIPPER
                && (order.getShipper() == null || !order.getShipper().getId().equals(userId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không được phân công giao đơn hàng này");
        }

        return toOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getUserOrders(Long userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return orders.stream().map(this::toOrderResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getUserOrders(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<Order> orderPage = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(orderPage.map(this::toOrderResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrdersForAdmin(Long userId, OrderStatus status, String fromDateStr, String toDateStr, int page, int size) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        if (actor.getRole() != RoleName.STAFF && actor.getRole() != RoleName.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ nhân viên hoặc quản trị viên mới có quyền xem toàn bộ đơn hàng");
        }

        LocalDateTime fromDate = parseDateTime(fromDateStr, false);
        LocalDateTime toDate = parseDateTime(toDateStr, true);

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Order> spec = OrderSpecifications.withFilters(status, fromDate, toDate);
        Page<Order> orderPage = orderRepository.findAll(spec, pageable);

        return PageResponse.from(orderPage.map(this::toOrderResponse));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse assignShipper(Long userId, String orderCode, AssignShipperRequest request) {
        log.info("Assigning shipper {} to order {} by user {}", request.getShipperId(), orderCode, userId);

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        if (actor.getRole() != RoleName.STAFF && actor.getRole() != RoleName.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ nhân viên hoặc quản trị viên mới có quyền gán shipper");
        }

        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.DELIVERED || currentStatus == OrderStatus.CANCELLED || currentStatus == OrderStatus.FAILED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Không thể gán shipper cho đơn hàng ở trạng thái kết thúc: " + currentStatus);
        }

        User shipper = userRepository.findById(request.getShipperId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper với ID: " + request.getShipperId()));

        if (shipper.getRole() != RoleName.SHIPPER) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Người dùng ID " + request.getShipperId() + " không có vai trò SHIPPER");
        }

        order.setShipper(shipper);
        Order updatedOrder = orderRepository.save(order);

        // Ghi lịch sử gán shipper
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(updatedOrder);
        history.setFromStatus(currentStatus);
        history.setToStatus(currentStatus);
        history.setChangedBy(actor);
        String historyNote = "Gán shipper: " + shipper.getFullName() + " (" + shipper.getPhone() + ")";
        if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
            historyNote += " - Ghi chú: " + request.getNote().trim();
        }
        history.setNote(historyNote);
        orderStatusHistoryRepository.save(history);

        log.info("Order {} assigned to shipper {} by {} ({})",
                orderCode, shipper.getFullName(), actor.getFullName(), actor.getRole());

        return toOrderResponse(updatedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrdersForShipper(Long userId, OrderStatus status, int page, int size) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        if (actor.getRole() != RoleName.SHIPPER && actor.getRole() != RoleName.STAFF && actor.getRole() != RoleName.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ nhân viên giao hàng mới có quyền truy cập danh sách đơn giao");
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<Order> orderPage;
        if (status != null) {
            orderPage = orderRepository.findByShipperIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
        } else {
            orderPage = orderRepository.findByShipperIdOrderByCreatedAtDesc(userId, pageable);
        }

        return PageResponse.from(orderPage.map(this::toOrderResponse));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse confirmDelivery(Long userId, String orderCode, ConfirmDeliveryRequest request) {
        log.info("Confirming delivery for order {} by user {}", orderCode, userId);

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        if (actor.getRole() != RoleName.SHIPPER && actor.getRole() != RoleName.STAFF && actor.getRole() != RoleName.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ shipper được phân công mới có quyền xác nhận giao hàng");
        }

        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));

        if (actor.getRole() == RoleName.SHIPPER) {
            if (order.getShipper() == null || !order.getShipper().getId().equals(actor.getId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không phải là shipper được phân công giao đơn hàng này");
            }
        }

        OrderStatus fromStatus = order.getStatus();
        OrderStatus toStatus = OrderStatus.DELIVERED;

        // Validate chuyển trạng thái sang DELIVERED (từ DELIVERING)
        orderStatusValidator.validateTransition(order, toStatus, actor);

        order.setStatus(toStatus);
        order.setDeliveredAt(LocalDateTime.now());

        // Cập nhật trạng thái thanh toán nếu là COD
        Payment payment = order.getPayment();
        if (payment != null && payment.getMethod() == PaymentMethod.COD && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
        }

        Order updatedOrder = orderRepository.save(order);

        // Ghi lịch sử
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(updatedOrder);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(actor);
        String note = "Shipper xác nhận giao hàng thành công";
        if (request != null && request.getNote() != null && !request.getNote().trim().isEmpty()) {
            note += ": " + request.getNote().trim();
        }
        history.setNote(note);
        orderStatusHistoryRepository.save(history);

        log.info("Order {} delivered successfully by user {} ({})", orderCode, userId, actor.getRole());

        return toOrderResponse(updatedOrder);
    }

    private LocalDateTime parseDateTime(String input, boolean isEndOfDay) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String str = input.trim();
        try {
            if (str.length() == 10) {
                LocalDate date = LocalDate.parse(str);
                return isEndOfDay ? date.atTime(LocalTime.MAX) : date.atStartOfDay();
            }
            if (str.contains(" ")) {
                str = str.replace(" ", "T");
            }
            return LocalDateTime.parse(str);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Định dạng ngày không hợp lệ (hỗ trợ yyyy-MM-dd hoặc ISO-8601): " + input);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse cancelOrder(Long userId, String orderCode, CancelOrderRequest request) {
        log.info("Cancelling order {} by user {}", orderCode, userId);

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));

        String reason = (request != null && request.getCancelReason() != null) ? request.getCancelReason().trim() : null;

        // AC 3: Validate quyền hủy và điều kiện trạng thái qua OrderStatusValidator
        orderStatusValidator.validateCancel(order, actor, reason);

        OrderStatus fromStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        Order updatedOrder = orderRepository.save(order);

        // AC 2: Tự động ghi 1 dòng vào order_status_history
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(updatedOrder);
        history.setFromStatus(fromStatus);
        history.setToStatus(OrderStatus.CANCELLED);
        history.setChangedBy(actor);
        history.setNote(reason);
        orderStatusHistoryRepository.save(history);

        log.info("Order {} cancelled successfully from {} by user {} (role: {}). Reason: {}",
                orderCode, fromStatus, userId, actor.getRole(), reason);

        return toOrderResponse(updatedOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse updateOrderStatus(Long userId, String orderCode, UpdateOrderStatusRequest request) {
        log.info("Updating status for order {} to {} by user {}", orderCode, request.getNewStatus(), userId);

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));

        OrderStatus fromStatus = order.getStatus();
        OrderStatus toStatus = request.getNewStatus();

        // AC 1: Validate state machine (chặn nhảy cóc, FAILED chỉ từ DELIVERING, phân quyền vận hành)
        orderStatusValidator.validateTransition(order, toStatus, actor);

        // Gán thông tin shipper nếu chuyển sang DELIVERING
        if (toStatus == OrderStatus.DELIVERING) {
            if (request.getShipperId() != null) {
                User shipper = userRepository.findById(request.getShipperId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper với ID: " + request.getShipperId()));
                if (shipper.getRole() != RoleName.SHIPPER) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "Người dùng ID " + request.getShipperId() + " không có vai trò SHIPPER");
                }
                order.setShipper(shipper);
            } else if (actor.getRole() == RoleName.SHIPPER && order.getShipper() == null) {
                order.setShipper(actor);
            }
        }

        // Đóng dấu thời gian giao hàng thành công
        if (toStatus == OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
        }

        order.setStatus(toStatus);
        Order updatedOrder = orderRepository.save(order);

        // AC 2: Tự động ghi 1 dòng vào order_status_history
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(updatedOrder);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(actor);
        history.setNote(request.getNote());
        orderStatusHistoryRepository.save(history);

        log.info("Order {} transitioned from {} to {} by user {} (role: {})",
                orderCode, fromStatus, toStatus, userId, actor.getRole());

        return toOrderResponse(updatedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getOrderStatusHistory(Long userId, String orderCode) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));

        // Customer chỉ được xem lịch sử đơn hàng của chính mình
        if (actor.getRole() == RoleName.CUSTOMER && (order.getUser() == null || !order.getUser().getId().equals(actor.getId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem lịch sử đơn hàng của người khác");
        }

        List<OrderStatusHistory> histories = orderStatusHistoryRepository.findByOrderOrderCodeOrderByCreatedAtAsc(orderCode);

        List<OrderStatusHistoryResponse> responses = new ArrayList<>();
        for (OrderStatusHistory h : histories) {
            responses.add(OrderStatusHistoryResponse.builder()
                    .id(h.getId())
                    .orderCode(orderCode)
                    .fromStatus(h.getFromStatus())
                    .toStatus(h.getToStatus())
                    .changedById(h.getChangedBy() != null ? h.getChangedBy().getId() : null)
                    .changedByName(h.getChangedBy() != null ? h.getChangedBy().getFullName() : null)
                    .changedByRole(h.getChangedBy() != null ? h.getChangedBy().getRole() : null)
                    .note(h.getNote())
                    .createdAt(h.getCreatedAt())
                    .build());
        }

        return responses;
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                List<OrderItemOptionResponse> optionResponses = new ArrayList<>();
                if (item.getOptions() != null) {
                    for (OrderItemOption opt : item.getOptions()) {
                        optionResponses.add(OrderItemOptionResponse.builder()
                                .id(opt.getId())
                                .optionName(opt.getOptionName())
                                .optionPrice(opt.getOptionPrice())
                                .build());
                    }
                }

                itemResponses.add(OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getLineTotal())
                        .options(optionResponses)
                        .build());
            }
        }

        Payment payment = order.getPayment();

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .shippingAddress(order.getShippingAddress())
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .total(order.getTotal())
                .promotionCode(order.getPromotionCode())
                .paymentMethod(payment != null ? payment.getMethod() : PaymentMethod.COD)
                .paymentStatus(payment != null ? payment.getStatus() : PaymentStatus.PENDING)
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .cancelReason(order.getCancelReason())
                .deliveredAt(order.getDeliveredAt())
                .shipperId(order.getShipper() != null ? order.getShipper().getId() : null)
                .shipperName(order.getShipper() != null ? order.getShipper().getFullName() : null)
                .shipperPhone(order.getShipper() != null ? order.getShipper().getPhone() : null)
                .items(itemResponses)
                .build();
    }
}
