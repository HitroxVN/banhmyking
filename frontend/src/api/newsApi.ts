import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
import type { AdminNews, AdminNewsFilter, NewsDetail, NewsPayload, NewsSummary } from '../types/content';

export const newsApi = {
  /** Bài đã đăng: ghim trước, mới nhất trước */
  async listPublished(page = 0, size = 9): Promise<PageResponse<NewsSummary>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<NewsSummary>>>('/news', { params: { page, size } });
    return res.data.data;
  },
  async latest(limit = 3): Promise<NewsSummary[]> {
    const res = await axiosClient.get<ApiResponse<NewsSummary[]>>('/news/latest', { params: { limit } });
    return res.data.data;
  },
  async getBySlug(slug: string): Promise<NewsDetail> {
    const res = await axiosClient.get<ApiResponse<NewsDetail>>(`/news/${encodeURIComponent(slug)}`);
    return res.data.data;
  },
  async adminList(filter: AdminNewsFilter = {}): Promise<PageResponse<AdminNews>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<AdminNews>>>('/admin/news', { params: filter });
    return res.data.data;
  },
  async adminGet(id: number): Promise<AdminNews> {
    const res = await axiosClient.get<ApiResponse<AdminNews>>(`/admin/news/${id}`);
    return res.data.data;
  },
  async adminCreate(payload: NewsPayload): Promise<AdminNews> {
    const res = await axiosClient.post<ApiResponse<AdminNews>>('/admin/news', payload);
    return res.data.data;
  },
  async adminUpdate(id: number, payload: NewsPayload): Promise<AdminNews> {
    const res = await axiosClient.put<ApiResponse<AdminNews>>(`/admin/news/${id}`, payload);
    return res.data.data;
  },
  async adminDelete(id: number): Promise<void> {
    await axiosClient.delete<ApiResponse<void>>(`/admin/news/${id}`);
  },
};
