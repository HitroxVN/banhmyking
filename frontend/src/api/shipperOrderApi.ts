import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
import type { OrderResponse, OrderStatus } from '../types/order';

export interface ConfirmDeliveryRequest {
  note?: string;
}

export interface FailDeliveryRequest {
  reason: string;
}

export interface RejectOrderRequest {
  reason: string;
}

export interface OrderStatusHistoryItem {
  id: number;
  orderCode: string;
  fromStatus?: OrderStatus;
  toStatus: OrderStatus;
  changedByName?: string;
  changedByRole?: string;
  note?: string;
  createdAt: string;
}

export const shipperOrderApi = {
  /**
   * Lấy danh sách đơn hàng được gán cho Shipper hiện tại
   */
  async getAssignedOrders(
    status?: OrderStatus,
    page: number = 0,
    size: number = 30
  ): Promise<PageResponse<OrderResponse>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<OrderResponse>>>('/shipper/orders', {
      params: {
        ...(status ? { status } : {}),
        page,
        size,
      },
    });
    return res.data.data;
  },

  /**
   * Xem chi tiết đơn hàng được gán
   */
  async getOrderDetail(orderCode: string): Promise<OrderResponse> {
    const res = await axiosClient.get<ApiResponse<OrderResponse>>(`/shipper/orders/${orderCode}`);
    return res.data.data;
  },

  /**
   * Bắt đầu đi giao (READY_FOR_PICKUP -> DELIVERING)
   */
  async startDelivering(orderCode: string, note?: string): Promise<OrderResponse> {
    const res = await axiosClient.put<ApiResponse<OrderResponse>>(
      `/orders/${orderCode}/status`,
      {
        newStatus: 'DELIVERING',
        note: note || 'Shipper đã lấy bánh tại quán và bắt đầu đi giao',
      }
    );
    return res.data.data;
  },

  /**
   * Xác nhận giao hàng thành công (DELIVERING -> DELIVERED)
   */
  async confirmDelivery(orderCode: string, note?: string): Promise<OrderResponse> {
    const res = await axiosClient.put<ApiResponse<OrderResponse>>(
      `/shipper/orders/${orderCode}/deliver`,
      { note: note?.trim() || undefined }
    );
    return res.data.data;
  },

  /**
   * Báo cáo giao hàng thất bại (DELIVERING -> FAILED kèm lý do)
   */
  async failDelivery(orderCode: string, reason: string): Promise<OrderResponse> {
    const res = await axiosClient.put<ApiResponse<OrderResponse>>(
      `/shipper/orders/${orderCode}/fail`,
      { reason: reason.trim() }
    );
    return res.data.data;
  },

  /**
   * Shipper từ chối nhận đơn hàng được gán (READY_FOR_PICKUP)
   */
  async rejectOrder(orderCode: string, reason: string): Promise<OrderResponse> {
    const res = await axiosClient.put<ApiResponse<OrderResponse>>(
      `/shipper/orders/${orderCode}/reject`,
      { reason: reason.trim() }
    );
    return res.data.data;
  },

  /**
   * Lấy lịch sử trạng thái đơn hàng
   */
  async getOrderHistory(orderCode: string): Promise<OrderStatusHistoryItem[]> {
    const res = await axiosClient.get<ApiResponse<OrderStatusHistoryItem[]>>(
      `/shipper/orders/${orderCode}/history`
    );
    return res.data.data;
  },
};
