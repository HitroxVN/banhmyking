import type { BadgeTone } from '../components/ui';
import type {
  ApplicationStatus,
  EmploymentType,
  FeedbackStatus,
  FeedbackType,
  JobStatus,
  NewsDisplayState,
  StoreRef,
} from '../types/content';
import { formatDate } from './formatters';

export const NEWS_STATE_LABEL: Record<NewsDisplayState, string> = {
  DRAFT: 'Nháp',
  SCHEDULED: 'Hẹn giờ',
  PUBLISHED: 'Đã đăng',
};

export const NEWS_STATE_TONE: Record<NewsDisplayState, BadgeTone> = {
  DRAFT: 'neutral',
  SCHEDULED: 'info',
  PUBLISHED: 'success',
};

export const EMPLOYMENT_TYPE_LABEL: Record<EmploymentType, string> = {
  FULL_TIME: 'Toàn thời gian',
  PART_TIME: 'Bán thời gian',
  SEASONAL: 'Thời vụ',
};

export const JOB_STATUS_LABEL: Record<JobStatus, string> = {
  OPEN: 'Đang mở',
  CLOSED: 'Đã đóng',
};

export const APPLICATION_STATUS_LABEL: Record<ApplicationStatus, string> = {
  NEW: 'Mới',
  CONTACTED: 'Đã liên hệ',
  HIRED: 'Đã nhận',
  REJECTED: 'Từ chối',
};

export const APPLICATION_STATUS_TONE: Record<ApplicationStatus, BadgeTone> = {
  NEW: 'warning',
  CONTACTED: 'info',
  HIRED: 'success',
  REJECTED: 'neutral',
};

export const FEEDBACK_TYPE_LABEL: Record<FeedbackType, string> = {
  SUGGESTION: 'Góp ý',
  COMPLAINT: 'Khiếu nại',
  PARTNERSHIP: 'Hợp tác',
  OTHER: 'Khác',
};

export const FEEDBACK_STATUS_LABEL: Record<FeedbackStatus, string> = {
  NEW: 'Mới',
  IN_PROGRESS: 'Đang xử lý',
  RESOLVED: 'Đã xử lý',
};

export const FEEDBACK_STATUS_TONE: Record<FeedbackStatus, BadgeTone> = {
  NEW: 'warning',
  IN_PROGRESS: 'info',
  RESOLVED: 'success',
};

/** "Toàn chuỗi" hoặc tên các cơ sở, cách nhau bởi dấu phẩy */
export const storesText = (chainWide: boolean, stores: StoreRef[]): string =>
  chainWide ? 'Toàn chuỗi' : stores.map((store) => store.name).join(', ');

/** Hạn nộp dd/MM/yyyy; null = không giới hạn */
export const deadlineText = (deadline?: string | null): string =>
  deadline ? formatDate(deadline) : 'Không giới hạn';

/** Cùng quy tắc với trang đăng ký và backend ContactFields */
export const VN_PHONE_REGEX = /^(0|\+84)(3|5|7|8|9)[0-9]{8}$/;
export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const normalizePhone = (raw: string): string => raw.replace(/[\s.-]/g, '');
