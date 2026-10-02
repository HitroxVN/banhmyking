import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
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
   * Lấy danh sách món ăn (Staff cần xem cả món còn lẫn món đã hết -> availableOnly = false).
   *
   * Endpoint trả theo trang; màn quản lý cần đủ bộ món để đếm và tìm tại chỗ nên gom hết
   * các trang lại. `content` rỗng cũng dừng vòng lặp — phòng khi cờ `last` sai thì không treo.
   */
  async getProducts(categoryId?: number, availableOnly: boolean = false): Promise<ProductItem[]> {
    const all: ProductItem[] = [];
    for (let page = 0; ; page += 1) {
      const res = await axiosClient.get<ApiResponse<PageResponse<ProductItem>>>('/catalog/products', {
        params: {
          categoryId,
          availableOnly,
          page,
          size: 50,
        },
      });
      const data = res.data.data;
      all.push(...data.content);
      if (data.last || data.content.length === 0) {
        return all;
      }
    }
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
   * Bật/Tắt nhanh tình trạng còn hàng / hết hàng (1 chạm).
   *
   * Cố ý KHÔNG gửi `options`/`optionGroups`: bỏ trống hai field đó nghĩa là giữ nguyên, còn
   * gửi lên sẽ thay toàn bộ — gửi `options` ở đây sẽ kéo lựa chọn đang thuộc nhóm ra phẳng.
   */
  async toggleProductAvailability(product: ProductItem): Promise<ProductItem> {
    const payload: ProductUpdatePayload = {
      categoryId: product.categoryId,
      name: product.name,
      description: product.description,
      imageUrl: product.imageUrl,
      price: product.price,
      // Đảo cờ của CHÍNH món: `available` của combo còn tính thành phần nên không dùng được ở đây
      available: !(product.enabled ?? product.available),
      featured: product.featured,
      // Bỏ trống salePrice nghĩa là xoá KM — phải gửi lại để bật/tắt không làm mất khuyến mãi
      salePrice: product.salePrice ?? null,
      saleStartsAt: product.saleStartsAt ?? null,
      saleEndsAt: product.saleEndsAt ?? null,
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

