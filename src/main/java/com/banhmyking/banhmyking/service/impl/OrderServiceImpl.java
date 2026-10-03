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
import com.banhmyking.banhmyking.event.OrderChangeKind;
import com.banhmyking.banhmyking.event.OrderChangedEvent;
import com.banhmyking.banhmyking.dto.order.PriceBreakdown;
import com.banhmyking.banhmyking.dto.order.RejectOrderRequest;
import com.banhmyking.banhmyking.dto.order.UpdateOrderStatusRequest;
import com.banhmyking.banhmyking.repository.specification.OrderSpecifications;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
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
import com.banhmyking.banhmyking.exception.NotFoundMessages;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.AddressRepository;
import com.banhmyking.banhmyking.repository.CartRepository;
import com.banhmyking.banhmyking.repository.OrderItemRepository;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.OrderStatusHistoryRepository;
import com.banhmyking.banhmyking.repository.PaymentRepository;
import com.banhmyking.banhmyking.repository.PromotionRepository;
import com.banhmyking.banhmyking.dto.delivery.DeliveryFeeResult;
import com.banhmyking.banhmyking.repository.PromotionUsageRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.dto.order.OrderItemComponentResponse;
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.OrderItemComponent;
import com.banhmyking.banhmyking.service.ComboExpander;
import com.banhmyking.banhmyking.service.CartService;
import com.banhmyking.banhmyking.service.DeliveryFeeCalculator;
import com.banhmyking.banhmyking.service.OrderService;
import com.banhmyking.banhmyking.service.PaymentService;
import com.banhmyking.banhmyking.service.PriceCalculator;
import com.banhmyking.banhmyking.util.OrderCodeGenerator;
import com.banhmyking.banhmyking.util.PageableFactory;
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
import java.util.Optional;

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
    private final com.banhmyking.banhmyking.service.PromotionService promotionService;
    private final PaymentRepository paymentRepository;
    private final PriceCalculator priceCalculator;
    private final com.banhmyking.banhmyking.service.ProductPricing productPricing;
    private final DeliveryFeeCalculator deliveryFeeCalculator;
    private final com.banhmyking.banhmyking.service.StoreSelectionService storeSelectionService;
    private final PaymentService paymentService;
    private final OrderCodeGenerator orderCodeGenerator;
    private final OrderStatusValidator orderStatusValidator;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final com.banhmyking.banhmyking.service.InventoryService inventoryService;
    private final com.banhmyking.banhmyking.security.StoreAccessGuard storeAccessGuard;
    /** Phát OrderChangedEvent cho realtime — gửi đi sau khi commit (RealtimeEventListener) */
    private final ApplicationEventPublisher eventPublisher;

    /** Số phút tối đa một đơn PENDING chưa thanh toán được phép treo trước khi bị tự huỷ. */
    @org.springframework.beans.factory.annotation.Value("${order.pending-timeout-minutes:30}")
    private long pendingTimeoutMinutes;

    // ─── Helpers dùng chung ───────────────────────────────────────

    /** Resolve user bắt buộc tồn tại — message tập trung, hết copy-paste 12 lần. */
    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(NotFoundMessages.userById(id)));
    }

    private Order requireOrderByCode(String orderCode) {
        return orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException(NotFoundMessages.orderByCode(orderCode)));
    }

    private User requireShipper(Long shipperId) {
        User shipper = userRepository.findById(shipperId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy shipper với ID: " + shipperId));
        if (shipper.getRole() != RoleName.SHIPPER) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Người dùng ID " + shipperId + " không có vai trò SHIPPER");
        }
        return shipper;
    }

    /**
     * Mark payment PAID khi đơn chuyển DELIVERED — block copy-paste 2 lần
     * (confirmDelivery + updateOrderStatus), gom về đây.
     */
    private void applyDeliveredPayment(Order order, LocalDateTime now) {
        Payment paidPayment = paymentService.markPaymentAsPaid(order.getId());
        if (paidPayment != null) {
            order.setPayment(paidPayment);
        }
        // Không hồi sinh payment đã hoàn tiền (admin hoàn trước khi shipper kịp xác nhận giao).
        // Chỉ COD mới thu tiền lúc giao — đơn chuyển khoản chưa có tiền về thì giữ nguyên trạng thái.
        Payment payment = order.getPayment();
        boolean isCod = payment != null
                && (payment.getMethod() == null || payment.getMethod() == PaymentMethod.COD);
        if (isCod && payment.getStatus() != PaymentStatus.REFUNDED) {
            order.getPayment().setStatus(PaymentStatus.PAID);
            order.getPayment().setPaidAt(now);
        }
    }

    /** Ghi 1 dòng order_status_history — block lặp 4 lần, gom về đây. kind suy ra từ trạng thái. */
    private void recordHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus,
                               User changedBy, String note) {
        recordHistory(order, fromStatus, toStatus, changedBy, note, null, null, null);
    }

    /**
     * Ghi 1 dòng lịch sử và phát OrderChangedEvent (spec realtime §2.2). Mọi đường đổi đơn đi qua đây
     * nên realtime không phải chèn code vào từng nghiệp vụ.
     */
    private void recordHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus, User changedBy,
                               String note, OrderChangeKind kind, Long previousStoreId, Long previousShipperId) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(changedBy);
        history.setNote(note);
        orderStatusHistoryRepository.save(history);
        eventPublisher.publishEvent(OrderChangedEvent.of(order, fromStatus, toStatus, kind,
                previousStoreId, previousShipperId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createFromCart(Long userId, CreateOrderRequest request) {
        log.info("Creating order from cart for user {}", userId);

        // 1. Kiểm tra User
        User user = requireUser(userId);

        // Chống double-submit: cùng idempotencyKey thì trả về đúng đơn đã tạo, không tạo đơn thứ hai.
        String idempotencyKey = (request.getIdempotencyKey() != null && !request.getIdempotencyKey().trim().isEmpty())
                ? request.getIdempotencyKey().trim()
                : null;
        if (idempotencyKey != null) {
            Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            // Key là UNIQUE toàn bảng: trùng key của user khác thì KHÔNG được trả đơn đó về
            // (lộ tên, SĐT, địa chỉ người nhận) — báo lỗi để client sinh key mới.
            if (existing.isPresent() && (existing.get().getUser() == null
                    || !userId.equals(existing.get().getUser().getId()))) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "Mã chống gửi trùng (idempotencyKey) đã được sử dụng, vui lòng thử lại");
            }
            if (existing.isPresent()) {
                log.info("Idempotent create: trả về đơn {} cho key {}", existing.get().getOrderCode(), idempotencyKey);
                return toOrderResponse(existing.get());
            }
        }

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
            // Combo còn cần mọi thành phần đang bán toàn chuỗi (spec combo-sale §4.3)
            if (!ComboExpander.isChainAvailable(product)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Món ăn '" + product.getName() + "' hiện không khả dụng (hết hàng hoặc tạm ngưng bán)");
            }
        }

        // 3. Resolve & Snapshot thông tin giao hàng (AC 4)
        String receiverName;
        String receiverPhone;
        String shippingAddress;
        BigDecimal deliveryLatitude;
        BigDecimal deliveryLongitude;

        if (request.getAddressId() != null) {
            Address address = addressRepository.findByIdAndUserId(request.getAddressId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ giao hàng với ID: " + request.getAddressId()));
            receiverName = address.getReceiverName();
            receiverPhone = address.getReceiverPhone();
            shippingAddress = address.getFullAddress();
            deliveryLatitude = address.getLatitude();
            deliveryLongitude = address.getLongitude();
            // R13: địa chỉ đã lưu chưa ghim (dữ liệu cũ) không xác định được cơ sở phục vụ / bán kính
            // → báo rõ để khách ghim lại trong Hồ sơ, thay vì lỗi chung "chọn cơ sở".
            if (deliveryLatitude == null || deliveryLongitude == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Địa chỉ này chưa được ghim trên bản đồ — vui lòng cập nhật địa chỉ trong Hồ sơ");
            }
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
            // R10: địa chỉ MỚI bắt buộc ghim toạ độ — nếu không, StoreSelectionService bỏ qua kiểm tra bán
            // kính và khách có thể chọn cơ sở xa tuỳ ý. Địa chỉ đã lưu (addressId) chưa có toạ độ vẫn được
            // chấp nhận như cũ (spec §3.4, dữ liệu cũ).
            if (request.getLatitude() == null || request.getLongitude() == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Vui lòng ghim vị trí giao hàng trên bản đồ");
            }
            deliveryLatitude = request.getLatitude();
            deliveryLongitude = request.getLongitude();
        }

        // Thời điểm chốt giá = lúc tạo đơn (spec combo-sale §3): một mốc cho tạm tính, mã giảm giá và snapshot.
        LocalDateTime pricedAt = productPricing.now();
        BigDecimal cartSubtotal = BigDecimal.ZERO;
        if (cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                cartSubtotal = cartSubtotal.add(productPricing.lineTotal(item, pricedAt));
            }
        }
        cartSubtotal = cartSubtotal.setScale(2, RoundingMode.HALF_UP);

        // Chọn / kiểm tra cơ sở phục vụ — server chấm lại toàn bộ điều kiện (spec §3.2)
        java.util.Map<Product, Integer> demand = new java.util.LinkedHashMap<>();
        for (CartItem item : cart.getItems()) {
            demand.merge(item.getProduct(), item.getQuantity(), Integer::sum);
        }
        com.banhmyking.banhmyking.service.StoreSelectionService.Candidate chosen = storeSelectionService.requireEligible(
                request.getStoreId(), deliveryLatitude, deliveryLongitude, cartSubtotal, demand);
        BigDecimal distanceKm = chosen.distanceKm();

        // 4. Resolve Promotion (nếu có)
        Promotion promotion = null;
        if (request.getPromotionCode() != null && !request.getPromotionCode().trim().isEmpty()) {
            BigDecimal promoSubtotal = priceCalculator.calculateSubtotal(cart, pricedAt);
            promotion = promotionService.validateForOrder(request.getPromotionCode(), userId, promoSubtotal);
        }

        // 5. Tính tiền qua DeliveryFeeCalculator & PriceCalculator (AC 1, AC 5)

        // #18: deliveryFeeCalculator/paymentService là bean bắt buộc (@RequiredArgsConstructor)
        // — null-check và fallback tự tạo Payment là dead code, đã xóa.
        DeliveryFeeResult deliveryResult = deliveryFeeCalculator.calculateFee(
                distanceKm, shippingAddress, cartSubtotal, chosen.store().getFreeShipRadiusKm());
        BigDecimal shippingFee = (deliveryResult != null && deliveryResult.getShippingFee() != null)
                ? deliveryResult.getShippingFee()
                : PriceCalculator.DEFAULT_SHIPPING_FEE;
        PriceBreakdown priceBreakdown = priceCalculator.calculate(cart, promotion, shippingFee, pricedAt);

        // 6. Sinh mã đơn hàng qua OrderCodeGenerator có retry 2–3 lần (AC 1)
        String orderCode = orderCodeGenerator.generateUniqueCode(orderRepository::existsByOrderCode, 3);

        // 7. Tạo Order và Snapshot chính xác dữ liệu (AC 4)
        Order order = new Order();
        order.setOrderCode(orderCode);
        order.setUser(user);
        order.setStore(chosen.store());
        order.setIdempotencyKey(idempotencyKey);
        order.setStatus(OrderStatus.PENDING);
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setShippingAddress(shippingAddress);
        order.setDistanceKm(distanceKm);
        order.setDeliveryLatitude(deliveryLatitude);
        order.setDeliveryLongitude(deliveryLongitude);
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
            BigDecimal unitPrice = productPricing.effectivePrice(product, pricedAt);
            orderItem.setUnitPrice(unitPrice); // Snapshot giá hiệu lực (giá KM / giá combo)
            // Giá gốc lúc đặt: món lẻ = price, combo = Σ giá lẻ thành phần; không thấp hơn giá bán → tiết kiệm ≥ 0
            orderItem.setOriginalUnitPrice(productPricing.originalPrice(product).max(unitPrice));
            orderItem.setQuantity(cartItem.getQuantity());
            if (product.isCombo()) {
                for (ComboItem comboItem : product.getComboItems()) {
                    OrderItemComponent component = new OrderItemComponent();
                    component.setOrderItem(orderItem);
                    component.setProduct(comboItem.getComponent());
                    component.setProductName(comboItem.getComponent().getName()); // Snapshot tên thành phần
                    component.setQuantity(comboItem.getQuantity());
                    orderItem.getComponents().add(component);
                }
            }

            if (cartItem.getSelectedOptions() != null) {
                for (CartItemOption cio : cartItem.getSelectedOptions()) {
                    if (cio.getProductOption() != null) {
                        OrderItemOption oio = new OrderItemOption();
                        oio.setOrderItem(orderItem);
                        oio.setOptionName(cio.getProductOption().getName());      // Snapshot tên topping
                        oio.setOptionPrice(cio.getProductOption().getExtraPrice()); // Snapshot giá topping
                        orderItem.getOptions().add(oio);
                    }
                }
            }

            // lineTotal qua helper chung (null-safe extraPrice — trước đây cộng trực tiếp → NPE tiềm ẩn)
            BigDecimal lineTotal = productPricing.lineTotal(cartItem, pricedAt)
                    .setScale(2, RoundingMode.HALF_UP);
            orderItem.setLineTotal(lineTotal); // Snapshot line_total

            order.getItems().add(orderItem);
        }

        // Lưu Order (cascade lưu order_items và order_item_options)
        order = orderRepository.save(order);

        // Mọi đơn đều có mốc khởi tạo trong lịch sử — không còn đơn "trần" thiếu dòng đầu.
        recordHistory(order, OrderStatus.PENDING, OrderStatus.PENDING, user, "Đơn hàng được tạo",
                OrderChangeKind.CREATED, null, null);

        // Khởi tạo Payment với trạng thái PENDING khi chốt đơn (AC 2)
        // paymentService là bean bắt buộc — bỏ null-check + nhánh tự tạo Payment (dead code).
        PaymentMethod method = request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.COD;
        Payment payment = paymentService.createPendingPayment(order, method, order.getTotal());
        order.setPayment(payment);

        // Ghi nhận lượt dùng khuyến mãi (nếu có) — SAU khi order đã save để FK order_id hợp lệ.
        // Increment atomic trong UPDATE (điều kiện maxUsage) → không race hai đơn cùng vượt quota.
        if (promotion != null) {
            if (promotionRepository.incrementUsedCountAtomic(promotion.getId()) == 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Mã khuyến mãi '" + promotion.getCode() + "' vừa hết lượt sử dụng, vui lòng thử lại");
            }
            PromotionUsage usage = new PromotionUsage();
            usage.setPromotion(promotion);
            usage.setUser(user);
            usage.setOrder(order);
            usage.setDiscountApplied(priceBreakdown.getDiscountAmount());
            try {
                // saveAndFlush: vi phạm uk_promotion_user phải nổ ngay tại đây (không đợi commit) để dịch được thành lỗi nghiệp vụ
                promotionUsageRepository.saveAndFlush(usage);
            } catch (DataIntegrityViolationException e) {
                // uk_promotion_user: 2 đơn cùng user + cùng code cùng lúc — đổi 500 thô thành lỗi nghiệp vụ
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Bạn đã sử dụng mã khuyến mãi '" + promotion.getCode() + "' trước đó");
            }
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

        User actor = requireUser(userId);

        if (actor.getRole() == RoleName.CUSTOMER && !order.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode);
        }

        if (actor.getRole() == RoleName.SHIPPER
                && (order.getShipper() == null || !order.getShipper().getId().equals(userId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không được phân công giao đơn hàng này");
        }

        storeAccessGuard.requireOrderAccess(actor, order);

        return toOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getUserOrders(Long userId, List<OrderStatus> statuses, int page, int size) {
        // Sắp xếp tường minh: đi qua Specification thì không còn thứ tự ngầm định từ tên method
        // như findByUserIdOrderByCreatedAtDesc trước đây.
        Pageable pageable = PageableFactory.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Order> spec = OrderSpecifications.ownedBy(userId, statuses);
        Page<Order> orderPage = orderRepository.findAll(spec, pageable);
        return PageResponse.from(orderPage.map(this::toOrderResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrdersForAdmin(Long userId, OrderStatus status, String fromDateStr, String toDateStr,
                                                            Long storeId, int page, int size) {
        User actor = requireUser(userId);

        storeAccessGuard.requireOperator(actor);
        Long scope = storeAccessGuard.resolveStoreFilter(actor, storeId);

        LocalDateTime fromDate = parseDateTime(fromDateStr, false);
        LocalDateTime toDate = parseDateTime(toDateStr, true);

        Pageable pageable = PageableFactory.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Order> spec = OrderSpecifications.withFilters(status, fromDate, toDate, scope);
        Page<Order> orderPage = orderRepository.findAll(spec, pageable);

        return PageResponse.from(orderPage.map(this::toOrderResponse));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse assignShipper(Long userId, String orderCode, AssignShipperRequest request) {
        log.info("Assigning shipper {} to order {} by user {}", request.getShipperId(), orderCode, userId);

        User actor = requireUser(userId);

        storeAccessGuard.requireOperator(actor);

        Order order = requireOrderByCode(orderCode);
        storeAccessGuard.requireOrderAccess(actor, order);

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.DELIVERED || currentStatus == OrderStatus.CANCELLED || currentStatus == OrderStatus.FAILED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Không thể gán shipper cho đơn hàng ở trạng thái kết thúc: " + currentStatus);
        }

        // requireShipper đã kiểm tra role SHIPPER
        User shipper = requireShipper(request.getShipperId());
        if (!storeAccessGuard.sameStore(shipper, order.getStore())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Tài xế " + shipper.getFullName() + " không thuộc cơ sở phục vụ đơn này");
        }

        // Chặn gán nếu shipper đang có đơn chưa hoàn tất (READY_FOR_PICKUP hoặc DELIVERING)
        long activeOrdersCount = orderRepository.countByShipperIdAndStatusInAndIdNot(
                shipper.getId(),
                java.util.List.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERING),
                order.getId()
        );
        if (activeOrdersCount > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Tài xế " + shipper.getFullName() + " hiện đang có đơn hàng chưa hoàn tất (" + activeOrdersCount + " đơn đang xử lý). Vui lòng chọn tài xế khác đang rảnh.");
        }

        Long previousShipperId = order.getShipper() == null ? null : order.getShipper().getId();
        order.setShipper(shipper);
        Order updatedOrder = orderRepository.save(order);

        // Ghi lịch sử gán shipper
        String historyNote = "Gán shipper: " + shipper.getFullName() + " (" + shipper.getPhone() + ")";
        if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
            historyNote += " - Ghi chú: " + request.getNote().trim();
        }
        recordHistory(updatedOrder, currentStatus, currentStatus, actor, historyNote,
                OrderChangeKind.SHIPPER_ASSIGNED, null, previousShipperId);

        log.info("Order {} assigned to shipper {} by {} ({})",
                orderCode, shipper.getFullName(), actor.getFullName(), actor.getRole());

        return toOrderResponse(updatedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrdersForShipper(Long userId, OrderStatus status, int page, int size) {
        User actor = requireUser(userId);

        if (actor.getRole() != RoleName.SHIPPER) {
            storeAccessGuard.requireOperator(actor);
        }

        Pageable pageable = PageableFactory.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        // SHIPPER chỉ thấy đơn được giao cho mình; STAFF/MANAGER thấy đơn của cơ sở mình,
        // ADMIN thấy toàn chuỗi (scopedStoreId trả null cho ADMIN).
        Specification<Order> spec = actor.getRole() == RoleName.SHIPPER
                ? OrderSpecifications.assignedTo(userId, status)
                : OrderSpecifications.withFilters(status, null, null, storeAccessGuard.scopedStoreId(actor));
        Page<Order> orderPage = orderRepository.findAll(spec, pageable);

        return PageResponse.from(orderPage.map(this::toOrderResponse));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse confirmDelivery(Long userId, String orderCode, ConfirmDeliveryRequest request) {
        log.info("Confirming delivery for order {} by user {}", orderCode, userId);

        User actor = requireUser(userId);

        if (actor.getRole() != RoleName.SHIPPER) {
            storeAccessGuard.requireOperator(actor);
        }

        Order order = requireOrderByCode(orderCode);
        storeAccessGuard.requireOrderAccess(actor, order);

        if (actor.getRole() == RoleName.SHIPPER) {
            if (order.getShipper() == null || !order.getShipper().getId().equals(actor.getId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không phải là shipper được phân công giao đơn hàng này");
            }
        }

        OrderStatus fromStatus = order.getStatus();
        OrderStatus toStatus = OrderStatus.DELIVERED;

        // Validate chuyển trạng thái sang DELIVERED (từ DELIVERING)
        orderStatusValidator.validateTransition(order, toStatus, actor);

        LocalDateTime now = LocalDateTime.now();
        order.setStatus(toStatus);
        order.setDeliveredAt(now);

        // AC 3: Cập nhật trạng thái thanh toán sang PAID (gom block mark-paid 2 bản copy về 1 helper)
        applyDeliveredPayment(order, now);

        Order updatedOrder = orderRepository.save(order);

        String note = "Shipper xác nhận giao hàng thành công";
        if (request != null && request.getNote() != null && !request.getNote().trim().isEmpty()) {
            note += ": " + request.getNote().trim();
        }
        recordHistory(updatedOrder, fromStatus, toStatus, actor, note);

        log.info("Order {} delivered successfully by user {} ({})", orderCode, userId, actor.getRole());

        return toOrderResponse(updatedOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse rejectAssignedOrder(Long userId, String orderCode, RejectOrderRequest request) {
        log.info("Shipper {} is rejecting order {}", userId, orderCode);

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        if (actor.getRole() != RoleName.SHIPPER && actor.getRole() != RoleName.STAFF && actor.getRole() != RoleName.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ nhân viên giao hàng mới có quyền từ chối nhận đơn");
        }

        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));

        if (order.getShipper() == null || !order.getShipper().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không phải là shipper được phân công giao đơn hàng này");
        }

        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Chỉ có thể từ chối đơn hàng khi ở trạng thái chờ lấy bánh (READY_FOR_PICKUP). Trạng thái hiện tại: " + order.getStatus());
        }

        String reason = (request != null && request.getReason() != null) ? request.getReason().trim() : "Không có lý do cụ thể";

        // Gỡ gán shipper để Staff phân công lại cho người khác; báo cả shipper vừa bị gỡ (previousShipperId)
        Long previousShipperId = order.getShipper().getId();
        order.setShipper(null);
        Order updatedOrder = orderRepository.save(order);
        recordHistory(updatedOrder, OrderStatus.READY_FOR_PICKUP, OrderStatus.READY_FOR_PICKUP, actor,
                "Tài xế " + actor.getFullName() + " (" + actor.getPhone() + ") từ chối nhận đơn: " + reason,
                OrderChangeKind.SHIPPER_ASSIGNED, null, previousShipperId);

        log.info("Order {} rejected by shipper {} ({}). Reason: {}. Order is now unassigned.",
                orderCode, actor.getFullName(), userId, reason);

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

        User actor = requireUser(userId);

        Order order = requireOrderByCode(orderCode);
        storeAccessGuard.requireOrderAccess(actor, order);

        String reason = (request != null && request.getCancelReason() != null) ? request.getCancelReason().trim() : null;

        // AC 3: Validate quyền hủy và điều kiện trạng thái qua OrderStatusValidator
        orderStatusValidator.validateCancel(order, actor, reason);

        OrderStatus fromStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);

        // Khép kín dòng tiền: đã thu tiền thì bắt buộc hoàn, không để tồn tại cặp (CANCELLED, PAID).
        Payment payment = order.getPayment();
        if (payment != null && payment.getStatus() == PaymentStatus.PAID) {
            paymentService.refundPayment(order, payment.getAmount(),
                    reason != null ? reason : "Huỷ đơn sau khi đã thanh toán", actor.getId());
        }
        // Hoàn hàng đã giữ về kho, rồi trả lại lượt mã khuyến mãi đã tiêu cho đơn này.
        // Hoàn kho TRƯỚC: release mã chạy bulk UPDATE, không đọc lazy items sau nó.
        inventoryService.restoreForOrder(order);
        promotionService.releaseForOrder(order);

        Order updatedOrder = orderRepository.save(order);

        // AC 2: Tự động ghi 1 dòng vào order_status_history
        recordHistory(updatedOrder, fromStatus, OrderStatus.CANCELLED, actor, reason);

        log.info("Order {} cancelled successfully from {} by user {} (role: {}). Reason: {}",
                orderCode, fromStatus, userId, actor.getRole(), reason);

        return toOrderResponse(updatedOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse refundOrder(Long userId, String orderCode, com.banhmyking.banhmyking.dto.order.RefundOrderRequest request) {
        User actor = requireUser(userId);
        storeAccessGuard.requireOperator(actor);

        Order order = requireOrderByCode(orderCode);
        storeAccessGuard.requireOrderAccess(actor, order);

        Payment payment = order.getPayment();
        if (payment == null) {
            payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
            order.setPayment(payment);
        }
        if (payment == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Đơn hàng chưa có bản ghi thanh toán");
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Chỉ hoàn tiền cho đơn đã thu tiền (trạng thái hiện tại: " + payment.getStatus() + ")");
        }

        paymentService.refundPayment(order, request.getAmount(), request.getReason(), actor.getId());
        recordHistory(order, order.getStatus(), order.getStatus(), actor,
                "Hoàn tiền: " + request.getReason());

        log.info("Order {} refunded by user {} (role: {})", orderCode, userId, actor.getRole());
        return toOrderResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse transferStore(Long userId, String orderCode,
                                       com.banhmyking.banhmyking.dto.store.TransferStoreRequest request) {
        User actor = requireUser(userId);
        if (actor.getRole() != RoleName.ADMIN && actor.getRole() != RoleName.MANAGER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ quản lý cơ sở hoặc quản trị viên mới được chuyển cơ sở");
        }
        Order order = requireOrderByCode(orderCode);
        storeAccessGuard.requireOrderAccess(actor, order);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Chỉ chuyển cơ sở được khi đơn còn PENDING. Trạng thái hiện tại: " + order.getStatus());
        }
        if (order.getStore() != null && order.getStore().getId().equals(request.storeId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Đơn đã thuộc cơ sở này");
        }

        java.util.Map<Product, Integer> demand = new java.util.LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            demand.merge(item.getProduct(), item.getQuantity(), Integer::sum);
        }
        com.banhmyking.banhmyking.service.StoreSelectionService.Candidate target = storeSelectionService.requireEligible(
                request.storeId(), order.getDeliveryLatitude(), order.getDeliveryLongitude(), order.getSubtotal(), demand);
        DeliveryFeeResult fee = deliveryFeeCalculator.calculateFee(target.distanceKm(), order.getShippingAddress(),
                order.getSubtotal(), target.store().getFreeShipRadiusKm());
        BigDecimal newFee = fee.getShippingFee().setScale(2, RoundingMode.HALF_UP);

        // Giảm giá phải tính lại theo phí ship mới: FREE_SHIP = min(value, phí ship) nên đổi phí là đổi
        // tiền giảm. Dùng đúng công thức lúc tạo đơn (PriceCalculator.computeDiscount). KHÔNG chấm lại
        // điều kiện mã (hạn, lượt, đơn tối thiểu): mã đã được chấp nhận + đã tiêu lượt cho chính đơn này,
        // và subtotal không đổi khi chuyển cơ sở nên điều kiện đơn tối thiểu vẫn y nguyên.
        BigDecimal newDiscount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        PromotionUsage usage = null;
        if (order.getPromotionCode() != null) {
            usage = order.getId() == null ? null
                    : promotionUsageRepository.findByOrderId(order.getId()).orElse(null);
            Promotion promotion = usage != null && usage.getPromotion() != null
                    ? usage.getPromotion()
                    : promotionRepository.findByCode(order.getPromotionCode()).orElse(null);
            if (promotion != null) {
                newDiscount = priceCalculator.computeDiscount(promotion, order.getSubtotal(), newFee);
            } else {
                log.warn("transferStore: không tìm thấy mã {} của đơn {} — giữ nguyên tiền giảm cũ",
                        order.getPromotionCode(), orderCode);
            }
        }
        newDiscount = newDiscount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal newTotal = order.getSubtotal().add(newFee).subtract(newDiscount)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        Payment payment = order.getPayment();
        if (payment == null && order.getId() != null) {
            payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        }
        boolean paid = payment != null && payment.getStatus() == PaymentStatus.PAID;
        if (paid && newTotal.compareTo(order.getTotal()) != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Đơn đã thanh toán — không thể chuyển sang cơ sở làm thay đổi tổng tiền");
        }
        // R11: chuyển khoản đang chờ — khách có thể đang chuyển đúng số tiền cũ; webhook SePay đòi khớp
        // chính xác order.total, nên đổi tổng tiền lúc này sẽ đẩy đơn vào đối soát tay. Từ chối như đơn đã trả.
        boolean bankTransferPending = payment != null
                && payment.getMethod() == PaymentMethod.BANK_TRANSFER
                && payment.getStatus() == PaymentStatus.PENDING;
        if (bankTransferPending && newTotal.compareTo(order.getTotal()) != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Đơn chuyển khoản đang chờ thanh toán — không thể chuyển sang cơ sở làm thay đổi số tiền");
        }

        Long previousStoreId = order.getStore() == null ? null : order.getStore().getId();
        Long previousShipperId = order.getShipper() == null ? null : order.getShipper().getId();
        String fromName = order.getStore() == null ? "?" : order.getStore().getName();
        order.setStore(target.store());
        order.setDistanceKm(target.distanceKm());
        order.setShippingFee(newFee);
        order.setDiscountAmount(newDiscount);
        order.setTotal(newTotal);
        if (usage != null && (usage.getDiscountApplied() == null
                || usage.getDiscountApplied().compareTo(newDiscount) != 0)) {
            usage.setDiscountApplied(newDiscount);
            promotionUsageRepository.save(usage);
        }
        if (payment != null && !paid) {
            payment.setAmount(newTotal);
            paymentRepository.save(payment);
        }
        String note = "Chuyển từ " + fromName + " sang " + target.store().getName() + ": " + request.reason().trim();
        User assigned = order.getShipper();
        if (assigned != null && !storeAccessGuard.sameStore(assigned, target.store())) {
            order.setShipper(null);
            note += " (đã gỡ tài xế " + assigned.getFullName() + " của cơ sở cũ)";
        }
        Order saved = orderRepository.save(order);
        recordHistory(saved, OrderStatus.PENDING, OrderStatus.PENDING, actor, note,
                OrderChangeKind.STORE_TRANSFERRED, previousStoreId, previousShipperId);
        return toOrderResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelStalePendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(pendingTimeoutMinutes);
        List<Order> staleOrders = orderRepository.findStalePendingUnpaid(cutoff);
        if (staleOrders.isEmpty()) {
            return 0;
        }
        for (Order order : staleOrders) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelReason("Tự động huỷ: quá hạn thanh toán " + pendingTimeoutMinutes + " phút");
            promotionService.releaseForOrder(order);
            orderRepository.save(order);
            recordHistory(order, OrderStatus.PENDING, OrderStatus.CANCELLED, null, order.getCancelReason());
        }
        log.info("Auto-cancelled {} stale unpaid order(s) older than {} minutes",
                staleOrders.size(), pendingTimeoutMinutes);
        return staleOrders.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse updateOrderStatus(Long userId, String orderCode, UpdateOrderStatusRequest request) {
        log.info("Updating status for order {} to {} by user {}", orderCode, request.getNewStatus(), userId);

        User actor = requireUser(userId);

        Order order = requireOrderByCode(orderCode);
        storeAccessGuard.requireOrderAccess(actor, order);

        OrderStatus fromStatus = order.getStatus();
        OrderStatus toStatus = request.getNewStatus();

        // AC 1: Validate state machine (chặn nhảy cóc, FAILED chỉ từ DELIVERING, phân quyền vận hành)
        orderStatusValidator.validateTransition(order, toStatus, actor);

        // Huỷ đơn phải kèm lý do → đi qua /cancel, không cho huỷ "câm" qua đổi trạng thái.
        if (toStatus == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Vui lòng huỷ đơn qua chức năng huỷ đơn (bắt buộc kèm lý do)");
        }
        // Xác nhận đơn: mã khuyến mãi phải còn hiệu lực tại thời điểm xác nhận.
        if (toStatus == OrderStatus.CONFIRMED) {
            promotionService.assertStillValidForConfirm(order);
        }

        // Shipper chỉ được thao tác trên đơn được phân công cho mình (khác confirmDelivery đã check ở trên)
        if (actor.getRole() == RoleName.SHIPPER
                && (order.getShipper() == null || !order.getShipper().getId().equals(actor.getId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không phải là shipper được phân công giao đơn hàng này");
        }

        // Gán shipper nếu chuyển sang DELIVERING — việc phân công là của STAFF/ADMIN
        // Nhớ shipper cũ để báo cho họ biết đơn đã bị chuyển đi (realtime)
        Long previousShipperId = order.getShipper() == null ? null : order.getShipper().getId();
        if (toStatus == OrderStatus.DELIVERING) {
            if (request.getShipperId() != null) {
                if (actor.getRole() == RoleName.SHIPPER) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "Shipper không có quyền phân công đơn hàng cho người khác");
                }
                User newShipper = requireShipper(request.getShipperId());
                if (!storeAccessGuard.sameStore(newShipper, order.getStore())) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                            "Tài xế " + newShipper.getFullName() + " không thuộc cơ sở phục vụ đơn này");
                }
                order.setShipper(newShipper);
            } else if (order.getShipper() == null) {
                // Không có shipperId và đơn chưa được gán → chặn, tránh đơn DELIVERING không có shipper
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Cần chỉ định shipperId khi chuyển sang DELIVERING (đơn chưa được phân công)");
            }
        }

        // Đóng dấu thời gian giao hàng thành công & cập nhật thanh toán nếu hoàn thành
        if (toStatus == OrderStatus.DELIVERED) {
            LocalDateTime now = LocalDateTime.now();
            order.setDeliveredAt(now);
            applyDeliveredPayment(order, now);
        }

        if (toStatus == OrderStatus.FAILED && request.getNote() != null && !request.getNote().trim().isEmpty()) {
            order.setCancelReason(request.getNote().trim());
        }

        // Giao thất bại cũng là kết thúc không thu được tiền: hoàn tiền (nếu đã thu) và trả lượt KM.
        if (toStatus == OrderStatus.FAILED) {
            Payment failedPayment = order.getPayment();
            if (failedPayment != null && failedPayment.getStatus() == PaymentStatus.PAID) {
                paymentService.refundPayment(order, failedPayment.getAmount(),
                        "Giao hàng thất bại, hoàn tiền cho khách", actor.getId());
            }
            inventoryService.restoreForOrder(order);
            promotionService.releaseForOrder(order);
        }

        // Xác nhận đơn là lúc giữ hàng: trừ tồn ngay, không đủ thì fail cả đơn.
        if (toStatus == OrderStatus.CONFIRMED) {
            inventoryService.decreaseForOrder(order);
        }

        order.setStatus(toStatus);
        Order updatedOrder = orderRepository.save(order);

        // AC 2: Tự động ghi 1 dòng vào order_status_history
        recordHistory(updatedOrder, fromStatus, toStatus, actor, request.getNote(), null, null, previousShipperId);

        log.info("Order {} transitioned from {} to {} by user {} (role: {})",
                orderCode, fromStatus, toStatus, userId, actor.getRole());

        return toOrderResponse(updatedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> getOrderStatusHistory(Long userId, String orderCode) {
        User actor = requireUser(userId);

        Order order = requireOrderByCode(orderCode);
        storeAccessGuard.requireOrderAccess(actor, order);

        // Customer chỉ được xem lịch sử đơn hàng của chính mình
        if (actor.getRole() == RoleName.CUSTOMER && (order.getUser() == null || !order.getUser().getId().equals(actor.getId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem lịch sử đơn hàng của người khác");
        }

        // Shipper chỉ được xem lịch sử đơn hàng được phân công cho mình
        if (actor.getRole() == RoleName.SHIPPER && (order.getShipper() == null || !order.getShipper().getId().equals(actor.getId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không phải là shipper được phân công giao đơn hàng này");
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
        BigDecimal savings = BigDecimal.ZERO;
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                List<OrderItemComponentResponse> componentResponses = new ArrayList<>();
                if (item.getComponents() != null) {
                    for (OrderItemComponent component : item.getComponents()) {
                        componentResponses.add(new OrderItemComponentResponse(
                                component.getProductName(), component.getQuantity()));
                    }
                }
                savings = savings.add(savingsOf(item));
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
                        .originalUnitPrice(item.getOriginalUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getLineTotal())
                        .options(optionResponses)
                        .components(componentResponses)
                        .build());
            }
        }

        Payment payment = order.getPayment();
        if (payment == null && order.getId() != null) {
            payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .shippingAddress(order.getShippingAddress())
                .storeId(order.getStore() != null ? order.getStore().getId() : null)
                .storeName(order.getStore() != null ? order.getStore().getName() : null)
                .storePhone(order.getStore() != null ? order.getStore().getPhone() : null)
                .distanceKm(order.getDistanceKm())
                .deliveryLatitude(order.getDeliveryLatitude())
                .deliveryLongitude(order.getDeliveryLongitude())
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .savingsAmount(savings.setScale(2, RoundingMode.HALF_UP))
                .total(order.getTotal())
                .promotionCode(order.getPromotionCode())
                .paymentMethod(payment != null ? payment.getMethod() : PaymentMethod.COD)
                .paymentStatus(payment != null ? payment.getStatus() : PaymentStatus.PENDING)
                .refundAmount(payment != null ? payment.getRefundAmount() : null)
                .refundReason(payment != null ? payment.getRefundReason() : null)
                .refundedAt(payment != null ? payment.getRefundedAt() : null)
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

    /** Tiết kiệm của dòng đơn = (giá gốc − giá bán) × số lượng; đơn cũ (originalUnitPrice NULL) = 0. */
    private static BigDecimal savingsOf(OrderItem item) {
        if (item.getOriginalUnitPrice() == null || item.getUnitPrice() == null || item.getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal perUnit = item.getOriginalUnitPrice().subtract(item.getUnitPrice());
        return perUnit.signum() > 0 ? perUnit.multiply(BigDecimal.valueOf(item.getQuantity())) : BigDecimal.ZERO;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.List<com.banhmyking.banhmyking.dto.order.ShipperAvailabilityResponse> getAvailableShippers(Long userId, Long storeId) {
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        storeAccessGuard.requireOperator(actor);
        Long scope = storeAccessGuard.resolveStoreFilter(actor, storeId);

        java.util.List<User> shippers = scope == null
                ? userRepository.findByRoleAndDeletedFalse(RoleName.SHIPPER)
                : userRepository.findByRoleAndStoreIdAndDeletedFalse(RoleName.SHIPPER, scope);
        java.util.List<com.banhmyking.banhmyking.dto.order.ShipperAvailabilityResponse> result = new java.util.ArrayList<>();

        for (User s : shippers) {
            if (s.isBanned()) {
                continue;
            }
            long activeOrdersCount = orderRepository.countByShipperIdAndStatusIn(
                    s.getId(),
                    java.util.List.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERING)
            );
            result.add(com.banhmyking.banhmyking.dto.order.ShipperAvailabilityResponse.builder()
                    .id(s.getId())
                    .fullName(s.getFullName())
                    .phone(s.getPhone())
                    .email(s.getEmail())
                    .activeOrdersCount(activeOrdersCount)
                    .available(activeOrdersCount == 0)
                    .build());
        }

        // Ưu tiên shipper rảnh (activeOrdersCount == 0), sau đó theo số đơn ít nhất
        result.sort(java.util.Comparator.comparingLong(com.banhmyking.banhmyking.dto.order.ShipperAvailabilityResponse::getActiveOrdersCount));
        return result;
    }
}
