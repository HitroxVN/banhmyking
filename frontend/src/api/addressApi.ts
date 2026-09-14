import type { ApiResponse } from '../types/auth';
import type { AddressResponse } from '../types/address';
import { axiosClient } from './axiosClient';

export const addressApi = {
  /**
   * Lấy danh sách địa chỉ giao hàng đã lưu của người dùng
   */
  async getAddresses(): Promise<AddressResponse[]> {
    const response = await axiosClient.get<ApiResponse<AddressResponse[]>>('/addresses');
    return response.data.data;
  },
};
