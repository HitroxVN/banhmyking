package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.order.AssignShipperRequest;
import com.banhmyking.banhmyking.dto.order.ConfirmDeliveryRequest;
import com.banhmyking.banhmyking.dto.order.OrderResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderStatusHistory;
import com.banhmyking.banhmyking.entity.Payment;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.PaymentMethod;
import com.banhmyking.banhmyking.enums.PaymentStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.OrderStatusHistoryRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.impl.OrderServiceImpl;
import com.banhmyking.banhmyking.validator.OrderStatusValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceApisTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private OrderStatusValidator orderStatusValidator;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User customer;
    private User otherCustomer;
    private User staff;
    private User admin;
    private User shipper;
    private User otherShipper;
    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setId(1L);
        customer.setFullName("Khách hàng 1");
        customer.setRole(RoleName.CUSTOMER);

        otherCustomer = new User();
        otherCustomer.setId(99L);
        otherCustomer.setFullName("Khách hàng khác");
        otherCustomer.setRole(RoleName.CUSTOMER);

        staff = new User();
        staff.setId(2L);
        staff.setFullName("Nhân viên quán");
        staff.setRole(RoleName.STAFF);

        admin = new User();
        admin.setId(3L);
        admin.setFullName("Quản trị viên");
        admin.setRole(RoleName.ADMIN);

        shipper = new User();
        shipper.setId(4L);
        shipper.setFullName("Tài xế Hoàng");
        shipper.setPhone("0906665555");
        shipper.setRole(RoleName.SHIPPER);

        otherShipper = new User();
        otherShipper.setId(5L);
        otherShipper.setFullName("Tài xế Nam");
        otherShipper.setPhone("0907776666");
        otherShipper.setRole(RoleName.SHIPPER);

        sampleOrder = new Order();
        sampleOrder.setId(10L);
        sampleOrder.setOrderCode("BMK-20260909-TEST1");
        sampleOrder.setUser(customer);
        sampleOrder.setStatus(OrderStatus.READY_FOR_PICKUP);
        sampleOrder.setReceiverName("Khách hàng 1");
        sampleOrder.setReceiverPhone("0901234567");
        sampleOrder.setShippingAddress("123 Lê Lợi, Q1, TP.HCM");
        sampleOrder.setSubtotal(BigDecimal.valueOf(80000));
        sampleOrder.setShippingFee(BigDecimal.valueOf(15000));
        sampleOrder.setDiscountAmount(BigDecimal.ZERO);
        sampleOrder.setTotal(BigDecimal.valueOf(95000));
        sampleOrder.setCreatedAt(LocalDateTime.now());
        sampleOrder.setItems(new ArrayList<>());

        Payment payment = new Payment();
        payment.setId(20L);
        payment.setOrder(sampleOrder);
        payment.setMethod(PaymentMethod.COD);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(BigDecimal.valueOf(95000));
        sampleOrder.setPayment(payment);
    }

    // ─── 1. Customer: Phân trang danh sách đơn ───────────────────────────────

    @Test
    @DisplayName("getUserOrders - Phân trang đơn hàng của khách hàng")
    void getUserOrders_withPagination_shouldReturnPageResponse() {
        Page<Order> page = new PageImpl<>(List.of(sampleOrder));
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<OrderResponse> result = orderService.getUserOrders(1L, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals("BMK-20260909-TEST1", result.content().get(0).getOrderCode());
        assertEquals(1, result.totalElements());
    }

    // ─── 2. Customer: Chi tiết & Check IDOR ──────────────────────────────────

    @Test
    @DisplayName("getOrderByCode - Khách hàng khác xem đơn của người khác bị chặn (IDOR)")
    void getOrderByCode_whenCustomerViewsOtherOrder_shouldThrowException() {
        when(orderRepository.findByOrderCodeWithDetails("BMK-20260909-TEST1")).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(99L)).thenReturn(Optional.of(otherCustomer));

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.getOrderByCode(99L, "BMK-20260909-TEST1"));
    }

    @Test
    @DisplayName("getOrderByCode - Khách hàng xem đúng đơn của mình thành công")
    void getOrderByCode_whenCustomerViewsOwnOrder_shouldSucceed() {
        when(orderRepository.findByOrderCodeWithDetails("BMK-20260909-TEST1")).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        OrderResponse response = orderService.getOrderByCode(1L, "BMK-20260909-TEST1");

        assertNotNull(response);
        assertEquals("BMK-20260909-TEST1", response.getOrderCode());
    }

    // ─── 3. Staff/Admin: Lấy toàn bộ đơn kèm filter ─────────────────────────

    @Test
    @DisplayName("getAllOrdersForAdmin - Khách hàng thường không có quyền gọi (403 Forbidden)")
    void getAllOrdersForAdmin_whenCustomerCalls_shouldThrowForbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThrows(BusinessException.class,
                () -> orderService.getAllOrdersForAdmin(1L, null, null, null, 0, 10));
    }

    @Test
    @DisplayName("getAllOrdersForAdmin - Staff/Admin gọi kèm filter thành công")
    void getAllOrdersForAdmin_whenStaffCalls_shouldReturnFilteredOrders() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        Page<Order> page = new PageImpl<>(List.of(sampleOrder));
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<OrderResponse> result = orderService.getAllOrdersForAdmin(
                2L, OrderStatus.READY_FOR_PICKUP, "2026-09-01", "2026-09-30", 0, 10);

        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals("BMK-20260909-TEST1", result.content().get(0).getOrderCode());
    }

    // ─── 4. Staff/Admin: Gán Shipper ─────────────────────────────────────────

    @Test
    @DisplayName("assignShipper - Staff gán shipper hợp lệ thành công")
    void assignShipper_whenValid_shouldAssignShipperAndLogHistory() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(orderRepository.findByOrderCode("BMK-20260909-TEST1")).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(4L)).thenReturn(Optional.of(shipper));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        AssignShipperRequest request = AssignShipperRequest.builder()
                .shipperId(4L)
                .note("Giao trước 12h")
                .build();

        OrderResponse response = orderService.assignShipper(2L, "BMK-20260909-TEST1", request);

        assertNotNull(response);
        assertEquals(4L, response.getShipperId());
        assertEquals("Tài xế Hoàng", response.getShipperName());
        verify(orderStatusHistoryRepository, times(1)).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("assignShipper - Không phải Staff/Admin thì bị chặn 403 Forbidden")
    void assignShipper_whenCustomerCalls_shouldThrowForbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        AssignShipperRequest request = AssignShipperRequest.builder().shipperId(4L).build();

        assertThrows(BusinessException.class,
                () -> orderService.assignShipper(1L, "BMK-20260909-TEST1", request));
    }

    @Test
    @DisplayName("assignShipper - Người được gán không có vai trò SHIPPER thì bị báo lỗi")
    void assignShipper_whenTargetNotShipper_shouldThrowValidationError() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(orderRepository.findByOrderCode("BMK-20260909-TEST1")).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer)); // Customer, not Shipper

        AssignShipperRequest request = AssignShipperRequest.builder().shipperId(1L).build();

        assertThrows(BusinessException.class,
                () -> orderService.assignShipper(2L, "BMK-20260909-TEST1", request));
    }

    @Test
    @DisplayName("assignShipper - Đơn hàng đã ở trạng thái kết thúc (DELIVERED) thì chặn gán")
    void assignShipper_whenOrderTerminal_shouldThrowBusinessError() {
        sampleOrder.setStatus(OrderStatus.DELIVERED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(orderRepository.findByOrderCode("BMK-20260909-TEST1")).thenReturn(Optional.of(sampleOrder));

        AssignShipperRequest request = AssignShipperRequest.builder().shipperId(4L).build();

        assertThrows(BusinessException.class,
                () -> orderService.assignShipper(2L, "BMK-20260909-TEST1", request));
    }

    @Test
    @DisplayName("assignShipper - Shipper đang có đơn hoạt động thì chặn gán thêm đơn")
    void assignShipper_whenShipperAlreadyHasActiveOrder_shouldThrowBusinessError() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(orderRepository.findByOrderCode("BMK-20260909-TEST1")).thenReturn(Optional.of(sampleOrder));
        when(userRepository.findById(4L)).thenReturn(Optional.of(shipper));
        when(orderRepository.countByShipperIdAndStatusInAndIdNot(eq(4L), any(), any()))
                .thenReturn(1L);

        AssignShipperRequest request = AssignShipperRequest.builder().shipperId(4L).build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.assignShipper(2L, "BMK-20260909-TEST1", request));
        assertTrue(ex.getMessage().contains("hiện đang có đơn hàng chưa hoàn tất"));
    }

    // ─── 5. Shipper: Lấy danh sách đơn được gán ──────────────────────────────

    @Test
    @DisplayName("getOrdersForShipper - Shipper lấy danh sách đơn của mình thành công")
    void getOrdersForShipper_whenShipperCalls_shouldReturnAssignedOrders() {
        sampleOrder.setShipper(shipper);
        when(userRepository.findById(4L)).thenReturn(Optional.of(shipper));
        Page<Order> page = new PageImpl<>(List.of(sampleOrder));
        when(orderRepository.findByShipperIdOrderByCreatedAtDesc(eq(4L), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<OrderResponse> result = orderService.getOrdersForShipper(4L, null, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals("BMK-20260909-TEST1", result.content().get(0).getOrderCode());
    }

    @Test
    @DisplayName("getOrdersForShipper - Customer gọi API shipper bị chặn (403 Forbidden)")
    void getOrdersForShipper_whenCustomerCalls_shouldThrowForbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThrows(BusinessException.class,
                () -> orderService.getOrdersForShipper(1L, null, 0, 10));
    }

    // ─── 6. Shipper: Xác nhận giao hàng ─────────────────────────────────────

    @Test
    @DisplayName("confirmDelivery - Shipper được gán xác nhận giao hàng thành công, tự cập nhật COD PAID")
    void confirmDelivery_whenAssignedShipper_shouldTransitionToDelivered() {
        sampleOrder.setStatus(OrderStatus.DELIVERING);
        sampleOrder.setShipper(shipper);

        when(userRepository.findById(4L)).thenReturn(Optional.of(shipper));
        when(orderRepository.findByOrderCode("BMK-20260909-TEST1")).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        ConfirmDeliveryRequest request = ConfirmDeliveryRequest.builder()
                .note("Khách đã nhận đủ và trả tiền mặt")
                .build();

        OrderResponse response = orderService.confirmDelivery(4L, "BMK-20260909-TEST1", request);

        assertNotNull(response);
        assertEquals(OrderStatus.DELIVERED, response.getStatus());
        assertNotNull(response.getDeliveredAt());
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
        verify(orderStatusHistoryRepository, times(1)).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("confirmDelivery - Shipper khác (không được gán) gọi xác nhận giao bị chặn 403 Forbidden")
    void confirmDelivery_whenOtherShipperCalls_shouldThrowForbidden() {
        sampleOrder.setStatus(OrderStatus.DELIVERING);
        sampleOrder.setShipper(shipper); // Assigned to shipper (ID 4)

        when(userRepository.findById(5L)).thenReturn(Optional.of(otherShipper)); // Caller is shipper 5
        when(orderRepository.findByOrderCode("BMK-20260909-TEST1")).thenReturn(Optional.of(sampleOrder));

        ConfirmDeliveryRequest request = ConfirmDeliveryRequest.builder().build();

        assertThrows(BusinessException.class,
                () -> orderService.confirmDelivery(5L, "BMK-20260909-TEST1", request));
    }
}
