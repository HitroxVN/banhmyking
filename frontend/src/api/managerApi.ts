import { axiosClient } from './axiosClient';
import type { ApiResponse, UserInfoResponse } from '../types/auth';
import type { DailyRevenue, DashboardMetrics, ReportFilterParams, TopProduct } from '../types/admin';

/** API riêng của Quản lý cơ sở — server tự khoá về cơ sở của người gọi */
export const managerApi = {
  async staff(): Promise<UserInfoResponse[]> {
    const res = await axiosClient.get<ApiResponse<UserInfoResponse[]>>('/manager/staff');
    return res.data.data;
  },
  async metrics(): Promise<DashboardMetrics> {
    const res = await axiosClient.get<ApiResponse<DashboardMetrics>>('/manager/dashboard/metrics');
    return res.data.data;
  },
  async revenueChart(days = 7): Promise<DailyRevenue[]> {
    const res = await axiosClient.get<ApiResponse<DailyRevenue[]>>('/manager/dashboard/revenue-chart', {
      params: { days },
    });
    return res.data.data;
  },
  async topProducts(params: ReportFilterParams = {}): Promise<TopProduct[]> {
    const res = await axiosClient.get<ApiResponse<TopProduct[]>>('/manager/reports/top-products', { params });
    return res.data.data;
  },
};
