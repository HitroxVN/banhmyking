import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { DeliveryFeeResult } from '../types/delivery';

export interface DeliveryFeeParams {
  /** Bỏ trống thì backend tính theo khu vực (nội thành 15k / ngoại thành 30k) */
  distanceKm?: number;
  shippingAddress?: string;
  subtotal?: number;
}

export const deliveryApi = {
  /**
   * Tính trước phí giao hàng để hiển thị ở giỏ / thanh toán.
   * Gọi cùng tham số mà `POST /orders` sẽ dùng (không truyền distanceKm) để
   * số tiền xem trước khớp đúng số tiền chốt đơn.
   */
  async getFee({ distanceKm, shippingAddress, subtotal }: DeliveryFeeParams): Promise<DeliveryFeeResult> {
    const res = await axiosClient.get<ApiResponse<DeliveryFeeResult>>('/delivery/fee', {
      params: { distanceKm, shippingAddress, subtotal },
    });
    return res.data.data;
  },
};
