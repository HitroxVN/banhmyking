import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
import type { OrderResponse, OrderStatus } from '../types/order';
import type { ShipperAvailability } from '../types/staff';

export const staffOrderApi = {
  /**
   * Lấy danh sách hàng đợi đơn hàng toàn hệ thống dành cho nhân viên (Staff)
   */
  async getOrderQueue(
    status?: OrderStatus,
    page: number = 0,
    size: number = 50
  ): Promise<PageResponse<OrderResponse>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<OrderResponse>>>('/admin/orders', {
      params: {
        status,
        page,
        size,
      },
    });
    return res.data.data;
  },

  /**
   * Cập nhật trạng thái đơn hàng (State machine)
   */
  async updateOrderStatus(
    orderCode: string,
    newStatus: OrderStatus,
    note?: string
  ): Promise<OrderResponse> {
    const res = await axiosClient.put<ApiResponse<OrderResponse>>(
      `/admin/orders/${orderCode}/status`,
      {
        newStatus,
        note,
      }
    );
    return res.data.data;
  },

  /**
   * Chuyển đơn từ PENDING sang PREPARING (nếu đơn đang PENDING, chuyển qua CONFIRMED rồi PREPARING)
   */
  async startPreparingOrder(order: OrderResponse, note?: string): Promise<OrderResponse> {
    if (order.status === 'PENDING') {
      // Step 1: Xác nhận đơn
      await this.updateOrderStatus(order.orderCode, 'CONFIRMED', 'Nhân viên xác nhận đơn hàng');
      // Step 2: Bắt đầu làm bánh
      return await this.updateOrderStatus(
        order.orderCode,
        'PREPARING',
        note || 'Nhân viên bếp bắt đầu làm bánh'
      );
    } else if (order.status === 'CONFIRMED') {
      return await this.updateOrderStatus(
        order.orderCode,
        'PREPARING',
        note || 'Nhân viên bếp bắt đầu làm bánh'
      );
    }
    return order;
  },

  /**
   * Chuyển đơn từ PREPARING sang READY_FOR_PICKUP (Bánh đã làm xong, sẵn sàng chờ Shipper)
   */
  async markReadyForPickup(orderCode: string, note?: string): Promise<OrderResponse> {
    return await this.updateOrderStatus(
      orderCode,
      'READY_FOR_PICKUP',
      note || 'Bánh đã làm xong, chờ Shipper nhận hàng'
    );
  },

  /**
   * Lấy danh sách tất cả các tài xế (Shipper) kèm số đơn DELIVERING thực tế
   */
  async getAvailableShippers(): Promise<ShipperAvailability[]> {
    const res = await axiosClient.get<ApiResponse<ShipperAvailability[]>>(
      '/admin/orders/shippers/available'
    );
    return res.data.data;
  },

  /**
   * Gán Shipper đang rảnh cho đơn hàng đã chuẩn bị xong
   */
  async assignShipper(
    orderCode: string,
    shipperId: number,
    note?: string
  ): Promise<OrderResponse> {
    const res = await axiosClient.put<ApiResponse<OrderResponse>>(
      `/admin/orders/${orderCode}/assign-shipper`,
      {
        shipperId,
        note,
      }
    );
    return res.data.data;
  },

  /**
   * Hủy đơn hàng với lý do cụ thể
   */
  async cancelOrder(orderCode: string, reason: string): Promise<OrderResponse> {
    const res = await axiosClient.put<ApiResponse<OrderResponse>>(
      `/admin/orders/${orderCode}/cancel`,
      {
        reason,
      }
    );
    return res.data.data;
  },
};
