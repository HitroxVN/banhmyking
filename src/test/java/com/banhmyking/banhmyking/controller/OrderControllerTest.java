package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.order.CreateOrderRequest;
import com.banhmyking.banhmyking.dto.order.OrderResponse;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.PaymentMethod;
import com.banhmyking.banhmyking.enums.PaymentStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Principal giả — username là userId (JWT subject), user 1 (customer). */
    private static final org.springframework.security.core.userdetails.UserDetails PRINCIPAL =
            org.springframework.security.core.userdetails.User
                    .withUsername("1")
                    .password("x")
                    .authorities("ROLE_CUSTOMER")
                    .build();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, PRINCIPAL.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/orders - Tạo đơn hàng thành công trả về 201 Created")
    void createOrder_shouldReturnCreated() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.PENDING)
                .receiverName("Nguyễn Văn A")
                .receiverPhone("0901234567")
                .shippingAddress("123 Lê Lợi, Q1, TP.HCM")
                .subtotal(BigDecimal.valueOf(80000))
                .shippingFee(BigDecimal.valueOf(15000))
                .discountAmount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(95000))
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(orderService.createFromCart(eq(1L), any(CreateOrderRequest.class))).thenReturn(response);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressId(1L)
                .paymentMethod(PaymentMethod.COD)
                .note("Giao nhanh giúp mình")
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("BMK-20260908-ABC12"))
                .andExpect(jsonPath("$.data.total").value(95000));
    }

    @Test
    @DisplayName("POST /api/v1/orders - Chặn tạo đơn khi giỏ hàng rỗng (400 Bad Request)")
    void createOrder_whenCartEmpty_shouldReturnBadRequest() throws Exception {
        when(orderService.createFromCart(eq(1L), any(CreateOrderRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "Giỏ hàng đang trống, không thể tạo đơn hàng"));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressId(1L)
                .build();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("Giỏ hàng đang trống, không thể tạo đơn hàng"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderCode} - Xem chi tiết đơn hàng thành công")
    void getOrderByCode_shouldReturnOrder() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.PENDING)
                .total(BigDecimal.valueOf(95000))
                .build();

        when(orderService.getOrderByCode(1L, "BMK-20260908-ABC12")).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/BMK-20260908-ABC12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("BMK-20260908-ABC12"));
    }

    @Test
    @DisplayName("GET /api/v1/orders - Xem danh sách đơn hàng của user (phân trang)")
    void getUserOrders_shouldReturnOrderList() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .total(BigDecimal.valueOf(95000))
                .build();

        com.banhmyking.banhmyking.dto.common.PageResponse<OrderResponse> pageResponse =
                new com.banhmyking.banhmyking.dto.common.PageResponse<>(
                        List.of(response), 0, 10, 1L, 1, true
                );

        when(orderService.getUserOrders(1L, 0, 10)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/orders")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].orderCode").value("BMK-20260908-ABC12"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/orders/{orderCode}/cancel - Hủy đơn hàng thành công")
    void cancelOrder_shouldReturnCancelled() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.CANCELLED)
                .cancelReason("Đổi ý không ăn nữa")
                .build();

        when(orderService.cancelOrder(eq(1L), eq("BMK-20260908-ABC12"), any())).thenReturn(response);

        com.banhmyking.banhmyking.dto.order.CancelOrderRequest request =
                com.banhmyking.banhmyking.dto.order.CancelOrderRequest.builder()
                        .cancelReason("Đổi ý không ăn nữa")
                        .build();

        mockMvc.perform(put("/api/v1/orders/BMK-20260908-ABC12/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Hủy đơn hàng thành công"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("Đổi ý không ăn nữa"));
    }

    @Test
    @DisplayName("PUT /api/v1/orders/{orderCode}/status - Cập nhật trạng thái đơn thành công")
    void updateOrderStatus_shouldReturnUpdated() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.CONFIRMED)
                .build();

        when(orderService.updateOrderStatus(eq(1L), eq("BMK-20260908-ABC12"), any())).thenReturn(response);

        com.banhmyking.banhmyking.dto.order.UpdateOrderStatusRequest request =
                com.banhmyking.banhmyking.dto.order.UpdateOrderStatusRequest.builder()
                        .newStatus(OrderStatus.CONFIRMED)
                        .note("Bếp xác nhận")
                        .build();

        mockMvc.perform(put("/api/v1/orders/BMK-20260908-ABC12/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái đơn hàng thành công"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderCode}/history - Xem lịch sử trạng thái đơn hàng")
    void getOrderStatusHistory_shouldReturnList() throws Exception {
        com.banhmyking.banhmyking.dto.order.OrderStatusHistoryResponse history =
                com.banhmyking.banhmyking.dto.order.OrderStatusHistoryResponse.builder()
                        .id(1L)
                        .orderCode("BMK-20260908-ABC12")
                        .fromStatus(OrderStatus.PENDING)
                        .toStatus(OrderStatus.CONFIRMED)
                        .changedByName("Admin User")
                        .build();

        when(orderService.getOrderStatusHistory(1L, "BMK-20260908-ABC12")).thenReturn(List.of(history));

        mockMvc.perform(get("/api/v1/orders/BMK-20260908-ABC12/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].fromStatus").value("PENDING"))
                .andExpect(jsonPath("$.data[0].toStatus").value("CONFIRMED"));
    }
}
