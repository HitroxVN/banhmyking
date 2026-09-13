import type { ApiResponse, LoginRequest, RegisterRequest, TokenResponse, UserInfoResponse } from '../types/auth';
import { axiosClient } from './axiosClient';

export const authApi = {
  /**
   * Đăng ký tài khoản khách hàng mới
   */
  async register(data: RegisterRequest): Promise<TokenResponse> {
    const response = await axiosClient.post<ApiResponse<TokenResponse>>('/auth/register', data);
    return response.data.data;
  },

  /**
   * Đăng nhập hệ thống bằng email và password
   */
  async login(data: LoginRequest): Promise<TokenResponse> {
    const response = await axiosClient.post<ApiResponse<TokenResponse>>('/auth/login', data);
    return response.data.data;
  },

  /**
   * Lấy thông tin tài khoản hiện tại của người dùng
   */
  async getMe(): Promise<UserInfoResponse> {
    const response = await axiosClient.get<ApiResponse<UserInfoResponse>>('/auth/me');
    return response.data.data;
  },

  /**
   * Đăng xuất hệ thống và thu hồi refresh token
   */
  async logout(refreshToken: string): Promise<void> {
    await axiosClient.post<ApiResponse<void>>('/auth/logout', { refreshToken });
  },

  /**
   * Làm mới access token bằng refresh token
   */
  async refresh(refreshToken: string): Promise<TokenResponse> {
    const response = await axiosClient.post<ApiResponse<TokenResponse>>('/auth/refresh', { refreshToken });
    return response.data.data;
  },
};
