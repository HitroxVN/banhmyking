package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.dashboard.DailyRevenueResponse;
import com.banhmyking.banhmyking.dto.dashboard.DashboardMetricsResponse;
import com.banhmyking.banhmyking.dto.dashboard.OrderStatusStatResponse;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.service.AdminDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminDashboardService adminDashboardService;

    @InjectMocks
    private AdminDashboardController adminDashboardController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminDashboardController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard/metrics - Lấy chỉ số tổng quan thành công")
    void getDashboardMetrics_success() throws Exception {
        DashboardMetricsResponse metrics = DashboardMetricsResponse.builder()
                .totalRevenue(BigDecimal.valueOf(15000000))
                .todayRevenue(BigDecimal.valueOf(1200000))
                .totalOrders(150)
                .todayOrders(15)
                .pendingOrders(3)
                .processingOrders(8)
                .deliveredOrders(130)
                .cancelledOrders(9)
                .successRate(86.7)
                .totalUsers(80)
                .customerCount(70)
                .staffCount(5)
                .shipperCount(4)
                .build();

        when(adminDashboardService.getDashboardMetrics()).thenReturn(metrics);

        mockMvc.perform(get("/api/v1/admin/dashboard/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRevenue").value(15000000))
                .andExpect(jsonPath("$.data.totalOrders").value(150))
                .andExpect(jsonPath("$.data.deliveredOrders").value(130));
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard/revenue-chart - Lấy chuỗi doanh thu theo ngày thành công")
    void getDailyRevenueChart_success() throws Exception {
        List<DailyRevenueResponse> chartData = List.of(
                new DailyRevenueResponse("2026-09-13", BigDecimal.valueOf(1000000), 10),
                new DailyRevenueResponse("2026-09-14", BigDecimal.valueOf(1500000), 15)
        );

        when(adminDashboardService.getDailyRevenueChart(anyInt())).thenReturn(chartData);

        mockMvc.perform(get("/api/v1/admin/dashboard/revenue-chart?days=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].date").value("2026-09-13"))
                .andExpect(jsonPath("$.data[1].orderCount").value(15));
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard/order-status-stats - Lấy phân bổ trạng thái thành công")
    void getOrderStatusStats_success() throws Exception {
        List<OrderStatusStatResponse> stats = List.of(
                new OrderStatusStatResponse(OrderStatus.DELIVERED, "Giao thành công", 130, 86.7, BigDecimal.ZERO),
                new OrderStatusStatResponse(OrderStatus.CANCELLED, "Đã hủy", 9, 6.0, BigDecimal.ZERO)
        );

        when(adminDashboardService.getOrderStatusStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/admin/dashboard/order-status-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].status").value("DELIVERED"));
    }
}
