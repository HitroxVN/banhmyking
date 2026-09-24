import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type {
  CategoryItem,
  ProductItem,
  ProductCreatePayload,
  ProductUpdatePayload,
} from '../types/staff';

export interface CategoryPayload {
  name: string;
  description?: string;
  sortOrder?: number;
}

export const staffCatalogApi = {
  /**
   * Lấy danh sách toàn bộ danh mục món ăn
   */
  async getCategories(): Promise<CategoryItem[]> {
    const res = await axiosClient.get<ApiResponse<CategoryItem[]>>('/catalog/categories');
    return res.data.data;
  },

  /**
   * Tạo danh mục mới
   */
  async createCategory(payload: CategoryPayload): Promise<CategoryItem> {
    const res = await axiosClient.post<ApiResponse<CategoryItem>>('/catalog/categories', payload);
    return res.data.data;
  },

  /**
   * Cập nhật danh mục (tên, mô tả, thứ tự hiển thị)
   */
  async updateCategory(id: number, payload: CategoryPayload): Promise<CategoryItem> {
    const res = await axiosClient.put<ApiResponse<CategoryItem>>(`/catalog/categories/${id}`, payload);
    return res.data.data;
  },

  /**
   * Xoá danh mục (backend đánh dấu đã xoá, không xoá cứng)
   */
  async deleteCategory(id: number): Promise<void> {
    await axiosClient.delete<ApiResponse<void>>(`/catalog/categories/${id}`);
  },

  /**
   * Lấy danh sách món ăn (Staff cần xem cả món còn lẫn món đã hết -> availableOnly = false)
   */
  async getProducts(categoryId?: number, availableOnly: boolean = false): Promise<ProductItem[]> {
    const res = await axiosClient.get<ApiResponse<ProductItem[]>>('/catalog/products', {
      params: {
        categoryId,
        availableOnly,
      },
    });
    return res.data.data;
  },

  /**
   * Tạo món ăn mới vào thực đơn
   */
  async createProduct(payload: ProductCreatePayload): Promise<ProductItem> {
    const res = await axiosClient.post<ApiResponse<ProductItem>>('/catalog/products', payload);
    return res.data.data;
  },

  /**
   * Cập nhật thông tin món ăn (Tên, giá, mô tả, ảnh, trạng thái)
   */
  async updateProduct(id: number, payload: ProductUpdatePayload): Promise<ProductItem> {
    const res = await axiosClient.put<ApiResponse<ProductItem>>(`/catalog/products/${id}`, payload);
    return res.data.data;
  },

  /**
   * Bật/Tắt nhanh tình trạng còn hàng / hết hàng (1 chạm)
   */
  async toggleProductAvailability(product: ProductItem): Promise<ProductItem> {
    const payload: ProductUpdatePayload = {
      categoryId: product.categoryId,
      name: product.name,
      description: product.description,
      imageUrl: product.imageUrl,
      price: product.price,
      available: !product.available,
      featured: product.featured,
      options: product.options?.map((o) => ({ name: o.name, extraPrice: o.extraPrice })),
    };
    const res = await axiosClient.put<ApiResponse<ProductItem>>(`/catalog/products/${product.id}`, payload);
    return res.data.data;
  },

  /**
   * Xoá món ăn khỏi thực đơn
   */
  async deleteProduct(id: number): Promise<void> {
    await axiosClient.delete<ApiResponse<void>>(`/catalog/products/${id}`);
  },

  /**
   * Tải tệp hình ảnh món ăn lên máy chủ (Multipart Upload)
   */
  async uploadImage(file: File): Promise<string> {
    const formData = new FormData();
    formData.append('file', file);
    const res = await axiosClient.post<ApiResponse<string>>('/catalog/products/upload-image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return res.data.data;
  },
};

