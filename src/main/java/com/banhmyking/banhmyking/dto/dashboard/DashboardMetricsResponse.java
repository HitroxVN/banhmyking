package com.banhmyking.banhmyking.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Số liệu thống kê tổng quan hệ thống dành cho Admin")
public class DashboardMetricsResponse {

    @Schema(description = "Tổng doanh thu tích lũy (VND)", example = "15500000")
    private BigDecimal totalRevenue;

    @Schema(description = "Doanh thu trong ngày hôm nay (VND)", example = "1250000")
    private BigDecimal todayRevenue;

    @Schema(description = "Tổng số đơn hàng toàn hệ thống", example = "142")
    private long totalOrders;

    @Schema(description = "Số đơn hàng phát sinh hôm nay", example = "18")
    private long todayOrders;

    @Schema(description = "Số đơn hàng đang chờ xác nhận (PENDING)", example = "4")
    private long pendingOrders;

    @Schema(description = "Số đơn hàng đang chuẩn bị / đang giao (CONFIRMED, PREPARING, READY_FOR_PICKUP, DELIVERING)", example = "9")
    private long processingOrders;

    @Schema(description = "Số đơn hàng giao thành công (DELIVERED)", example = "120")
    private long deliveredOrders;

    @Schema(description = "Số đơn hàng đã hủy / thất bại (CANCELLED, FAILED)", example = "9")
    private long cancelledOrders;

    @Schema(description = "Tỷ lệ đơn hàng thành công (%)", example = "93.0")
    private double successRate;

    @Schema(description = "Tổng số tài khoản người dùng", example = "68")
    private long totalUsers;

    @Schema(description = "Số tài khoản khách hàng (CUSTOMER)", example = "55")
    private long customerCount;

    @Schema(description = "Số tài khoản nhân viên (STAFF)", example = "6")
    private long staffCount;

    @Schema(description = "Số tài khoản tài xế giao hàng (SHIPPER)", example = "5")
    private long shipperCount;
}
