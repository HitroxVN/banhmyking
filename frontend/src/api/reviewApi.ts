import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
import type { CreateReviewPayload, ProductRatingSummary, ReviewResponse } from '../types/review';

/** Bộ lọc cho trang quản lý đánh giá — bỏ trống = không lọc */
export interface ReviewQuery {
  productId?: number;
  /** 1–5 */
  rating?: number;
  /** Trang bắt đầu từ 0 (backend) */
  page?: number;
  size?: number;
}

/**
 * Đánh giá món ăn.
 * - `GET /products/{id}/reviews` + `GET /products/{id}/rating` là public (ai cũng xem được).
 * - `POST /reviews` cần đăng nhập và chỉ nhận đơn đã DELIVERED (backend kiểm tra).
 * - `GET /reviews` (STAFF, ADMIN) và `DELETE /reviews/{id}` (ADMIN) là trang quản lý.
 */
export const reviewApi = {
  async getProductReviews(productId: number, page: number = 0, size: number = 5): Promise<PageResponse<ReviewResponse>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<ReviewResponse>>>(`/products/${productId}/reviews`, {
      params: { page, size, sort: 'createdAt,desc' },
    });
    return res.data.data;
  },

  async getProductRating(productId: number): Promise<ProductRatingSummary> {
    const res = await axiosClient.get<ApiResponse<ProductRatingSummary>>(`/products/${productId}/rating`);
    return res.data.data;
  },

  async createReview(payload: CreateReviewPayload): Promise<ReviewResponse> {
    const res = await axiosClient.post<ApiResponse<ReviewResponse>>('/reviews', payload);
    return res.data.data;
  },

  /** Danh sách đánh giá cho trang quản lý (STAFF & ADMIN), mới nhất trước. */
  async getAllReviews({ productId, rating, page = 0, size = 10 }: ReviewQuery = {}): Promise<PageResponse<ReviewResponse>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<ReviewResponse>>>('/reviews', {
      params: { productId, rating, page, size },
    });
    return res.data.data;
  },

  /**
   * Xoá hẳn 1 đánh giá — chỉ ADMIN gọi được (backend chặn bằng hasRole('ADMIN')).
   * Xoá xong khách được đánh giá lại món đó và điểm sao của món tính lại ngay.
   */
  async deleteReview(id: number): Promise<void> {
    await axiosClient.delete(`/reviews/${id}`);
  },
};
