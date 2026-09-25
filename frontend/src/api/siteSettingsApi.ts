import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { SiteSettings } from '../types/siteSettings';

export const siteSettingsApi = {
  /**
   * Đọc cấu hình nội dung website. Công khai (không cần token) vì trang đăng nhập
   * cũng hiện tên web.
   */
  async getPublic(): Promise<SiteSettings> {
    const res = await axiosClient.get<ApiResponse<SiteSettings>>('/site-settings');
    return res.data.data;
  },

  /**
   * Cập nhật một phần — chỉ những field có trong `payload` được ghi, field bỏ trống giữ nguyên.
   */
  async update(payload: Partial<SiteSettings>): Promise<SiteSettings> {
    const res = await axiosClient.put<ApiResponse<SiteSettings>>('/admin/site-settings', {
      settings: payload,
    });
    return res.data.data;
  },

  /** Tải ảnh banner mới lên, backend xoá file ảnh cũ. Tối đa 5MB, định dạng image/*. */
  async uploadHeroImage(file: File): Promise<string> {
    const formData = new FormData();
    formData.append('file', file);
    // PHẢI set tường minh: axiosClient mặc định application/json, mà axios thấy FormData +
    // content-type JSON thì stringify FormData thành JSON → backend multipart từ chối.
    const res = await axiosClient.post<ApiResponse<string>>('/admin/site-settings/hero-image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data.data;
  },
};
