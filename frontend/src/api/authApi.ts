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
   * Cập nhật hồ sơ cá nhân (email không đổi được ở backend)
   */
  async updateProfile(data: { fullName: string; phone?: string }): Promise<UserInfoResponse> {
    const response = await axiosClient.patch<ApiResponse<UserInfoResponse>>('/users/me', data);
    return response.data.data;
  },

  /**
   * Đổi mật khẩu. Backend thu hồi toàn bộ refresh token sau khi đổi
   * → phải đăng nhập lại để có phiên mới.
   */
  async changePassword(data: { oldPassword: string; newPassword: string }): Promise<void> {
    await axiosClient.patch<ApiResponse<void>>('/auth/change-password', data);
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
