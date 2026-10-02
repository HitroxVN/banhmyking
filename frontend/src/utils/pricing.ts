import type { CartItem } from '../types/cart';
import type { OrderItemResponse } from '../types/order';
import type { ProductItem } from '../types/staff';

/** Giá đang bán của món — server tính (effectivePrice); dữ liệu thiếu field thì dùng giá gốc. */
export const priceNow = (product: Pick<ProductItem, 'price' | 'effectivePrice'>): number =>
  product.effectivePrice ?? product.price;

/** Giá gạch của 1 đơn vị trong giỏ (đã gồm topping); null khi dòng không có ưu đãi. */
export const cartUnitCompareAt = (item: CartItem): number | null => {
  if (item.originalUnitPrice == null || item.originalUnitPrice <= item.basePrice) return null;
  return item.unitPrice + (item.originalUnitPrice - item.basePrice);
};

/**
 * Tiền tiết kiệm của giỏ, tính lại từ từng dòng — khớp cả khi CartProvider cập nhật số lượng
 * lạc quan (chưa có phản hồi server).
 */
export const cartSavings = (items: CartItem[]): number =>
  items.reduce((sum, item) => {
    const perUnit = (item.originalUnitPrice ?? item.basePrice) - item.basePrice;
    return perUnit > 0 ? sum + perUnit * item.quantity : sum;
  }, 0);

/** Giá gạch 1 đơn vị của dòng đơn (chưa topping, theo snapshot); đơn cũ → null. */
export const orderUnitCompareAt = (item: OrderItemResponse): number | null =>
  item.originalUnitPrice != null && item.originalUnitPrice > item.unitPrice ? item.originalUnitPrice : null;

/** "Gồm: 1× Bánh mì · 2× Cà phê" cho dòng combo trong đơn; món lẻ → "". */
export const orderComponentsText = (item: OrderItemResponse): string => {
  const components = item.components ?? [];
  if (components.length === 0) return '';
  return `Gồm: ${components.map((component) => `${component.quantity}× ${component.productName}`).join(' · ')}`;
};

export type SaleState = 'ACTIVE' | 'UPCOMING' | 'ENDED' | null;

/** Nhãn KM cho admin — chỉ hiển thị; server mới là nguồn sự thật của giá. */
export const saleState = (product: ProductItem, now: Date = new Date()): SaleState => {
  if (product.productType === 'COMBO' || product.salePrice == null) return null;
  if (product.onSale) return 'ACTIVE';
  if (product.saleStartsAt && new Date(product.saleStartsAt) > now) return 'UPCOMING';
  if (product.saleEndsAt && new Date(product.saleEndsAt) <= now) return 'ENDED';
  return null;
};

/** Hạn KM dạng "dd/MM HH:mm" (chuỗi server là giờ Việt Nam, không kèm múi giờ). */
export const formatSaleEnd = (iso: string): string =>
  new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

/** Giá trị cho `<input type="datetime-local">` từ chuỗi server (bỏ phần giây). */
export const toDateTimeLocal = (iso?: string | null): string => (iso ? iso.slice(0, 16) : '');
