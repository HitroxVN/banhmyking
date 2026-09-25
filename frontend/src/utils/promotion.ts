import { formatCurrency } from './formatters';
import type { DiscountType } from '../types/promotion';

/** Phần dữ liệu tối thiểu để mô tả mức giảm — cả `PromotionResponse` (admin) và
 *  `PublicPromotionResponse` (khách) đều thoả mãn interface này. */
interface PromotionDiscountLike {
  discountType: DiscountType;
  value: number;
  maxDiscountAmount?: number;
}

/**
 * Mô tả mức giảm bằng chữ. Cố tình KHÔNG kèm "đơn tối thiểu" vì mỗi chỗ hiển thị
 * nó một kiểu khác nhau — nhưng phần mức giảm thì phải giống nhau, nên để một nguồn duy nhất.
 */
export const describePromotionValue = (promo: PromotionDiscountLike): string => {
  if (promo.discountType === 'PERCENTAGE') {
    return promo.maxDiscountAmount
      ? `${promo.value}% · tối đa ${formatCurrency(promo.maxDiscountAmount)}`
      : `${promo.value}%`;
  }
  if (promo.discountType === 'FREE_SHIP') {
    return `Tối đa ${formatCurrency(promo.value)}`;
  }
  return formatCurrency(promo.value);
};
