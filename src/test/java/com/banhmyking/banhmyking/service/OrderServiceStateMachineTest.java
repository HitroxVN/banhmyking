package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.order.CancelOrderRequest;
import com.banhmyking.banhmyking.dto.order.OrderResponse;
import com.banhmyking.banhmyking.dto.order.OrderStatusHistoryResponse;
import com.banhmyking.banhmyking.dto.order.UpdateOrderStatusRequest;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderStatusHistory;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.repository.AddressRepository;
import com.banhmyking.banhmyking.repository.CartRepository;
import com.banhmyking.banhmyking.repository.OrderItemRepository;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.OrderStatusHistoryRepository;
import com.banhmyking.banhmyking.repository.PaymentRepository;
import com.banhmyking.banhmyking.repository.PromotionRepository;
import com.banhmyking.banhmyking.repository.PromotionUsageRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.impl.OrderServiceImpl;
import com.banhmyking.banhmyking.util.OrderCodeGenerator;
import com.banhmyking.banhmyking.validator.OrderStatusValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceStateMachineTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartService cartService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromotionUsageRepository promotionUsageRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PriceCalculator priceCalculator;

    @Mock
    private OrderCodeGenerator orderCodeGenerator;

    @Spy
    private OrderStatusValidator orderStatusValidator = new OrderStatusValidator();

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User customer;
    private User staff;
    private User shipper;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setId(2L);
        customer.setFullName("Nguyễn Văn Khách");
        customer.setRole(RoleName.CUSTOMER);

        staff = new User();
        staff.setId(4L);
        staff.setFullName("Nhân Viên Quán");
        staff.setRole(RoleName.STAFF);

        shipper = new User();
        shipper.setId(3L);
        shipper.setFullName("Trần Văn Giao");
        shipper.setRole(RoleName.SHIPPER);

        testOrder = new Order();
        testOrder.setId(100L);
        testOrder.setOrderCode("BMK-20260909-ABCDE");
        testOrder.setStatus(OrderStatus.PENDING);
        testOrder.setUser(customer);
        testOrder.setSubtotal(BigDecimal.valueOf(50000));
        testOrder.setShippingFee(BigDecimal.valueOf(15000));
        testOrder.setTotal(BigDecimal.valueOf(65000));
    }

    @Test
    @DisplayName("Customer hủy đơn PENDING thành công: Cập nhật status CANCELLED và lưu order_status_history")
    void cancelOrder_byCustomer_success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByOrderCode("BMK-20260909-ABCDE")).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        CancelOrderRequest request = CancelOrderRequest.builder()
                .cancelReason("Đổi ý không ăn nữa")
                .build();

        OrderResponse response = orderService.cancelOrder(2L, "BMK-20260909-ABCDE", request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.getCancelReason()).isEqualTo("Đổi ý không ăn nữa");

        // Kiểm tra lưu history
        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());

        OrderStatusHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedHistory.getToStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(savedHistory.getChangedBy().getId()).isEqualTo(2L);
        assertThat(savedHistory.getNote()).isEqualTo("Đổi ý không ăn nữa");
    }

    @Test
    @DisplayName("Staff hủy đơn PREPARING kèm lý do: Thành công và ghi log vào order_status_history")
    void cancelOrder_byStaff_withReason_success() {
        testOrder.setStatus(OrderStatus.PREPARING);

        when(userRepository.findById(4L)).thenReturn(Optional.of(staff));
        when(orderRepository.findByOrderCode("BMK-20260909-ABCDE")).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        CancelOrderRequest request = CancelOrderRequest.builder()
                .cancelReason("Bếp hết bánh mì")
                .build();

        OrderResponse response = orderService.cancelOrder(4L, "BMK-20260909-ABCDE", request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.getCancelReason()).isEqualTo("Bếp hết bánh mì");

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());

        OrderStatusHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getFromStatus()).isEqualTo(OrderStatus.PREPARING);
        assertThat(savedHistory.getToStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(savedHistory.getChangedBy().getId()).isEqualTo(4L);
        assertThat(savedHistory.getNote()).isEqualTo("Bếp hết bánh mì");
    }

    @Test
    @DisplayName("Cập nhật trạng thái PENDING -> CONFIRMED bởi Staff: Thành công và ghi order_status_history")
    void updateOrderStatus_pendingToConfirmed_success() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(staff));
        when(orderRepository.findByOrderCode("BMK-20260909-ABCDE")).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                .newStatus(OrderStatus.CONFIRMED)
                .note("Nhân viên xác nhận đơn")
                .build();

        OrderResponse response = orderService.updateOrderStatus(4L, "BMK-20260909-ABCDE", request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(historyCaptor.capture());

        OrderStatusHistory history = historyCaptor.getValue();
        assertThat(history.getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(history.getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(history.getChangedBy().getId()).isEqualTo(4L);
        assertThat(history.getNote()).isEqualTo("Nhân viên xác nhận đơn");
    }

    @Test
    @DisplayName("Cập nhật DELIVERING -> DELIVERED: Đóng dấu deliveredAt và ghi order_status_history")
    void updateOrderStatus_deliveringToDelivered_success() {
        testOrder.setStatus(OrderStatus.DELIVERING);
        testOrder.setShipper(shipper); // shipper 3 là người được phân công

        when(userRepository.findById(3L)).thenReturn(Optional.of(shipper));
        when(orderRepository.findByOrderCode("BMK-20260909-ABCDE")).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                .newStatus(OrderStatus.DELIVERED)
                .note("Đã giao hàng và thu tiền COD")
                .build();

        OrderResponse response = orderService.updateOrderStatus(3L, "BMK-20260909-ABCDE", request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(response.getDeliveredAt()).isNotNull();

        verify(orderStatusHistoryRepository).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("Cập nhật DELIVERING -> FAILED: Cho phép và ghi log vào order_status_history")
    void updateOrderStatus_deliveringToFailed_success() {
        testOrder.setStatus(OrderStatus.DELIVERING);
        testOrder.setShipper(shipper); // shipper 3 là người được phân công

        when(userRepository.findById(3L)).thenReturn(Optional.of(shipper));
        when(orderRepository.findByOrderCode("BMK-20260909-ABCDE")).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                .newStatus(OrderStatus.FAILED)
                .note("Khách không nghe máy sau 3 cuộc gọi")
                .build();

        OrderResponse response = orderService.updateOrderStatus(3L, "BMK-20260909-ABCDE", request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderStatusHistoryRepository).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("Chặn xem lịch sử đơn hàng của người khác đối với Customer")
    void getOrderStatusHistory_customerForbiddenForOtherUserOrder() {
        User otherCustomer = new User();
        otherCustomer.setId(99L);
        otherCustomer.setRole(RoleName.CUSTOMER);

        when(userRepository.findById(99L)).thenReturn(Optional.of(otherCustomer));
        when(orderRepository.findByOrderCode("BMK-20260909-ABCDE")).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() -> orderService.getOrderStatusHistory(99L, "BMK-20260909-ABCDE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("Lấy danh sách lịch sử chuyển trạng thái thành công")
    void getOrderStatusHistory_success() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByOrderCode("BMK-20260909-ABCDE")).thenReturn(Optional.of(testOrder));

        OrderStatusHistory h1 = new OrderStatusHistory();
        h1.setId(1L);
        h1.setFromStatus(OrderStatus.PENDING);
        h1.setToStatus(OrderStatus.CONFIRMED);
        h1.setChangedBy(staff);
        h1.setNote("Đã xác nhận");
        h1.setCreatedAt(LocalDateTime.now().minusMinutes(10));

        when(orderStatusHistoryRepository.findByOrderOrderCodeOrderByCreatedAtAsc("BMK-20260909-ABCDE"))
                .thenReturn(List.of(h1));

        List<OrderStatusHistoryResponse> result = orderService.getOrderStatusHistory(2L, "BMK-20260909-ABCDE");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.get(0).getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.get(0).getChangedByName()).isEqualTo("Nhân Viên Quán");
        assertThat(result.get(0).getChangedByRole()).isEqualTo(RoleName.STAFF);
    }

    @Test
    @DisplayName("Security: Shipper không được gán không thể cập nhật trạng thái đơn của shipper khác")
    void updateOrderStatus_shipperNotAssigned_forbidden() {
        testOrder.setStatus(OrderStatus.DELIVERING);
        testOrder.setShipper(otherShipper()); // đơn thuộc shipper khác

        when(userRepository.findById(3L)).thenReturn(Optional.of(shipper));
        when(orderRepository.findByOrderCode("BMK-20260909-ABCDE")).thenReturn(Optional.of(testOrder));

        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                .newStatus(OrderStatus.FAILED)
                .note("Khách không nghe máy")
                .build();

        assertThatThrownBy(() -> orderService.updateOrderStatus(3L, "BMK-20260909-ABCDE", request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("Security: Shipper không thể tự gán mình vào đơn chưa phân công khi chuyển DELIVERING")
    void updateOrderStatus_shipperSelfAssign_blocked() {
        testOrder.setStatus(OrderStatus.READY_FOR_PICKUP); // đơn chưa có shipper

        when(userRepository.findById(3L)).thenReturn(Optional.of(shipper));
        when(orderRepository.findByOrderCode("BMK-20260909-ABCDE")).thenReturn(Optional.of(testOrder));

        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                .newStatus(OrderStatus.DELIVERING)
                .build();

        assertThatThrownBy(() -> orderService.updateOrderStatus(3L, "BMK-20260909-ABCDE", request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("Security: Shipper không thể phân công đơn cho shipper khác qua shipperId")
    void updateOrderStatus_shipperAssignsOtherShipper_forbidden() {
        testOrder.setStatus(OrderStatus.READY_FOR_PICKUP);
        testOrder.setShipper(shipper); // shipper 3 đang giữ đơn

        when(userRepository.findById(3L)).thenReturn(Optional.of(shipper));
        when(orderRepository.findByOrderCode("BMK-20260909-ABCDE")).thenReturn(Optional.of(testOrder));

        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                .newStatus(OrderStatus.DELIVERING)
                .shipperId(7L) // thử chuyển cho đồng nghiệp
                .build();

        assertThatThrownBy(() -> orderService.updateOrderStatus(3L, "BMK-20260909-ABCDE", request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("READY_FOR_PICKUP -> DELIVERING bởi Staff có truyền shipperId: Gán shipper và thành công")
    void updateOrderStatus_readyForPickupToDelivering_withShipperId_success() {
        testOrder.setStatus(OrderStatus.READY_FOR_PICKUP);
        shipper.setId(3L);

        when(userRepository.findById(4L)).thenReturn(Optional.of(staff));
        when(userRepository.findById(3L)).thenReturn(Optional.of(shipper));
        when(orderRepository.findByOrderCode("BMK-20260909-ABCDE")).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                .newStatus(OrderStatus.DELIVERING)
                .shipperId(3L)
                .build();

        OrderResponse response = orderService.updateOrderStatus(4L, "BMK-20260909-ABCDE", request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.DELIVERING);
        assertThat(testOrder.getShipper().getId()).isEqualTo(3L);
    }

    /** Shipper khác (id 7) — fixture cho test ownership. */
    private User otherShipper() {
        User s = new User();
        s.setId(7L);
        s.setRole(RoleName.SHIPPER);
        return s;
    }
}
