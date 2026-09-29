import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type {
  PromotionPayload,
  PromotionResponse,
  PublicPromotionResponse,
  ValidatePromotionPayload,
  WalletPromotion,
} from '../types/promotion';

/** Mã giảm giá của khách — kiểm tra & tính số tiền giảm trước khi đặt đơn. */
export const promotionApi = {
  /**
   * Mã khách đang dùng được, để khách chọn thay vì phải biết trước mã.
   * Lỗi mạng ở đây KHÔNG được chặn việc đặt hàng — FE chỉ coi đây là gợi ý.
   */
  async getPublicPromotions(): Promise<PublicPromotionResponse[]> {
    const res = await axiosClient.get<ApiResponse<PublicPromotionResponse[]>>('/promotions/public');
    return res.data.data;
  },

  /**
   * Backend là nguồn sự thật duy nhất của số tiền giảm (cùng công thức với lúc tạo đơn),
   * nên FE không tự tính lại. Lỗi (hết hạn / chưa đủ đơn tối thiểu / hết lượt) trả về
   * `Error` với message tiếng Việt từ backend.
   */
  async validate(payload: ValidatePromotionPayload): Promise<PromotionResponse> {
    const res = await axiosClient.post<ApiResponse<PromotionResponse>>('/promotions/validate', payload);
    return res.data.data;
  },

  /** Ví mã của khách đang đăng nhập: mã còn dùng được + mã đã dùng. */
  async getWallet(): Promise<WalletPromotion[]> {
    const res = await axiosClient.get<ApiResponse<WalletPromotion[]>>('/promotions/wallet');
    return res.data.data;
  },

  /** Các API dưới đây chỉ ADMIN gọi được (backend chặn bằng hasRole('ADMIN')). */
  async getAllPromotions(): Promise<PromotionResponse[]> {
    const res = await axiosClient.get<ApiResponse<PromotionResponse[]>>('/admin/promotions');
    return res.data.data;
  },

  async createPromotion(payload: PromotionPayload): Promise<PromotionResponse> {
    const res = await axiosClient.post<ApiResponse<PromotionResponse>>('/admin/promotions', payload);
    return res.data.data;
  },

  async updatePromotion(id: number, payload: PromotionPayload): Promise<PromotionResponse> {
    const res = await axiosClient.put<ApiResponse<PromotionResponse>>(`/admin/promotions/${id}`, payload);
    return res.data.data;
  },

  async deletePromotion(id: number): Promise<void> {
    await axiosClient.delete(`/admin/promotions/${id}`);
  },
};
