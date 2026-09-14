import type { ApiResponse } from '../types/auth';
import type { CreateOrderRequest, OrderResponse } from '../types/order';
import { axiosClient } from './axiosClient';

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

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
};
