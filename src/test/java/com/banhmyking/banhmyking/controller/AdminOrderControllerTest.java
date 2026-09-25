package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.order.AssignShipperRequest;
import com.banhmyking.banhmyking.dto.order.CancelOrderRequest;
import com.banhmyking.banhmyking.dto.order.OrderResponse;
import com.banhmyking.banhmyking.dto.order.OrderStatusHistoryResponse;
import com.banhmyking.banhmyking.dto.order.UpdateOrderStatusRequest;
import com.banhmyking.banhmyking.enums.OrderStatus;
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
class AdminOrderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private AdminOrderController adminOrderController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Principal giả — username là userId (JWT subject), user 2 (staff). */
    private static final org.springframework.security.core.userdetails.UserDetails PRINCIPAL =
            org.springframework.security.core.userdetails.User
                    .withUsername("2")
                    .password("x")
                    .authorities("ROLE_STAFF")
                    .build();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminOrderController)
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
    @DisplayName("GET /api/v1/admin/orders - Lấy danh sách toàn bộ đơn hàng có filter và phân trang thành công")
    void getAllOrders_shouldReturnPageResponse() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.PENDING)
                .total(BigDecimal.valueOf(95000))
                .build();

        PageResponse<OrderResponse> pageResponse = new PageResponse<>(
                List.of(response), 0, 10, 1L, 1, true
        );

        when(orderService.getAllOrdersForAdmin(eq(2L), eq(OrderStatus.PENDING), eq("2026-09-01"), eq("2026-09-30"), eq(0), eq(10)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("status", "PENDING")
                        .param("fromDate", "2026-09-01")
                        .param("toDate", "2026-09-30")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].orderCode").value("BMK-20260908-ABC12"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/admin/orders/{orderCode} - Xem chi tiết đơn hàng thành công")
    void getOrderByCode_shouldReturnOrder() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.PENDING)
                .total(BigDecimal.valueOf(95000))
                .build();

        when(orderService.getOrderByCode(2L, "BMK-20260908-ABC12")).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/orders/BMK-20260908-ABC12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("BMK-20260908-ABC12"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/orders/{orderCode}/status - Staff/Admin cập nhật trạng thái đơn thành công")
    void updateOrderStatus_shouldReturnUpdated() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.CONFIRMED)
                .build();

        when(orderService.updateOrderStatus(eq(2L), eq("BMK-20260908-ABC12"), any())).thenReturn(response);

        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                .newStatus(OrderStatus.CONFIRMED)
                .note("Bếp xác nhận đơn")
                .build();

        mockMvc.perform(put("/api/v1/admin/orders/BMK-20260908-ABC12/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/orders/{orderCode}/assign-shipper - Gán shipper cho đơn hàng thành công")
    void assignShipper_shouldReturnAssignedOrder() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.READY_FOR_PICKUP)
                .shipperId(4L)
                .shipperName("Tài Xế Giao Hàng")
                .shipperPhone("0906665555")
                .build();

        when(orderService.assignShipper(eq(2L), eq("BMK-20260908-ABC12"), any())).thenReturn(response);

        AssignShipperRequest request = AssignShipperRequest.builder()
                .shipperId(4L)
                .note("Giao nhanh trong giờ trưa")
                .build();

        mockMvc.perform(put("/api/v1/admin/orders/BMK-20260908-ABC12/assign-shipper")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shipperId").value(4))
                .andExpect(jsonPath("$.data.shipperName").value("Tài Xế Giao Hàng"));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/orders/{orderCode}/cancel - Staff/Admin hủy đơn thành công")
    void cancelOrder_shouldReturnCancelled() throws Exception {
        OrderResponse response = OrderResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .status(OrderStatus.CANCELLED)
                .cancelReason("Khách gọi điện báo đổi món")
                .build();

        when(orderService.cancelOrder(eq(2L), eq("BMK-20260908-ABC12"), any())).thenReturn(response);

        CancelOrderRequest request = CancelOrderRequest.builder()
                .cancelReason("Khách gọi điện báo đổi món")
                .build();

        mockMvc.perform(put("/api/v1/admin/orders/BMK-20260908-ABC12/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("Khách gọi điện báo đổi món"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/orders/{orderCode}/history - Lấy lịch sử trạng thái đơn hàng thành công")
    void getOrderStatusHistory_shouldReturnList() throws Exception {
        OrderStatusHistoryResponse history = OrderStatusHistoryResponse.builder()
                .id(1L)
                .orderCode("BMK-20260908-ABC12")
                .fromStatus(OrderStatus.READY_FOR_PICKUP)
                .toStatus(OrderStatus.READY_FOR_PICKUP)
                .changedByName("Nhân Viên Quán")
                .changedByRole(RoleName.STAFF)
                .note("Gán shipper: Tài Xế Giao Hàng")
                .createdAt(LocalDateTime.now())
                .build();

        when(orderService.getOrderStatusHistory(2L, "BMK-20260908-ABC12")).thenReturn(List.of(history));

        mockMvc.perform(get("/api/v1/admin/orders/BMK-20260908-ABC12/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].note").value("Gán shipper: Tài Xế Giao Hàng"));
    }
}
