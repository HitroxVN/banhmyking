package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.dto.order.AssignShipperRequest;
import com.banhmyking.banhmyking.dto.store.TransferStoreRequest;
import com.banhmyking.banhmyking.dto.delivery.DeliveryFeeResult;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.Payment;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.PaymentMethod;
import com.banhmyking.banhmyking.enums.PaymentStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.OrderStatusHistoryRepository;
import com.banhmyking.banhmyking.repository.PaymentRepository;
import com.banhmyking.banhmyking.repository.PromotionRepository;
import com.banhmyking.banhmyking.repository.PromotionUsageRepository;
import com.banhmyking.banhmyking.entity.Promotion;
import com.banhmyking.banhmyking.entity.PromotionUsage;
import com.banhmyking.banhmyking.enums.DiscountType;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.StoreAccessGuard;
import com.banhmyking.banhmyking.service.impl.OrderServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.banhmyking.banhmyking.entity.OrderStatusHistory;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderStoreScopeTest {

    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock private StoreSelectionService storeSelectionService;
    @Mock private DeliveryFeeCalculator deliveryFeeCalculator;
    @Mock private PromotionUsageRepository promotionUsageRepository;
    @Mock private PromotionRepository promotionRepository;
    @Spy private StoreAccessGuard storeAccessGuard = new StoreAccessGuard();
    @Spy private PriceCalculator priceCalculator = new PriceCalculator(
            new ProductPricing(java.time.Clock.system(com.banhmyking.banhmyking.config.TimeConfig.VIETNAM)));
    @InjectMocks private OrderServiceImpl orderService;

    private Store storeA;
    private Store storeB;
    private User managerA;
    private Order orderB;

    @BeforeEach
    void setUp() {
        storeA = new Store();
        storeA.setId(1L);
        storeA.setName("Cơ sở A");
        storeB = new Store();
        storeB.setId(2L);
        storeB.setName("Cơ sở B");
        managerA = new User();
        managerA.setId(10L);
        managerA.setRole(RoleName.MANAGER);
        managerA.setStore(storeA);
        orderB = new Order();
        orderB.setId(500L);
        orderB.setOrderCode("BMK-B");
        orderB.setStore(storeB);
        orderB.setStatus(OrderStatus.PENDING);
        when(userRepository.findById(10L)).thenReturn(Optional.of(managerA));
    }

    @Test
    void managerCannotAssignShipperOnOtherStoreOrder() {
        when(orderRepository.findByOrderCode("BMK-B")).thenReturn(Optional.of(orderB));
        AssignShipperRequest request = new AssignShipperRequest();
        request.setShipperId(20L);

        assertThatThrownBy(() -> orderService.assignShipper(10L, "BMK-B", request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shipperOfAnotherStoreCannotBeAssigned() {
        Order orderA = new Order();
        orderA.setId(501L);
        orderA.setOrderCode("BMK-A");
        orderA.setStore(storeA);
        orderA.setStatus(OrderStatus.READY_FOR_PICKUP);
        User shipperB = new User();
        shipperB.setId(20L);
        shipperB.setRole(RoleName.SHIPPER);
        shipperB.setStore(storeB);
        when(orderRepository.findByOrderCode("BMK-A")).thenReturn(Optional.of(orderA));
        when(userRepository.findById(20L)).thenReturn(Optional.of(shipperB));
        AssignShipperRequest request = new AssignShipperRequest();
        request.setShipperId(20L);

        assertThatThrownBy(() -> orderService.assignShipper(10L, "BMK-A", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không thuộc cơ sở");
    }

    @Test
    void managerListIsLockedToOwnStore() {
        assertThatThrownBy(() -> orderService.getAllOrdersForAdmin(10L, null, null, null, 2L, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void transferMovesPendingOrderAndRecomputesTotalWhenUnpaid() {
        Order orderA = new Order();
        orderA.setId(502L);
        orderA.setOrderCode("BMK-T");
        orderA.setStore(storeA);
        orderA.setStatus(OrderStatus.PENDING);
        orderA.setShippingAddress("12 Láng Hạ");
        orderA.setSubtotal(new BigDecimal("60000"));
        orderA.setShippingFee(new BigDecimal("15000"));
        orderA.setDiscountAmount(BigDecimal.ZERO);
        orderA.setTotal(new BigDecimal("75000"));
        OrderItem item = new OrderItem();
        item.setProduct(new Product());
        item.setQuantity(1);
        orderA.setItems(List.of(item));
        when(orderRepository.findByOrderCode("BMK-T")).thenReturn(Optional.of(orderA));
        when(storeSelectionService.requireEligible(eq(2L), any(), any(), any(), anyMap()))
                .thenReturn(new StoreSelectionService.Candidate(storeB, null, List.of(), List.of()));
        when(deliveryFeeCalculator.calculateFee(any(), any(), any(), any()))
                .thenReturn(DeliveryFeeResult.builder().shippingFee(new BigDecimal("20000")).build());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = orderService.transferStore(10L, "BMK-T", new TransferStoreRequest(2L, "Hết bánh"));

        assertThat(response.getStoreId()).isEqualTo(2L);
        assertThat(orderA.getShippingFee()).isEqualByComparingTo("20000");
        assertThat(orderA.getTotal()).isEqualByComparingTo("80000");
    }

    @Test
    void transferRemovesShipperOfOldStoreAndRecordsIt() {
        Order order = new Order();
        order.setId(504L);
        order.setOrderCode("BMK-S");
        order.setStore(storeA);
        order.setStatus(OrderStatus.PENDING);
        order.setSubtotal(new BigDecimal("60000"));
        order.setShippingFee(new BigDecimal("15000"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("75000"));
        order.setItems(List.of());
        User shipperA = new User();
        shipperA.setId(21L);
        shipperA.setFullName("Tài Xế A");
        shipperA.setRole(RoleName.SHIPPER);
        shipperA.setStore(storeA);
        order.setShipper(shipperA);
        when(orderRepository.findByOrderCode("BMK-S")).thenReturn(Optional.of(order));
        when(storeSelectionService.requireEligible(eq(2L), any(), any(), any(), anyMap()))
                .thenReturn(new StoreSelectionService.Candidate(storeB, null, List.of(), List.of()));
        when(deliveryFeeCalculator.calculateFee(any(), any(), any(), any()))
                .thenReturn(DeliveryFeeResult.builder().shippingFee(new BigDecimal("15000")).build());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = orderService.transferStore(10L, "BMK-S", new TransferStoreRequest(2L, "Hết bánh"));

        assertThat(order.getShipper()).isNull();
        assertThat(response.getStoreId()).isEqualTo(2L);
        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getNote()).contains("Tài Xế A");
    }

    @Test
    void transferRefusedWhenPaidAndTotalWouldChange() {
        Order paid = new Order();
        paid.setId(503L);
        paid.setOrderCode("BMK-P");
        paid.setStore(storeA);
        paid.setStatus(OrderStatus.PENDING);
        paid.setSubtotal(new BigDecimal("60000"));
        paid.setShippingFee(new BigDecimal("15000"));
        paid.setDiscountAmount(BigDecimal.ZERO);
        paid.setTotal(new BigDecimal("75000"));
        paid.setItems(List.of());
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.PAID);
        paid.setPayment(payment);
        when(orderRepository.findByOrderCode("BMK-P")).thenReturn(Optional.of(paid));
        when(storeSelectionService.requireEligible(eq(2L), any(), any(), any(), anyMap()))
                .thenReturn(new StoreSelectionService.Candidate(storeB, null, List.of(), List.of()));
        when(deliveryFeeCalculator.calculateFee(any(), any(), any(), any()))
                .thenReturn(DeliveryFeeResult.builder().shippingFee(new BigDecimal("30000")).build());

        assertThatThrownBy(() -> orderService.transferStore(10L, "BMK-P", new TransferStoreRequest(2L, "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã thanh toán");
    }

    // ---- M2 / R11: chuyển khoản đang chờ thanh toán không được đổi số tiền ----

    private Order pendingPaymentOrder(String code, PaymentMethod method) {
        Order order = new Order();
        order.setId(700L);
        order.setOrderCode(code);
        order.setStore(storeA);
        order.setStatus(OrderStatus.PENDING);
        order.setSubtotal(new BigDecimal("60000"));
        order.setShippingFee(new BigDecimal("15000"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("75000"));
        order.setItems(List.of());
        Payment payment = new Payment();
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("75000"));
        order.setPayment(payment);
        return order;
    }

    @Test
    void transferRefusedForPendingBankTransferWhenTotalWouldChange() {
        Order order = pendingPaymentOrder("BMK-BT1", PaymentMethod.BANK_TRANSFER);
        when(orderRepository.findByOrderCode("BMK-BT1")).thenReturn(Optional.of(order));
        stubTransferTo("20000");

        assertThatThrownBy(() -> orderService.transferStore(10L, "BMK-BT1", new TransferStoreRequest(2L, "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chuyển khoản đang chờ thanh toán");
        assertThat(order.getStore()).isSameAs(storeA);
        assertThat(order.getPayment().getAmount()).isEqualByComparingTo("75000");
        verify(orderRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void transferAllowedForPendingBankTransferWhenTotalUnchanged() {
        Order order = pendingPaymentOrder("BMK-BT2", PaymentMethod.BANK_TRANSFER);
        when(orderRepository.findByOrderCode("BMK-BT2")).thenReturn(Optional.of(order));
        stubTransferTo("15000");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.transferStore(10L, "BMK-BT2", new TransferStoreRequest(2L, "Hết bánh"));

        assertThat(order.getStore()).isSameAs(storeB);
        assertThat(order.getTotal()).isEqualByComparingTo("75000");
        assertThat(order.getPayment().getAmount()).isEqualByComparingTo("75000");
    }

    @Test
    void transferOfPendingCodOrderMayChangeTotal() {
        Order order = pendingPaymentOrder("BMK-COD", PaymentMethod.COD);
        when(orderRepository.findByOrderCode("BMK-COD")).thenReturn(Optional.of(order));
        stubTransferTo("20000");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.transferStore(10L, "BMK-COD", new TransferStoreRequest(2L, "Hết bánh"));

        assertThat(order.getTotal()).isEqualByComparingTo("80000");
        assertThat(order.getPayment().getAmount()).isEqualByComparingTo("80000");
        verify(paymentRepository).save(order.getPayment());
    }

    // ---- I1: tiền giảm phải tính lại theo phí ship của cơ sở mới ----

    private Order promoOrder(String code, String fee, String discount, String total, String promoCode) {
        Order order = new Order();
        order.setId(600L);
        order.setOrderCode(code);
        order.setStore(storeA);
        order.setStatus(OrderStatus.PENDING);
        order.setShippingAddress("12 Láng Hạ");
        order.setSubtotal(new BigDecimal("60000"));
        order.setShippingFee(new BigDecimal(fee));
        order.setDiscountAmount(new BigDecimal(discount));
        order.setTotal(new BigDecimal(total));
        order.setPromotionCode(promoCode);
        order.setItems(List.of());
        return order;
    }

    private PromotionUsage usageFor(Order order, Promotion promotion, String applied) {
        PromotionUsage usage = new PromotionUsage();
        usage.setPromotion(promotion);
        usage.setOrder(order);
        usage.setDiscountApplied(new BigDecimal(applied));
        return usage;
    }

    private void stubTransferTo(String newFee) {
        when(storeSelectionService.requireEligible(eq(2L), any(), any(), any(), anyMap()))
                .thenReturn(new StoreSelectionService.Candidate(storeB, null, List.of(), List.of()));
        when(deliveryFeeCalculator.calculateFee(any(), any(), any(), any()))
                .thenReturn(DeliveryFeeResult.builder().shippingFee(new BigDecimal(newFee)).build());
    }

    private static Promotion freeShip(String cap) {
        return Promotion.builder().code("FREESHIP").discountType(DiscountType.FREE_SHIP)
                .value(new BigDecimal(cap)).minOrderAmount(BigDecimal.ZERO).build();
    }

    @Test
    void transferRecomputesFreeShipDiscountWhenNewFeeIsLower() {
        // fee 25 000, FREE_SHIP trần 30 000 → giảm 25 000, tổng 60 000. Sang cơ sở miễn ship (fee 0).
        Order order = promoOrder("BMK-F1", "25000", "25000", "60000", "FREESHIP");
        Promotion promo = freeShip("30000");
        PromotionUsage usage = usageFor(order, promo, "25000");
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("60000"));
        order.setPayment(payment);
        when(orderRepository.findByOrderCode("BMK-F1")).thenReturn(Optional.of(order));
        when(promotionUsageRepository.findByOrderId(600L)).thenReturn(Optional.of(usage));
        stubTransferTo("0");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.transferStore(10L, "BMK-F1", new TransferStoreRequest(2L, "Hết bánh"));

        assertThat(order.getShippingFee()).isEqualByComparingTo("0");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(order.getTotal()).isEqualByComparingTo("60000"); // trước fix: 35 000
        assertThat(payment.getAmount()).isEqualByComparingTo("60000");
        assertThat(usage.getDiscountApplied()).isEqualByComparingTo("0");
        verify(promotionUsageRepository).save(usage);
    }

    @Test
    void transferRecomputesFreeShipDiscountCappedWhenNewFeeIsHigher() {
        // fee 25 000 → 35 000, trần 30 000 → giảm 30 000, tổng 65 000 (trước fix: 70 000).
        Order order = promoOrder("BMK-F2", "25000", "25000", "60000", "FREESHIP");
        Promotion promo = freeShip("30000");
        PromotionUsage usage = usageFor(order, promo, "25000");
        when(orderRepository.findByOrderCode("BMK-F2")).thenReturn(Optional.of(order));
        when(promotionUsageRepository.findByOrderId(600L)).thenReturn(Optional.of(usage));
        stubTransferTo("35000");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.transferStore(10L, "BMK-F2", new TransferStoreRequest(2L, "Hết bánh"));

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("30000");
        assertThat(order.getTotal()).isEqualByComparingTo("65000");
        assertThat(usage.getDiscountApplied()).isEqualByComparingTo("30000");
    }

    @Test
    void transferOfPaidFreeShipOrderAllowedWhenTotalStaysTheSame() {
        // Đã thanh toán 60 000: fee 15 000 → 20 000 nhưng FREE_SHIP (trần 30 000) bù hết → tổng không đổi.
        Order order = promoOrder("BMK-F3", "15000", "15000", "60000", "FREESHIP");
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.PAID);
        payment.setAmount(new BigDecimal("60000"));
        order.setPayment(payment);
        Promotion promo = freeShip("30000");
        PromotionUsage usage = usageFor(order, promo, "15000");
        when(orderRepository.findByOrderCode("BMK-F3")).thenReturn(Optional.of(order));
        when(promotionUsageRepository.findByOrderId(600L)).thenReturn(Optional.of(usage));
        stubTransferTo("20000");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.transferStore(10L, "BMK-F3", new TransferStoreRequest(2L, "Hết bánh"));

        assertThat(order.getStore()).isSameAs(storeB);
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("20000");
        assertThat(order.getTotal()).isEqualByComparingTo("60000");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void transferKeepsNonShippingPromotionDiscount() {
        // PERCENTAGE 10% trên subtotal 60 000 → giảm 6 000, không phụ thuộc phí ship.
        Order order = promoOrder("BMK-F4", "15000", "6000", "69000", "GIAM10");
        Promotion promo = Promotion.builder().code("GIAM10").discountType(DiscountType.PERCENTAGE)
                .value(new BigDecimal("10")).minOrderAmount(BigDecimal.ZERO).build();
        PromotionUsage usage = usageFor(order, promo, "6000");
        when(orderRepository.findByOrderCode("BMK-F4")).thenReturn(Optional.of(order));
        when(promotionUsageRepository.findByOrderId(600L)).thenReturn(Optional.of(usage));
        stubTransferTo("25000");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.transferStore(10L, "BMK-F4", new TransferStoreRequest(2L, "Hết bánh"));

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("6000");
        assertThat(order.getTotal()).isEqualByComparingTo("79000");
        verify(promotionUsageRepository, never()).save(any());
    }

    @Test
    void transferFallsBackToPromotionCodeWhenUsageMissing() {
        Order order = promoOrder("BMK-F5", "25000", "25000", "60000", "FREESHIP");
        when(orderRepository.findByOrderCode("BMK-F5")).thenReturn(Optional.of(order));
        when(promotionUsageRepository.findByOrderId(600L)).thenReturn(Optional.empty());
        when(promotionRepository.findByCode("FREESHIP")).thenReturn(Optional.of(freeShip("30000")));
        stubTransferTo("10000");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.transferStore(10L, "BMK-F5", new TransferStoreRequest(2L, "Hết bánh"));

        assertThat(order.getDiscountAmount()).isEqualByComparingTo("10000");
        assertThat(order.getTotal()).isEqualByComparingTo("60000");
    }

    @Test
    void transferOnlyFromPending() {
        Order confirmed = new Order();
        confirmed.setOrderCode("BMK-C");
        confirmed.setStore(storeA);
        confirmed.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByOrderCode("BMK-C")).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> orderService.transferStore(10L, "BMK-C", new TransferStoreRequest(2L, "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PENDING");
    }
}
