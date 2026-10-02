import type { OrderResponse, OrderStatus } from './order';

export interface ProductOption {
  id?: number;
  name: string;
  extraPrice: number;
  /** Nhóm chứa lựa chọn này; null/undefined = lựa chọn phẳng (dữ liệu cũ, không chịu luật nhóm). */
  groupId?: number | null;
}

/**
 * Nhóm lựa chọn (Size/Topping). `maxChoices` = 0 nghĩa là không giới hạn, 1 nghĩa là chọn một
 * (hiển thị radio).
 */
export interface OptionGroup {
  id?: number;
  name: string;
  required: boolean;
  maxChoices: number;
  sortOrder?: number;
  options: ProductOption[];
}

/** Payload gửi lên: nhóm kèm danh sách lựa chọn của nó (không có `id`/`groupId`). */
export interface OptionGroupPayload {
  name: string;
  required: boolean;
  maxChoices: number;
  options: Array<{ name: string; extraPrice: number }>;
}

/** SINGLE = món lẻ, COMBO = combo cố định gồm nhiều món lẻ (ProductType backend). */
export type ProductType = 'SINGLE' | 'COMBO';

/** Một món lẻ trong combo — ComboItemResponse. */
export interface ComboItemInfo {
  productId: number;
  name: string;
  imageUrl?: string | null;
  /** Giá gốc của món lẻ */
  price: number;
  /** Số phần trong một combo */
  quantity: number;
}

/** Dòng thành phần gửi lên khi tạo/sửa combo — ComboItemRequest. */
export interface ComboItemPayload {
  productId: number;
  quantity: number;
}

/** Giá KM + thành phần combo gửi kèm món (ProductRequest). */
export interface ProductPricingPayload {
  /** Chỉ món lẻ. null/bỏ trống = không khuyến mãi (xoá KM đang có). */
  salePrice?: number | null;
  /** 'yyyy-MM-ddTHH:mm' giờ Việt Nam; null = áp dụng ngay */
  saleStartsAt?: string | null;
  /** null = không hết hạn */
  saleEndsAt?: string | null;
  /** Chỉ combo. Sửa combo mà bỏ trống = giữ nguyên thành phần. */
  comboItems?: ComboItemPayload[];
}

export interface CategoryItem {
  id: number;
  name: string;
  description?: string;
  /** Thứ tự hiển thị trong thực đơn (CategoryResponse.sortOrder) */
  sortOrder?: number;
}

export interface ProductItem {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  description?: string;
  imageUrl?: string;
  /** Bộ ảnh chi tiết, đã sắp theo thứ tự hiển thị. */
  images?: string[];
  price: number;
  /** Khách đặt được (combo: còn xét mọi thành phần đang bán) */
  available: boolean;
  /** Cờ bật/tắt của chính món — nút bật/tắt của admin dùng cờ này */
  enabled?: boolean;
  featured: boolean;
  productType?: ProductType;
  /** Giá KM đã cấu hình (kể cả chưa tới hạn / đã hết) */
  salePrice?: number | null;
  /** 'yyyy-MM-ddTHH:mm:ss' giờ Việt Nam */
  saleStartsAt?: string | null;
  saleEndsAt?: string | null;
  /** Giá đang bán do server tính — client chỉ hiển thị */
  effectivePrice?: number;
  /** Giá gạch; null = không gạch */
  compareAtPrice?: number | null;
  discountPercent?: number | null;
  /** Món lẻ đang trong thời gian KM */
  onSale?: boolean;
  /** Thành phần combo; rỗng với món lẻ */
  comboItems?: ComboItemInfo[];
  /** Điểm trung bình từ đánh giá khách (0 khi chưa có đánh giá nào) */
  averageRating?: number;
  totalReviews?: number;
  options?: ProductOption[];
  /** Nhóm lựa chọn đã sắp theo thứ tự hiển thị; rỗng/không có = món chỉ có option phẳng. */
  optionGroups?: OptionGroup[];
}

export interface ProductCreatePayload extends ProductPricingPayload {
  /** Chỉ khi tạo; bỏ trống = SINGLE */
  productType?: ProductType;
  categoryId: number;
  name: string;
  description?: string;
  imageUrl?: string;
  /** Bộ ảnh chi tiết. Bỏ trống = không có ảnh nào. */
  images?: string[];
  price: number;
  available: boolean;
  featured?: boolean;
  options?: Array<{ name: string; extraPrice: number }>;
  /** Nhóm lựa chọn, theo đúng thứ tự hiển thị. Bỏ trống = món không có nhóm nào. */
  optionGroups?: OptionGroupPayload[];
}

export interface ProductUpdatePayload extends ProductPricingPayload {
  categoryId: number;
  name: string;
  description?: string;
  imageUrl?: string;
  /**
   * Bộ ảnh chi tiết, gửi cả mảng = thay toàn bộ. Khác `options`: bỏ trống = giữ nguyên
   * bộ ảnh cũ (để các chỗ chỉ sửa 1 field như bật/tắt còn hàng không xoá mất ảnh).
   */
  images?: string[];
  price: number;
  available: boolean;
  featured?: boolean;
  options?: Array<{ name: string; extraPrice: number }>;
  /**
   * Nhóm lựa chọn, gửi cả mảng = thay toàn bộ nhóm hiện có (lựa chọn của nhóm bị bỏ khỏi
   * payload sẽ bị xoá — backend trả 409 nếu còn trong giỏ của khách).
   */
  optionGroups?: OptionGroupPayload[];
}

export type StockMovementReason = 'IMPORT' | 'ORDER' | 'RESTORE' | 'ADJUST';

export interface StockMovement {
  id: number;
  /** Số thay đổi: dương = nhập, âm = giảm. */
  changeQty: number;
  reason: StockMovementReason;
  /** Có khi thay đổi phát sinh từ một đơn hàng. */
  orderCode?: string;
  note?: string;
  createdAt: string;
}

export interface ShipperAvailability {
  id: number;
  fullName: string;
  phone: string;
  email: string;
  activeOrdersCount: number;
  available: boolean;
}

export interface QueueFilterParams {
  status?: OrderStatus;
  page?: number;
  size?: number;
}

export interface StaffOrderQueueItem extends OrderResponse {
  elapsedMinutes?: number;
}
