/**
 * Review types matching backend DTOs:
 * - ReviewResponse
 * - ProductRatingSummaryResponse
 * - CreateReviewRequest
 */

export interface ReviewResponse {
  id: number;
  productId: number;
  productName: string;
  userId: number;
  userFullName?: string;
  orderItemId: number;
  rating: number;
  comment?: string;
  createdAt: string;
}

/**
 * `GET /products/{id}/rating` — backend dùng COALESCE(AVG(rating), 0.0) nên khi món
 * chưa có đánh giá thì averageRating = 0 và totalReviews = 0 (không null).
 */
export interface ProductRatingSummary {
  productId: number;
  averageRating: number;
  totalReviews: number;
}

/** Body của `POST /reviews` — orderItemId là id dòng món trong đơn đã giao */
export interface CreateReviewPayload {
  orderItemId: number;
  rating: number;
  comment?: string;
}
