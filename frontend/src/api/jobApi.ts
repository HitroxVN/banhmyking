import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
import type {
  AdminJob,
  AdminJobFilter,
  ApplicationFilter,
  ApplicationForm,
  ApplicationUpdate,
  JobApplication,
  JobDetail,
  JobPayload,
  JobStatus,
  JobSummary,
} from '../types/content';

/** Tải lên/xuống tệp lớn: nới timeout mặc định 15s */
const UPLOAD_TIMEOUT_MS = 120_000;

export const jobApi = {
  /** Tin đang tuyển; storeId lọc tin của cơ sở đó + tin toàn chuỗi */
  async listOpen(storeId?: number | null): Promise<JobSummary[]> {
    const res = await axiosClient.get<ApiResponse<JobSummary[]>>('/jobs', {
      params: storeId ? { storeId } : {},
    });
    return res.data.data;
  },
  async getBySlug(slug: string): Promise<JobDetail> {
    const res = await axiosClient.get<ApiResponse<JobDetail>>(`/jobs/${encodeURIComponent(slug)}`);
    return res.data.data;
  },
  /** Nộp hồ sơ (multipart). Trả câu thông báo của backend. */
  async apply(slug: string, form: ApplicationForm, cv: File | null): Promise<string> {
    const data = new FormData();
    if (form.storeId != null) data.append('storeId', String(form.storeId));
    data.append('fullName', form.fullName);
    data.append('phone', form.phone);
    data.append('email', form.email);
    data.append('message', form.message);
    data.append('website', form.website);
    if (cv) data.append('cv', cv);
    // PHẢI set tường minh: axiosClient mặc định application/json (xem siteSettingsApi.uploadHeroImage)
    const res = await axiosClient.post<ApiResponse<void>>(`/jobs/${encodeURIComponent(slug)}/applications`, data, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: UPLOAD_TIMEOUT_MS, // CV tối đa 5MB trên mạng chậm — không bị cắt ở 15s mặc định
    });
    return res.data.message ?? 'Đã gửi hồ sơ ứng tuyển.';
  },
  async adminList(filter: AdminJobFilter = {}): Promise<PageResponse<AdminJob>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<AdminJob>>>('/admin/jobs', { params: filter });
    return res.data.data;
  },
  async adminGet(id: number): Promise<AdminJob> {
    const res = await axiosClient.get<ApiResponse<AdminJob>>(`/admin/jobs/${id}`);
    return res.data.data;
  },
  async adminCreate(payload: JobPayload): Promise<AdminJob> {
    const res = await axiosClient.post<ApiResponse<AdminJob>>('/admin/jobs', payload);
    return res.data.data;
  },
  async adminUpdate(id: number, payload: JobPayload): Promise<AdminJob> {
    const res = await axiosClient.put<ApiResponse<AdminJob>>(`/admin/jobs/${id}`, payload);
    return res.data.data;
  },
  async adminSetStatus(id: number, status: JobStatus): Promise<AdminJob> {
    const res = await axiosClient.patch<ApiResponse<AdminJob>>(`/admin/jobs/${id}/status`, { status });
    return res.data.data;
  },
  async adminDelete(id: number): Promise<void> {
    await axiosClient.delete<ApiResponse<void>>(`/admin/jobs/${id}`);
  },
  /** MANAGER bị backend ép về cơ sở của mình */
  async listApplications(filter: ApplicationFilter = {}): Promise<PageResponse<JobApplication>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<JobApplication>>>('/job-applications', {
      params: filter,
    });
    return res.data.data;
  },
  async getApplication(id: number): Promise<JobApplication> {
    const res = await axiosClient.get<ApiResponse<JobApplication>>(`/job-applications/${id}`);
    return res.data.data;
  },
  async updateApplication(id: number, update: ApplicationUpdate): Promise<JobApplication> {
    const res = await axiosClient.patch<ApiResponse<JobApplication>>(`/job-applications/${id}`, update);
    return res.data.data;
  },
  /** CV chỉ tải qua API có token — không có đường dẫn công khai */
  async downloadCv(application: JobApplication): Promise<void> {
    const res = await axiosClient.get<Blob>(`/job-applications/${application.id}/cv`, { responseType: 'blob', timeout: UPLOAD_TIMEOUT_MS });
    const url = URL.createObjectURL(res.data);
    const link = document.createElement('a');
    link.href = url;
    link.download = application.cvOriginalName || `cv-ho-so-${application.id}`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
  async countNewApplications(): Promise<number> {
    const res = await axiosClient.get<ApiResponse<number>>('/job-applications/count-new');
    return res.data.data;
  },
};
