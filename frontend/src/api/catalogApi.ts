import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { CategoryItem, ProductItem } from '../types/staff';

/**
 * Thực đơn dành cho khách.
 * Lưu ý: backend `GET /catalog/products` chỉ nhận `categoryId` + `availableOnly`
 * (trả về List, không phân trang) — nên tìm kiếm/lọc/phân trang được xử lý ở client.
 */
export const catalogApi = {
  async getCategories(): Promise<CategoryItem[]> {
    const res = await axiosClient.get<ApiResponse<CategoryItem[]>>('/catalog/categories');
    return res.data.data ?? [];
  },

  /** Chỉ lấy món đang bán (availableOnly mặc định true) */
  async getProducts(categoryId?: number, availableOnly: boolean = true): Promise<ProductItem[]> {
    const res = await axiosClient.get<ApiResponse<ProductItem[]>>('/catalog/products', {
      params: { categoryId, availableOnly },
    });
    return res.data.data ?? [];
  },

  /** Chi tiết 1 món kèm danh sách topping */
  async getProduct(productId: number): Promise<ProductItem> {
    const res = await axiosClient.get<ApiResponse<ProductItem>>(`/catalog/products/${productId}`);
    return res.data.data;
  },
};
