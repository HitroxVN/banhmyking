import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
import type { CategoryItem, ProductItem, ProductType } from '../types/staff';

/** Cách sắp xếp thực đơn — phải khớp whitelist `ProductSort` của backend. */
export type ProductSortValue = 'FEATURED' | 'PRICE_ASC' | 'PRICE_DESC' | 'NAME' | 'NEWEST';

/**
 * Bộ lọc thực đơn. Backend tìm/lọc/sắp xếp/phân trang hết — frontend không lọc lại
 * trên kết quả, vì kết quả chỉ là một trang chứ không phải toàn bộ danh sách.
 */
export interface ProductSearchParams {
  keyword?: string;
  categoryId?: number;
  availableOnly?: boolean;
  featured?: boolean;
  /** Giá thấp nhất — bỏ trống = không lọc. Backend bỏ qua mốc <= 0. */
  minPrice?: number;
  /** Giá cao nhất — bỏ trống = không lọc. */
  maxPrice?: number;
  /** Chỉ món lẻ đang khuyến mãi */
  onSale?: boolean;
  /** SINGLE | COMBO */
  type?: ProductType;
  sort?: ProductSortValue;
  /** 0-based, khớp backend (UI hiển thị 1-based) */
  page?: number;
  size?: number;
}

export const catalogApi = {
  async getCategories(): Promise<CategoryItem[]> {
    const res = await axiosClient.get<ApiResponse<CategoryItem[]>>('/catalog/categories');
    return res.data.data ?? [];
  },

  /** Một trang thực đơn đã lọc/sắp xếp sẵn ở server */
  async getProducts(params: ProductSearchParams = {}): Promise<PageResponse<ProductItem>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<ProductItem>>>('/catalog/products', {
      params,
    });
    return res.data.data;
  },

  /** Chi tiết 1 món kèm danh sách topping */
  async getProduct(productId: number): Promise<ProductItem> {
    const res = await axiosClient.get<ApiResponse<ProductItem>>(`/catalog/products/${productId}`);
    return res.data.data;
  },
};
