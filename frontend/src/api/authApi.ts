import type { ApiResponse, LoginRequest, RegisterRequest, TokenResponse, UserInfoResponse } from '../types/auth';
import { axiosClient } from './axiosClient';

export const authApi = {
  /**
   * Đăng ký tài khoản khách hàng mới.
   * Tài khoản tạo ra ở trạng thái CHƯA xác thực email nên KHÔNG có token trả về —
   * phải bấm link trong mail rồi mới đăng nhập được. Trả về message của backend.
   */
  async register(data: RegisterRequest): Promise<string> {
    const response = await axiosClient.post<ApiResponse<void>>('/auth/register', data);
    return response.data.message ?? 'Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản.';
  },

  /**
   * Xác thực email bằng token lấy từ query string trên link trong mail
   */
  async verifyEmail(token: string): Promise<string> {
    const response = await axiosClient.post<ApiResponse<void>>('/auth/verify-email', { token });
    return response.data.message ?? 'Đã xác thực thành công email.';
  },

  /**
   * Gửi lại email xác thực cho tài khoản chưa xác thực
   */
  async resendVerification(email: string): Promise<string> {
    const response = await axiosClient.post<ApiResponse<void>>('/auth/resend-verification', { email });
    return response.data.message ?? 'Đã gửi lại email xác thực. Vui lòng kiểm tra hộp thư.';
  },

  /**
   * Quên mật khẩu bước 1: yêu cầu gửi link đặt lại.
   * Backend trả cùng một thông điệp dù email có tồn tại hay không.
   */
  async forgotPassword(email: string): Promise<string> {
    const response = await axiosClient.post<ApiResponse<void>>('/auth/forgot-password', { email });
    return (
      response.data.message ??
      'Nếu email này đã đăng ký, chúng tôi đã gửi link đặt lại mật khẩu. Vui lòng kiểm tra hộp thư.'
    );
  },

  /**
   * Quên mật khẩu bước 2: đặt mật khẩu mới bằng token trong link.
   * Backend thu hồi toàn bộ phiên đang đăng nhập → phải đăng nhập lại.
   */
  async resetPassword(token: string, newPassword: string): Promise<string> {
    const response = await axiosClient.post<ApiResponse<void>>('/auth/reset-password', { token, newPassword });
    return response.data.message ?? 'Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.';
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
   * Tải ảnh đại diện mới lên. Backend thay ảnh cũ và xoá file cũ.
   * Ảnh tối đa 5MB, định dạng image/*.
   */
  async uploadAvatar(file: File): Promise<UserInfoResponse> {
    const formData = new FormData();
    formData.append('file', file);
    // PHẢI set tường minh: axiosClient mặc định application/json, mà axios thấy FormData +
    // content-type JSON thì stringify FormData thành JSON → backend multipart từ chối.
    const response = await axiosClient.post<ApiResponse<UserInfoResponse>>('/users/me/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data.data;
  },

  /** Xoá ảnh đại diện, quay về mặc định. */
  async removeAvatar(): Promise<UserInfoResponse> {
    const response = await axiosClient.delete<ApiResponse<UserInfoResponse>>('/users/me/avatar');
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
