import type { ApiResponse } from '../types/auth';
import type { AddressRequest, AddressResponse } from '../types/address';
import { axiosClient } from './axiosClient';

export const addressApi = {
  /**
   * Lấy danh sách địa chỉ giao hàng đã lưu của người dùng
   */
  async getAddresses(): Promise<AddressResponse[]> {
    const response = await axiosClient.get<ApiResponse<AddressResponse[]>>('/addresses');
    return response.data.data;
  },

  async createAddress(data: AddressRequest): Promise<AddressResponse> {
    const response = await axiosClient.post<ApiResponse<AddressResponse>>('/addresses', data);
    return response.data.data;
  },

  async updateAddress(addressId: number, data: AddressRequest): Promise<AddressResponse> {
    const response = await axiosClient.put<ApiResponse<AddressResponse>>(`/addresses/${addressId}`, data);
    return response.data.data;
  },

  async deleteAddress(addressId: number): Promise<void> {
    await axiosClient.delete<ApiResponse<void>>(`/addresses/${addressId}`);
  },

  /** Đặt làm địa chỉ mặc định (backend tự bỏ mặc định của các địa chỉ cũ) */
  async setDefaultAddress(addressId: number): Promise<AddressResponse> {
    const response = await axiosClient.patch<ApiResponse<AddressResponse>>(`/addresses/${addressId}/default`);
    return response.data.data;
  },
};
