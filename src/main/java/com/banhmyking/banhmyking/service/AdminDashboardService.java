package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.dashboard.DailyRevenueResponse;
import com.banhmyking.banhmyking.dto.dashboard.DashboardMetricsResponse;
import com.banhmyking.banhmyking.dto.dashboard.OrderStatusStatResponse;

import java.util.List;

public interface AdminDashboardService {

    /**
     * Lấy các chỉ số KPI tổng quan toàn hệ thống (Doanh thu, Đơn hàng, Người dùng).
     */
    DashboardMetricsResponse getDashboardMetrics();

    /**
     * Lấy chuỗi thống kê doanh thu và đơn hàng theo từng ngày (mặc định 7 ngày gần nhất).
     */
    List<DailyRevenueResponse> getDailyRevenueChart(int days);

    /**
     * Lấy thống kê phân bổ đơn hàng theo trạng thái kèm tỷ lệ phần trăm và tổng giá trị.
     */
    List<OrderStatusStatResponse> getOrderStatusStats();
}
