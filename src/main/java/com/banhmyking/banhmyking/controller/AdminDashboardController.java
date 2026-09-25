package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.dashboard.DailyRevenueResponse;
import com.banhmyking.banhmyking.dto.dashboard.DashboardMetricsResponse;
import com.banhmyking.banhmyking.dto.dashboard.OrderStatusStatResponse;
import com.banhmyking.banhmyking.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard", description = "APIs thống kê số liệu và biểu đồ thời gian thực dành cho Admin")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy các chỉ số KPI tổng quan hệ thống",
            description = "Bao gồm tổng doanh thu, doanh thu hôm nay, tổng đơn hàng, tỷ lệ thành công, số lượng người dùng theo vai trò.")
    public ResponseEntity<ApiResponse<DashboardMetricsResponse>> getDashboardMetrics() {
        DashboardMetricsResponse response = adminDashboardService.getDashboardMetrics();
        return ResponseEntity.ok(ApiResponse.ok("Lấy chỉ số thống kê tổng quan thành công", response));
    }

    @GetMapping("/revenue-chart")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy dữ liệu biểu đồ doanh thu theo ngày",
            description = "Thống kê doanh thu và số lượng đơn hàng theo chuỗi ngày (mặc định 7 ngày gần nhất).")
    public ResponseEntity<ApiResponse<List<DailyRevenueResponse>>> getDailyRevenueChart(
            @Parameter(description = "Số ngày thống kê (1-90)", example = "7")
            @RequestParam(defaultValue = "7") int days) {
        List<DailyRevenueResponse> response = adminDashboardService.getDailyRevenueChart(days);
        return ResponseEntity.ok(ApiResponse.ok("Lấy dữ liệu biểu đồ doanh thu thành công", response));
    }

    @GetMapping("/order-status-stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lấy dữ liệu phân bổ trạng thái đơn hàng",
            description = "Phân bổ số lượng và tỷ lệ % đơn hàng theo từng trạng thái (Pending, Confirmed, Delivered...).")
    public ResponseEntity<ApiResponse<List<OrderStatusStatResponse>>> getOrderStatusStats() {
        List<OrderStatusStatResponse> response = adminDashboardService.getOrderStatusStats();
        return ResponseEntity.ok(ApiResponse.ok("Lấy dữ liệu phân bổ trạng thái đơn hàng thành công", response));
    }
}
