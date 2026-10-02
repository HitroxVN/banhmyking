import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PublicStore, Store, StorePayload } from '../types/store';

export const storeApi = {
  async listPublic(): Promise<PublicStore[]> {
    const res = await axiosClient.get<ApiResponse<PublicStore[]>>('/stores');
    return res.data.data;
  },
  async adminList(): Promise<Store[]> {
    const res = await axiosClient.get<ApiResponse<Store[]>>('/admin/stores');
    return res.data.data;
  },
  async adminCreate(payload: StorePayload): Promise<Store> {
    const res = await axiosClient.post<ApiResponse<Store>>('/admin/stores', payload);
    return res.data.data;
  },
  async adminUpdate(id: number, payload: StorePayload): Promise<Store> {
    const res = await axiosClient.put<ApiResponse<Store>>(`/admin/stores/${id}`, payload);
    return res.data.data;
  },
  async adminDelete(id: number): Promise<void> {
    await axiosClient.delete<ApiResponse<void>>(`/admin/stores/${id}`);
  },
  /** MANAGER (cơ sở mình) / ADMIN: tạm ngưng hoặc mở lại nhận đơn */
  async setAccepting(storeId: number, accepting: boolean): Promise<Store> {
    const res = await axiosClient.patch<ApiResponse<Store>>(`/manager/stores/${storeId}/accepting`, { accepting });
    return res.data.data;
  },
};
