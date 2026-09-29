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

/**
 * Mã giảm giá hiển thị cho khách ở trang thanh toán — khớp `PublicPromotionResponse`.
 * KHÔNG có `id` / `usedCount` / `maxUsage`: backend cố tình không trả thông tin nội bộ.
 */
export interface PublicPromotionResponse {
  code: string;
  description?: string;
  discountType: DiscountType;
  /** PERCENTAGE: 1–100 · FIXED_AMOUNT/FREE_SHIP: số tiền */
  value: number;
  maxDiscountAmount?: number;
  minOrderAmount?: number;
  endsAt?: string;
}

/**
 * Một dòng trong ví mã — khớp `WalletPromotionResponse`.
 * `used = false` là mã khả dụng (3 field cuối rỗng); `used = true` là mã đã dùng kèm đơn đã áp.
 */
export interface WalletPromotion {
  code: string;
  description?: string;
  discountType: DiscountType;
  /** PERCENTAGE: 1–100 · FIXED_AMOUNT/FREE_SHIP: số tiền */
  value: number;
  maxDiscountAmount?: number;
  minOrderAmount?: number;
  endsAt?: string;
  used: boolean;
  usedAt?: string;
  orderCode?: string;
  discountApplied?: number;
}

export interface ValidatePromotionPayload {
  code: string;
  /** Giá trị đơn trước phí ship — khớp `validateForOrder(code, userId, subtotal)` của backend */
  orderAmount: number;
  /** Phí ship dự kiến, chỉ cần cho mã FREE_SHIP */
  shippingFee?: number;
  userId?: number;
}
