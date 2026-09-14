package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.order.ConfirmDeliveryRequest;
import com.banhmyking.banhmyking.dto.order.FailDeliveryRequest;
import com.banhmyking.banhmyking.dto.order.OrderResponse;
import com.banhmyking.banhmyking.dto.order.OrderStatusHistoryResponse;
import com.banhmyking.banhmyking.dto.order.RejectOrderRequest;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.PaymentMethod;
import com.banhmyking.banhmyking.enums.PaymentStatus;
import com.banhmyking.banhmyking.enums.RoleName;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShipperOrderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private ShipperOrderController shipperOrderController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Principal giả — username là userId (JWT subject), user 4 (shipper). */
    private static final org.springframework.security.core.userdetails.UserDetails PRINCIPAL =
            org.springframework.security.core.userdetails.User
                    .withUsername("4")
                    .password("x")
                    .authorities("ROLE_SHIPPER")
                    .build();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(shipperOrderController)
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
    @DisplayName("GET /api/v1/shipper/orders - Lấy danh sách đơn hàng được gán cho Shipper thành công")
    void getAssignedOrders_shouldReturnPageResponse() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.DELIVERING)
                .shipperId(4L)
                .shipperName("Tài Xế Giao Hàng")
                .total(BigDecimal.valueOf(95000))
                .build();

        PageResponse<OrderResponse> pageResponse = new PageResponse<>(
                List.of(response), 0, 10, 1L, 1, true
        );

        when(orderService.getOrdersForShipper(eq(4L), eq(OrderStatus.DELIVERING), eq(0), eq(10)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/shipper/orders")
                        .param("status", "DELIVERING")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].orderCode").value("BMK-20260908-ABC12"))
                .andExpect(jsonPath("$.data.content[0].shipperId").value(4))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/shipper/orders/{orderCode} - Xem chi tiết đơn hàng được gán thành công")
    void getOrderByCode_shouldReturnOrder() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.DELIVERING)
                .shipperId(4L)
                .total(BigDecimal.valueOf(95000))
                .build();

        when(orderService.getOrderByCode(4L, "BMK-20260908-ABC12")).thenReturn(response);

        mockMvc.perform(get("/api/v1/shipper/orders/BMK-20260908-ABC12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("BMK-20260908-ABC12"));
    }

    @Test
    @DisplayName("PUT /api/v1/shipper/orders/{orderCode}/deliver - Xác nhận giao hàng thành công (DELIVERING -> DELIVERED)")
    void confirmDelivery_shouldReturnDelivered() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.DELIVERED)
                .deliveredAt(LocalDateTime.now())
                .paymentStatus(PaymentStatus.PAID)
                .build();

        when(orderService.confirmDelivery(eq(4L), eq("BMK-20260908-ABC12"), any())).thenReturn(response);

        ConfirmDeliveryRequest request = ConfirmDeliveryRequest.builder()
                .note("Đã giao tận tay khách hàng")
                .build();

        mockMvc.perform(put("/api/v1/shipper/orders/BMK-20260908-ABC12/deliver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));
    }

    @Test
    @DisplayName("GET /api/v1/shipper/orders/{orderCode}/history - Xem lịch sử đơn hàng của Shipper thành công")
    void getOrderStatusHistory_shouldReturnList() throws Exception {
        OrderStatusHistoryResponse history = OrderStatusHistoryResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .fromStatus(OrderStatus.DELIVERING)
                .toStatus(OrderStatus.DELIVERED)
                .changedByName("Tài Xế Giao Hàng")
                .changedByRole(RoleName.SHIPPER)
                .note("Shipper xác nhận giao hàng thành công")
                .createdAt(LocalDateTime.now())
                .build();

        when(orderService.getOrderStatusHistory(4L, "BMK-20260908-ABC12")).thenReturn(List.of(history));

        mockMvc.perform(get("/api/v1/shipper/orders/BMK-20260908-ABC12/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].toStatus").value("DELIVERED"));
    }

    @Test
    @DisplayName("PUT /api/v1/shipper/orders/{orderCode}/fail - Báo cáo giao hàng thất bại thành công (DELIVERING -> FAILED)")
    void failDelivery_shouldReturnFailed() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.FAILED)
                .cancelReason("Khách hàng không nhấc máy sau 3 lần gọi")
                .build();

        when(orderService.updateOrderStatus(eq(4L), eq("BMK-20260908-ABC12"), any())).thenReturn(response);

        FailDeliveryRequest request = FailDeliveryRequest.builder()
                .reason("Khách hàng không nhấc máy sau 3 lần gọi")
                .build();

        mockMvc.perform(put("/api/v1/shipper/orders/BMK-20260908-ABC12/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("FAILED"));
    }

    @Test
    @DisplayName("PUT /api/v1/shipper/orders/{orderCode}/reject - Shipper từ chối nhận đơn hàng thành công")
    void rejectOrder_shouldReturnSuccess() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.READY_FOR_PICKUP)
                .shipperId(null)
                .shipperName(null)
                .build();

        when(orderService.rejectAssignedOrder(eq(4L), eq("BMK-20260908-ABC12"), any())).thenReturn(response);

        RejectOrderRequest request = RejectOrderRequest.builder()
                .reason("Xe gặp sự cố hỏng hóc trên đường")
                .build();

        mockMvc.perform(put("/api/v1/shipper/orders/BMK-20260908-ABC12/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Từ chối nhận đơn hàng thành công"))
                .andExpect(jsonPath("$.data.status").value("READY_FOR_PICKUP"))
                .andExpect(jsonPath("$.data.shipperId").doesNotExist());
    }
}
