import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { DeliveryQuote } from '../types/store';

export interface DeliveryQuoteParams {
  latitude?: number | null;
  longitude?: number | null;
  shippingAddress?: string;
}

export const deliveryApi = {
  /**
   * Báo giá theo từng cơ sở cho giỏ hàng hiện tại (server tự đọc giỏ) — cùng logic mà
   * `POST /orders` dùng để chọn cơ sở và tính phí, nên số xem trước khớp số chốt đơn.
   */
  async getQuote({ latitude, longitude, shippingAddress }: DeliveryQuoteParams): Promise<DeliveryQuote> {
    const res = await axiosClient.get<ApiResponse<DeliveryQuote>>('/delivery/quote', {
      params: { latitude: latitude ?? undefined, longitude: longitude ?? undefined, shippingAddress },
    });
    return res.data.data;
  },
};
