import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { DashboardMetrics, DailyRevenue, OrderStatusStat } from '../types/admin';

export const adminDashboardApi = {
  /**
   * Lấy tổng quan các chỉ số kinh doanh & người dùng
   */
  async getMetrics(): Promise<DashboardMetrics> {
    const res = await axiosClient.get<ApiResponse<DashboardMetrics>>('/admin/dashboard/metrics');
    return res.data.data;
  },

  /**
   * Lấy dữ liệu biểu đồ doanh thu theo chu kỳ (7 ngày hoặc 30 ngày)
   */
  async getRevenueChart(days: number = 7): Promise<DailyRevenue[]> {
    const res = await axiosClient.get<ApiResponse<DailyRevenue[]>>('/admin/dashboard/revenue-chart', {
      params: { days },
    });
    return res.data.data;
  },

  /**
   * Lấy tỷ lệ phân bố trạng thái đơn hàng
   */
  async getOrderStatusStats(): Promise<OrderStatusStat[]> {
    const res = await axiosClient.get<ApiResponse<OrderStatusStat[]>>('/admin/dashboard/order-status-stats');
    return res.data.data;
  },
};
