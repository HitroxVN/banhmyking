/** Khớp DTO backend dto/news, dto/job, dto/feedback (dự án con D). Thời điểm là giờ Việt Nam, không có múi giờ. */

export type NewsStatus = 'DRAFT' | 'PUBLISHED';
/** Suy ra từ status + publishedAt: PUBLISHED có publishedAt ở tương lai = SCHEDULED */
export type NewsDisplayState = 'DRAFT' | 'SCHEDULED' | 'PUBLISHED';

export interface NewsSummary {
  id: number;
  title: string;
  slug: string;
  coverImageUrl?: string | null;
  summary?: string | null;
  publishedAt: string;
  pinned: boolean;
}

export interface NewsDetail {
  id: number;
  title: string;
  slug: string;
  coverImageUrl?: string | null;
  summary?: string | null;
  /** Markdown */
  content: string;
  publishedAt: string;
  related: NewsSummary[];
}

export interface AdminNews {
  id: number;
  title: string;
  slug: string;
  coverImageUrl?: string | null;
  summary?: string | null;
  /** null trong danh sách — gọi newsApi.adminGet để lấy nội dung */
  content?: string | null;
  status: NewsStatus;
  displayState: NewsDisplayState;
  publishedAt?: string | null;
  pinned: boolean;
  authorName?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface NewsPayload {
  title: string;
  /** Bỏ trống: tạo → sinh từ tiêu đề; sửa → giữ slug cũ */
  slug?: string | null;
  coverImageUrl?: string | null;
  summary?: string | null;
  content: string;
  status: NewsStatus;
  /** Giá trị ô datetime-local (yyyy-MM-ddTHH:mm, giờ Việt Nam); null + PUBLISHED = đăng ngay */
  publishedAt?: string | null;
  pinned: boolean;
}

export interface AdminNewsFilter {
  status?: NewsDisplayState;
  keyword?: string;
  page?: number;
  size?: number;
}

export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'SEASONAL';
export type JobStatus = 'OPEN' | 'CLOSED';

export interface StoreRef {
  id: number;
  code: string;
  name: string;
}

export interface JobSummary {
  id: number;
  title: string;
  slug: string;
  employmentType: EmploymentType;
  salaryText?: string | null;
  headcount?: number | null;
  /** yyyy-MM-dd; null = không hạn */
  deadline?: string | null;
  chainWide: boolean;
  /** Cơ sở đang nhận hồ sơ */
  stores: StoreRef[];
  acceptingApplications: boolean;
}

export interface JobDetail extends JobSummary {
  /** Markdown */
  description: string;
}

export interface AdminJob {
  id: number;
  title: string;
  slug: string;
  employmentType: EmploymentType;
  salaryText?: string | null;
  headcount?: number | null;
  deadline?: string | null;
  description: string;
  status: JobStatus;
  expired: boolean;
  chainWide: boolean;
  /** Cơ sở đã cấu hình; rỗng = toàn chuỗi */
  stores: StoreRef[];
  createdAt: string;
}

export interface JobPayload {
  title: string;
  slug?: string | null;
  employmentType: EmploymentType;
  salaryText?: string | null;
  headcount?: number | null;
  deadline?: string | null;
  description: string;
  status: JobStatus;
  /** Rỗng = tuyển toàn chuỗi */
  storeIds: number[];
}

export interface AdminJobFilter {
  status?: JobStatus;
  keyword?: string;
  page?: number;
  size?: number;
}

export type ApplicationStatus = 'NEW' | 'CONTACTED' | 'HIRED' | 'REJECTED';

export interface JobApplication {
  id: number;
  jobId: number;
  jobTitle: string;
  jobSlug: string;
  storeId: number;
  storeName: string;
  fullName: string;
  phone: string;
  email?: string | null;
  message?: string | null;
  hasCv: boolean;
  cvOriginalName?: string | null;
  status: ApplicationStatus;
  internalNote?: string | null;
  handledByName?: string | null;
  handledAt?: string | null;
  createdAt: string;
}

export interface ApplicationForm {
  storeId: number | null;
  fullName: string;
  phone: string;
  email: string;
  message: string;
  /** Ô bẫy bot — người thật luôn để trống */
  website: string;
}

export interface ApplicationFilter {
  jobId?: number;
  storeId?: number;
  status?: ApplicationStatus;
  page?: number;
  size?: number;
}

export interface ApplicationUpdate {
  status?: ApplicationStatus;
  internalNote?: string;
}

export type FeedbackType = 'SUGGESTION' | 'COMPLAINT' | 'PARTNERSHIP' | 'OTHER';
export type FeedbackStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED';

export interface FeedbackPayload {
  type: FeedbackType;
  storeId?: number | null;
  /** Chỉ gửi khi đăng nhập và là đơn của chính mình */
  orderCode?: string | null;
  fullName: string;
  phone?: string | null;
  email?: string | null;
  subject: string;
  content: string;
  /** Ô bẫy bot */
  website: string;
}

export interface Feedback {
  id: number;
  type: FeedbackType;
  /** null = chung toàn chuỗi */
  storeId?: number | null;
  storeName?: string | null;
  orderCode?: string | null;
  userId?: number | null;
  fullName: string;
  phone?: string | null;
  email?: string | null;
  subject: string;
  content: string;
  status: FeedbackStatus;
  resolutionNote?: string | null;
  handledByName?: string | null;
  handledAt?: string | null;
  createdAt: string;
}

export interface FeedbackFilter {
  type?: FeedbackType;
  storeId?: number;
  status?: FeedbackStatus;
  page?: number;
  size?: number;
}

export interface FeedbackUpdate {
  status?: FeedbackStatus;
  resolutionNote?: string;
}
