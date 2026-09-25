import type { OrderResponse, OrderStatus } from './order';

export interface ProductOption {
  id?: number;
  name: string;
  extraPrice: number;
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
  price: number;
  available: boolean;
  featured: boolean;
  options?: ProductOption[];
}

export interface ProductCreatePayload {
  categoryId: number;
  name: string;
  description?: string;
  imageUrl?: string;
  price: number;
  available: boolean;
  featured?: boolean;
  options?: Array<{ name: string; extraPrice: number }>;
}

export interface ProductUpdatePayload {
  categoryId: number;
  name: string;
  description?: string;
  imageUrl?: string;
  price: number;
  available: boolean;
  featured?: boolean;
  options?: Array<{ name: string; extraPrice: number }>;
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
