import { axiosClient } from './axiosClient';
import type { ApiResponse, RoleName } from '../types/auth';
import type {
  AdminUser,
  AdminCreateUserPayload,
  AdminUpdateUserPayload,
  UserFilterParams,
  PageResponse,
} from '../types/admin';

export const adminUserApi = {
  /**
   * Lấy danh sách người dùng có phân trang & bộ lọc
   */
  async getUsers(params: UserFilterParams = {}): Promise<PageResponse<AdminUser>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<AdminUser>>>('/admin/users', {
      params,
    });
    return res.data.data;
  },

  /**
   * Xem thông tin chi tiết một người dùng theo ID
   */
  async getUser(id: number): Promise<AdminUser> {
    const res = await axiosClient.get<ApiResponse<AdminUser>>(`/admin/users/${id}`);
    return res.data.data;
  },

  /**
   * Tạo tài khoản người dùng mới (Staff, Shipper, Customer)
   */
  async createUser(payload: AdminCreateUserPayload): Promise<AdminUser> {
    const res = await axiosClient.post<ApiResponse<AdminUser>>('/admin/users', payload);
    return res.data.data;
  },

  /**
   * Cập nhật thông tin và phân quyền người dùng
   */
  async updateUser(id: number, payload: AdminUpdateUserPayload): Promise<AdminUser> {
    const res = await axiosClient.put<ApiResponse<AdminUser>>(`/admin/users/${id}`, payload);
    return res.data.data;
  },

  /**
   * Thay đổi vai trò người dùng (STAFF, SHIPPER, CUSTOMER, ADMIN)
   */
  async changeRole(id: number, role: RoleName): Promise<AdminUser> {
    const res = await axiosClient.patch<ApiResponse<AdminUser>>(`/admin/users/${id}/role`, { role });
    return res.data.data;
  },

  /**
   * Khóa hoặc mở khóa tài khoản người dùng
   */
  async changeStatus(id: number, banned: boolean): Promise<AdminUser> {
    const res = await axiosClient.patch<ApiResponse<AdminUser>>(`/admin/users/${id}/status`, { banned });
    return res.data.data;
  },

  /**
   * Xóa mềm tài khoản người dùng
   */
  async deleteUser(id: number): Promise<void> {
    await axiosClient.delete<ApiResponse<void>>(`/admin/users/${id}`);
  },
};
