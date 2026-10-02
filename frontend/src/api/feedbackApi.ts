import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
import type { Feedback, FeedbackFilter, FeedbackPayload, FeedbackUpdate } from '../types/content';

export const feedbackApi = {
  /** Công khai; có token thì backend ghi nhận người gửi (bắt buộc khi gắn đơn). Trả câu thông báo. */
  async submit(payload: FeedbackPayload): Promise<string> {
    const res = await axiosClient.post<ApiResponse<void>>('/feedbacks', payload);
    return res.data.message ?? 'Đã gửi phản hồi.';
  },
  async adminList(filter: FeedbackFilter = {}): Promise<PageResponse<Feedback>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<Feedback>>>('/admin/feedbacks', { params: filter });
    return res.data.data;
  },
  async adminGet(id: number): Promise<Feedback> {
    const res = await axiosClient.get<ApiResponse<Feedback>>(`/admin/feedbacks/${id}`);
    return res.data.data;
  },
  async adminUpdate(id: number, update: FeedbackUpdate): Promise<Feedback> {
    const res = await axiosClient.patch<ApiResponse<Feedback>>(`/admin/feedbacks/${id}`, update);
    return res.data.data;
  },
  async countNew(): Promise<number> {
    const res = await axiosClient.get<ApiResponse<number>>('/admin/feedbacks/count-new');
    return res.data.data;
  },
};
