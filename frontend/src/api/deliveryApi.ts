import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { DeliveryFeeResult } from '../types/delivery';

export interface DeliveryFeeParams {
  /**
   * Toạ độ điểm giao. Có toạ độ + quán đã ghim vị trí → server tự tính khoảng cách;
   * bỏ trống thì tính theo khu vực (nội thành 15k / ngoại thành 30k).
   */
  latitude?: number | null;
  longitude?: number | null;
  shippingAddress?: string;
  subtotal?: number;
}

export const deliveryApi = {
  /**
   * Tính trước phí giao hàng để hiển thị ở giỏ / thanh toán.
   * Gọi cùng tham số (toạ độ + địa chỉ) mà `POST /orders` sẽ dùng để
   * số tiền xem trước khớp đúng số tiền chốt đơn. Ngoài bán kính giao → lỗi 400 kèm lý do.
   */
  async getFee({ latitude, longitude, shippingAddress, subtotal }: DeliveryFeeParams): Promise<DeliveryFeeResult> {
    const res = await axiosClient.get<ApiResponse<DeliveryFeeResult>>('/delivery/fee', {
      params: { latitude: latitude ?? undefined, longitude: longitude ?? undefined, shippingAddress, subtotal },
    });
    return res.data.data;
  },
};
