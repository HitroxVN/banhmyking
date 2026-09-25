/**
 * TypeScript types matching Spring Boot backend DTOs:
 * - PromotionResponse
 */

export type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT' | 'FREE_SHIP';

export interface PromotionResponse {
  id: number;
  code: string;
  description?: string;
  discountType: DiscountType;
  /** PERCENTAGE: 1–100 · FIXED_AMOUNT/FREE_SHIP: số tiền */
  value: number;
  maxDiscountAmount?: number;
  minOrderAmount?: number;
  startsAt?: string;
  endsAt?: string;
  maxUsage?: number;
  usedCount?: number;
  active: boolean;
  /** Số tiền thực được giảm cho đơn đang xét — backend tính, FE chỉ hiển thị */
  discountApplied: number;
  createdAt?: string;
}

/**
 * Body tạo/sửa mã (trang ADMIN) — khớp `CreatePromotionRequest` / `UpdatePromotionRequest`.
 * `active` bắt buộc ở cả 2 nên luôn gửi; `maxDiscountAmount` chỉ có nghĩa với PERCENTAGE.
 */
export interface PromotionPayload {
  code: string;
  description: string;
  discountType: DiscountType;
  /** PERCENTAGE: 1–100 · FIXED_AMOUNT/FREE_SHIP: số tiền */
  value: number;
  maxDiscountAmount?: number;
  minOrderAmount: number;
  startsAt: string;
  endsAt: string;
  maxUsage: number;
  active: boolean;
}

export interface ValidatePromotionPayload {
  code: string;
  /** Giá trị đơn trước phí ship — khớp `validateForOrder(code, userId, subtotal)` của backend */
  orderAmount: number;
  /** Phí ship dự kiến, chỉ cần cho mã FREE_SHIP */
  shippingFee?: number;
  userId?: number;
}
