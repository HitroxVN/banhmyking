package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.order.AssignShipperRequest;
import com.banhmyking.banhmyking.dto.order.CancelOrderRequest;
import com.banhmyking.banhmyking.dto.order.ConfirmDeliveryRequest;
import com.banhmyking.banhmyking.dto.order.OrderResponse;
import com.banhmyking.banhmyking.dto.order.OrderStatusHistoryResponse;
import com.banhmyking.banhmyking.dto.order.UpdateOrderStatusRequest;
import com.banhmyking.banhmyking.dto.payment.PaymentResponse;
import com.banhmyking.banhmyking.entity.Order;
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
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.OrderStatusHistoryRepository;
import com.banhmyking.banhmyking.repository.PaymentRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.impl.OrderServiceImpl;
import com.banhmyking.banhmyking.service.impl.PaymentServiceImpl;
import com.banhmyking.banhmyking.validator.OrderStatusValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderOwnershipTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Spy
    private OrderStatusValidator orderStatusValidator = new OrderStatusValidator();

    @Mock
    private DeliveryFeeCalculator deliveryFeeCalculator;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private PaymentServiceImpl internalPaymentService;

    private User customerA;
    private User customerB;
    private User staff;
    private User admin;
    private User assignedShipper;
    private User otherShipper;

    private Order orderA;
    private Payment paymentA;

    @BeforeEach
    void setUp() {
        internalPaymentService = new PaymentServiceImpl(paymentRepository, orderRepository, userRepository);

        customerA = new User();
        customerA.setId(10L);
        customerA.setFullName("Khách Hàng A");
        customerA.setRole(RoleName.CUSTOMER);

        customerB = new User();
        customerB.setId(20L);
        customerB.setFullName("Khách Hàng B");
        customerB.setRole(RoleName.CUSTOMER);

        staff = new User();
        staff.setId(30L);
        staff.setFullName("Nhân Viên Quán");
        staff.setRole(RoleName.STAFF);

        admin = new User();
        admin.setId(40L);
        admin.setFullName("Quản Trị Viên");
        admin.setRole(RoleName.ADMIN);

        assignedShipper = new User();
        assignedShipper.setId(50L);
        assignedShipper.setFullName("Tài Xế Được Gán");
        assignedShipper.setRole(RoleName.SHIPPER);

        otherShipper = new User();
        otherShipper.setId(60L);
        otherShipper.setFullName("Tài Xế Khác");
        otherShipper.setRole(RoleName.SHIPPER);

        orderA = new Order();
        orderA.setId(100L);
        orderA.setOrderCode("BMK-20260912-ORD_A");
        orderA.setUser(customerA);
        orderA.setStatus(OrderStatus.PENDING);
        orderA.setSubtotal(BigDecimal.valueOf(70000));
        orderA.setShippingFee(BigDecimal.valueOf(15000));
        orderA.setTotal(BigDecimal.valueOf(85000));
        orderA.setCreatedAt(LocalDateTime.now());
        orderA.setShipper(assignedShipper);
        orderA.setItems(new ArrayList<>());

        paymentA = new Payment();
        paymentA.setId(500L);
        paymentA.setOrder(orderA);
        paymentA.setAmount(BigDecimal.valueOf(85000));
        paymentA.setMethod(PaymentMethod.COD);
        paymentA.setStatus(PaymentStatus.PENDING);
        paymentA.setCreatedAt(LocalDateTime.now());
        orderA.setPayment(paymentA);
    }

    @Nested
    @DisplayName("1. Quyền xem chi tiết đơn hàng (getOrderByCode)")
    class GetOrderByCodeOwnershipTests {

        @Test
        @DisplayName("Customer A xem đơn của chính mình -> Thành công")
        void getOrderByCode_asOwner_success() {
            when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-ORD_A")).thenReturn(Optional.of(orderA));
            when(userRepository.findById(10L)).thenReturn(Optional.of(customerA));

            OrderResponse response = orderService.getOrderByCode(10L, "BMK-20260912-ORD_A");

            assertThat(response).isNotNull();
            assertThat(response.getOrderCode()).isEqualTo("BMK-20260912-ORD_A");
        }

        @Test
        @DisplayName("Customer B cố tình xem đơn của Customer A -> Bị chặn ResourceNotFound (chống IDOR enumeration)")
        void getOrderByCode_asOtherCustomer_shouldThrowNotFound() {
            when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-ORD_A")).thenReturn(Optional.of(orderA));
            when(userRepository.findById(20L)).thenReturn(Optional.of(customerB));

            assertThatThrownBy(() -> orderService.getOrderByCode(20L, "BMK-20260912-ORD_A"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy đơn hàng");
        }

        @Test
        @DisplayName("Staff và Admin có quyền xem đơn hàng của bất kỳ khách hàng nào")
        void getOrderByCode_asStaffOrAdmin_success() {
            when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-ORD_A")).thenReturn(Optional.of(orderA));
            when(userRepository.findById(30L)).thenReturn(Optional.of(staff));
            when(userRepository.findById(40L)).thenReturn(Optional.of(admin));

            assertThatCode(() -> orderService.getOrderByCode(30L, "BMK-20260912-ORD_A")).doesNotThrowAnyException();
            assertThatCode(() -> orderService.getOrderByCode(40L, "BMK-20260912-ORD_A")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Shipper được gán có quyền xem; Shipper khác xem bị chặn 403 Forbidden")
        void getOrderByCode_shipperPermissions() {
            when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-ORD_A")).thenReturn(Optional.of(orderA));
            when(userRepository.findById(50L)).thenReturn(Optional.of(assignedShipper));
            when(userRepository.findById(60L)).thenReturn(Optional.of(otherShipper));

            // Shipper được gán -> OK
            assertThatCode(() -> orderService.getOrderByCode(50L, "BMK-20260912-ORD_A")).doesNotThrowAnyException();

            // Shipper khác -> FORBIDDEN
            assertThatThrownBy(() -> orderService.getOrderByCode(60L, "BMK-20260912-ORD_A"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN))
                    .hasMessageContaining("Bạn không được phân công giao đơn hàng này");
        }
    }

    @Nested
    @DisplayName("2. Quyền xem lịch sử trạng thái (getOrderStatusHistory)")
    class OrderStatusHistoryOwnershipTests {

        @Test
        @DisplayName("Customer A xem lịch sử đơn của mình -> Thành công")
        void getOrderStatusHistory_asOwner_success() {
            when(orderRepository.findByOrderCode("BMK-20260912-ORD_A")).thenReturn(Optional.of(orderA));
            when(userRepository.findById(10L)).thenReturn(Optional.of(customerA));
            when(orderStatusHistoryRepository.findByOrderOrderCodeOrderByCreatedAtAsc("BMK-20260912-ORD_A")).thenReturn(List.of());

            List<OrderStatusHistoryResponse> history = orderService.getOrderStatusHistory(10L, "BMK-20260912-ORD_A");
            assertThat(history).isNotNull();
        }

        @Test
        @DisplayName("Customer B xem lịch sử đơn của Customer A -> Bị chặn 403 Forbidden")
        void getOrderStatusHistory_asOtherCustomer_shouldThrowForbidden() {
            when(orderRepository.findByOrderCode("BMK-20260912-ORD_A")).thenReturn(Optional.of(orderA));
            when(userRepository.findById(20L)).thenReturn(Optional.of(customerB));

            assertThatThrownBy(() -> orderService.getOrderStatusHistory(20L, "BMK-20260912-ORD_A"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN))
                    .hasMessageContaining("Bạn không có quyền xem lịch sử đơn hàng của người khác");
        }
    }

    @Nested
    @DisplayName("3. Quyền Hủy đơn hàng (cancelOrder)")
    class CancelOrderOwnershipTests {

        @Test
        @DisplayName("Customer A hủy đơn PENDING của chính mình -> Thành công")
        void cancelOrder_asOwner_success() {
            when(orderRepository.findByOrderCode("BMK-20260912-ORD_A")).thenReturn(Optional.of(orderA));
            when(userRepository.findById(10L)).thenReturn(Optional.of(customerA));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            CancelOrderRequest request = CancelOrderRequest.builder().cancelReason("Đổi ý").build();
            OrderResponse response = orderService.cancelOrder(10L, "BMK-20260912-ORD_A", request);

            assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("Customer B cố tình hủy đơn của Customer A -> Bị chặn 403 Forbidden")
        void cancelOrder_asOtherCustomer_shouldThrowForbidden() {
            when(orderRepository.findByOrderCode("BMK-20260912-ORD_A")).thenReturn(Optional.of(orderA));
            when(userRepository.findById(20L)).thenReturn(Optional.of(customerB));

            CancelOrderRequest request = CancelOrderRequest.builder().cancelReason("Phá đơn người khác").build();

            assertThatThrownBy(() -> orderService.cancelOrder(20L, "BMK-20260912-ORD_A", request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN))
                    .hasMessageContaining("Bạn không có quyền hủy đơn hàng của người khác");
        }
    }

    @Nested
    @DisplayName("4. Quyền Cập nhật trạng thái và Phân công (updateOrderStatus & assignShipper)")
    class ManagementOwnershipTests {

        @Test
        @DisplayName("Customer cố cập nhật trạng thái đơn hàng -> Bị chặn 403 Forbidden")
        void updateOrderStatus_asCustomer_shouldThrowForbidden() {
            when(orderRepository.findByOrderCode("BMK-20260912-ORD_A")).thenReturn(Optional.of(orderA));
            when(userRepository.findById(10L)).thenReturn(Optional.of(customerA));

            UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                    .newStatus(OrderStatus.CONFIRMED)
                    .build();

            assertThatThrownBy(() -> orderService.updateOrderStatus(10L, "BMK-20260912-ORD_A", request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }

        @Test
        @DisplayName("Customer cố gán shipper cho đơn hàng -> Bị chặn 403 Forbidden")
        void assignShipper_asCustomer_shouldThrowForbidden() {
            when(userRepository.findById(10L)).thenReturn(Optional.of(customerA));

            AssignShipperRequest request = AssignShipperRequest.builder()
                    .shipperId(50L)
                    .build();

            assertThatThrownBy(() -> orderService.assignShipper(10L, "BMK-20260912-ORD_A", request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }

        @Test
        @DisplayName("Shipper không được gán gọi confirmDelivery -> Bị chặn 403 Forbidden")
        void confirmDelivery_asUnassignedShipper_shouldThrowForbidden() {
            orderA.setStatus(OrderStatus.DELIVERING);
            when(orderRepository.findByOrderCode("BMK-20260912-ORD_A")).thenReturn(Optional.of(orderA));
            when(userRepository.findById(60L)).thenReturn(Optional.of(otherShipper));

            ConfirmDeliveryRequest request = ConfirmDeliveryRequest.builder().build();

            assertThatThrownBy(() -> orderService.confirmDelivery(60L, "BMK-20260912-ORD_A", request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN))
                    .hasMessageContaining("Bạn không phải là shipper được phân công giao đơn hàng này");
        }
    }

    @Nested
    @DisplayName("5. Quyền tra cứu thanh toán (PaymentService Ownership)")
    class PaymentOwnershipTests {

        @Test
        @DisplayName("Customer A xem thanh toán đơn hàng của chính mình -> Thành công")
        void getPaymentByOrderCode_asOwner_success() {
            when(orderRepository.findByOrderCode("BMK-20260912-ORD_A")).thenReturn(Optional.of(orderA));
            when(userRepository.findById(10L)).thenReturn(Optional.of(customerA));
            when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(paymentA));

            PaymentResponse response = internalPaymentService.getPaymentByOrderCode(10L, "BMK-20260912-ORD_A");

            assertThat(response).isNotNull();
            assertThat(response.getOrderCode()).isEqualTo("BMK-20260912-ORD_A");
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("Customer B xem thanh toán đơn hàng của Customer A -> Bị chặn ResourceNotFound")
        void getPaymentByOrderCode_asOtherCustomer_shouldThrowNotFound() {
            when(orderRepository.findByOrderCode("BMK-20260912-ORD_A")).thenReturn(Optional.of(orderA));
            when(userRepository.findById(20L)).thenReturn(Optional.of(customerB));

            assertThatThrownBy(() -> internalPaymentService.getPaymentByOrderCode(20L, "BMK-20260912-ORD_A"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy đơn hàng");
        }

        @Test
        @DisplayName("Customer B xem thanh toán qua paymentId của Customer A -> Bị chặn ResourceNotFound")
        void getPaymentById_asOtherCustomer_shouldThrowNotFound() {
            when(paymentRepository.findById(500L)).thenReturn(Optional.of(paymentA));
            when(userRepository.findById(20L)).thenReturn(Optional.of(customerB));

            assertThatThrownBy(() -> internalPaymentService.getPaymentById(20L, 500L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy thông tin thanh toán");
        }

        @Test
        @DisplayName("Shipper không được gán xem thanh toán qua paymentId -> Bị chặn 403 Forbidden")
        void getPaymentById_asUnassignedShipper_shouldThrowForbidden() {
            when(paymentRepository.findById(500L)).thenReturn(Optional.of(paymentA));
            when(userRepository.findById(60L)).thenReturn(Optional.of(otherShipper));

            assertThatThrownBy(() -> internalPaymentService.getPaymentById(60L, 500L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN))
                    .hasMessageContaining("Bạn không được phân công giao đơn hàng này");
        }

        @Test
        @DisplayName("Shipper được gán xem thanh toán qua paymentId -> Thành công")
        void getPaymentById_asAssignedShipper_success() {
            when(paymentRepository.findById(500L)).thenReturn(Optional.of(paymentA));
            when(userRepository.findById(50L)).thenReturn(Optional.of(assignedShipper));

            PaymentResponse response = internalPaymentService.getPaymentById(50L, 500L);

            assertThat(response).isNotNull();
            assertThat(response.getOrderCode()).isEqualTo("BMK-20260912-ORD_A");
        }
    }
}
