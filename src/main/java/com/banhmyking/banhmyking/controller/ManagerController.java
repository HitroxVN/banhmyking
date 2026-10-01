package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.dashboard.DailyRevenueResponse;
import com.banhmyking.banhmyking.dto.dashboard.DashboardMetricsResponse;
import com.banhmyking.banhmyking.dto.report.TopProductResponse;
import com.banhmyking.banhmyking.dto.store.AcceptingOrdersRequest;
import com.banhmyking.banhmyking.dto.store.StoreResponse;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.exception.NotFoundMessages;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.security.StoreAccessGuard;
import com.banhmyking.banhmyking.service.AdminDashboardService;
import com.banhmyking.banhmyking.service.AdminReportService;
import com.banhmyking.banhmyking.service.StoreService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
@Tag(name = "Manager", description = "Quản lý cơ sở (MANAGER cơ sở mình, ADMIN mọi cơ sở)")
public class ManagerController {

    private final StoreService storeService;
    private final com.banhmyking.banhmyking.service.UserService userService;
    private final AdminDashboardService dashboardService;
    private final AdminReportService reportService;
    private final UserRepository userRepository;
    private final StoreAccessGuard storeAccessGuard;

    @GetMapping("/dashboard/metrics")
    @Operation(summary = "Chỉ số tổng quan của cơ sở mình")
    public ResponseEntity<ApiResponse<DashboardMetricsResponse>> metrics(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy chỉ số cơ sở thành công",
                dashboardService.getDashboardMetrics(ownStore(principal))));
    }

    @GetMapping("/dashboard/revenue-chart")
    @Operation(summary = "Biểu đồ doanh thu theo ngày của cơ sở mình")
    public ResponseEntity<ApiResponse<List<DailyRevenueResponse>>> revenueChart(
            @RequestParam(defaultValue = "7") int days, @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy biểu đồ doanh thu thành công",
                dashboardService.getDailyRevenueChart(days, ownStore(principal))));
    }

    @GetMapping("/reports/top-products")
    @Operation(summary = "Món bán chạy của cơ sở mình")
    public ResponseEntity<ApiResponse<List<TopProductResponse>>> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "10") int limit, @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy món bán chạy thành công",
                reportService.getTopProducts(fromDate, toDate, limit, ownStore(principal))));
    }

    /** MANAGER → cơ sở của mình; ADMIN gọi API này nhận báo cáo toàn chuỗi (null). */
    private Long ownStore(UserDetails principal) {
        Long userId = SecurityUtils.requireUserId(principal);
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(NotFoundMessages.userById(userId)));
        return storeAccessGuard.scopedStoreId(actor);
    }

    @PatchMapping("/stores/{storeId}/accepting")
    @Operation(summary = "Tạm ngưng / mở lại nhận đơn")
    public ResponseEntity<ApiResponse<StoreResponse>> setAccepting(
            @PathVariable Long storeId, @Valid @RequestBody AcceptingOrdersRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        StoreResponse store = storeService.setAcceptingOrders(
                SecurityUtils.requireUserId(principal), storeId, request.accepting());
        return ResponseEntity.ok(ApiResponse.ok(
                store.isAcceptingOrders() ? "Đã mở lại nhận đơn" : "Đã tạm ngưng nhận đơn", store));
    }

    @GetMapping("/staff")
    @Operation(summary = "Nhân sự của cơ sở mình (chỉ xem)")
    public ResponseEntity<ApiResponse<java.util.List<com.banhmyking.banhmyking.dto.user.UserDetailResponse>>> staff(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy nhân sự cơ sở thành công",
                userService.getStoreStaff(SecurityUtils.requireUserId(principal))));
    }
}
