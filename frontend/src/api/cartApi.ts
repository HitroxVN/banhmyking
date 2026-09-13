import type { ApiResponse } from '../types/auth';
import type { AddToCartRequest, Cart, UpdateCartItemRequest } from '../types/cart';
import { axiosClient } from './axiosClient';

export const cartApi = {
  /**
   * Lấy dữ liệu giỏ hàng hiện tại của người dùng
   */
  async getCart(): Promise<Cart> {
    const response = await axiosClient.get<ApiResponse<Cart>>('/cart');
    return response.data.data;
  },

  /**
   * Thêm một món vào giỏ hàng
   */
  async addToCart(data: AddToCartRequest): Promise<Cart> {
    const response = await axiosClient.post<ApiResponse<Cart>>('/cart/items', data);
    return response.data.data;
  },

  /**
   * Cập nhật số lượng của một dòng món trong giỏ hàng
   */
  async updateQuantity(itemId: number, quantity: number): Promise<Cart> {
    const payload: UpdateCartItemRequest = { quantity };
    const response = await axiosClient.put<ApiResponse<Cart>>(`/cart/items/${itemId}`, payload);
    return response.data.data;
  },

  /**
   * Xóa một dòng món khỏi giỏ hàng
   */
  async removeItem(itemId: number): Promise<Cart> {
    const response = await axiosClient.delete<ApiResponse<Cart>>(`/cart/items/${itemId}`);
    return response.data.data;
  },

  /**
   * Xóa sạch toàn bộ giỏ hàng
   */
  async clearCart(): Promise<Cart> {
    const response = await axiosClient.delete<ApiResponse<Cart>>('/cart');
    return response.data.data;
  },
};
