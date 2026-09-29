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
  available: boolean;
  featured: boolean;
  /** Điểm trung bình từ đánh giá khách (0 khi chưa có đánh giá nào) */
  averageRating?: number;
  totalReviews?: number;
  options?: ProductOption[];
  /** Nhóm lựa chọn đã sắp theo thứ tự hiển thị; rỗng/không có = món chỉ có option phẳng. */
  optionGroups?: OptionGroup[];
  /** Số tồn hiện tại; null/undefined = món không quản tồn. */
  stockQuantity?: number | null;
  /** Ngưỡng cảnh báo sắp hết (mặc định 5). */
  lowStockThreshold?: number;
  /** Đã bật quản tồn và tồn đang <= ngưỡng cảnh báo. */
  lowStock?: boolean;
}

export interface ProductCreatePayload {
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
  /** Tồn ban đầu. Bỏ trống = món không quản tồn. */
  stockQuantity?: number;
  /** Ngưỡng cảnh báo sắp hết. Bỏ trống = giữ mặc định (5). */
  lowStockThreshold?: number;
}

export interface ProductUpdatePayload {
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
  /** Ngưỡng cảnh báo sắp hết. Bỏ trống = giữ nguyên ngưỡng cũ. */
  lowStockThreshold?: number;
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
