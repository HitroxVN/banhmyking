import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
import type { CreateOrderRequest, OrderResponse, OrderStatusHistoryItem } from '../types/order';
import { axiosClient } from './axiosClient';

export const orderApi = {
  /**
   * Tạo đơn hàng mới từ giỏ hàng hiện tại của người dùng
   */
  async createOrder(data: CreateOrderRequest): Promise<OrderResponse> {
    const response = await axiosClient.post<ApiResponse<OrderResponse>>('/orders', data);
    return response.data.data;
  },

  /**
   * Xem chi tiết đơn hàng theo mã đơn hàng (orderCode)
   */
  async getOrderByCode(orderCode: string): Promise<OrderResponse> {
    const response = await axiosClient.get<ApiResponse<OrderResponse>>(`/orders/${orderCode}`);
    return response.data.data;
  },

  /**
   * Lấy lịch sử đơn hàng của người dùng hiện tại có phân trang
   */
  async getUserOrders(page = 0, size = 10): Promise<PageResponse<OrderResponse>> {
    const response = await axiosClient.get<ApiResponse<PageResponse<OrderResponse>>>('/orders', {
      params: { page, size },
    });
    return response.data.data;
  },

  /**
   * Lịch sử chuyển trạng thái của một đơn (ai đổi, từ gì sang gì, lúc nào)
   */
  async getOrderHistory(orderCode: string): Promise<OrderStatusHistoryItem[]> {
    const response = await axiosClient.get<ApiResponse<OrderStatusHistoryItem[]>>(
      `/orders/${orderCode}/history`
    );
    return response.data.data ?? [];
  },

  /**
   * Khách tự huỷ đơn — backend chỉ cho phép khi đơn đang PENDING/CONFIRMED
   */
  async cancelOrder(orderCode: string, cancelReason?: string): Promise<OrderResponse> {
    const response = await axiosClient.put<ApiResponse<OrderResponse>>(`/orders/${orderCode}/cancel`, {
      cancelReason,
    });
    return response.data.data;
  },
};
