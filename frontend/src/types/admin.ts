import type { RoleName } from './auth';

/** Khớp DashboardMetricsResponse của backend */
export interface DashboardMetrics {
  totalRevenue: number;
  todayRevenue: number;
  totalOrders: number;
  todayOrders: number;
  pendingOrders: number;
  processingOrders: number;
  deliveredOrders: number;
  cancelledOrders: number;
  successRate: number;
  totalUsers: number;
  customerCount: number;
  staffCount: number;
  shipperCount: number;
}

/** Khớp DailyRevenueResponse */
export interface DailyRevenue {
  /** yyyy-MM-dd */
  date: string;
  revenue: number;
  orderCount: number;
}

/** Khớp OrderStatusStatResponse */
export interface OrderStatusStat {
  status: string;
  statusLabel: string;
  count: number;
  percentage: number;
  totalAmount: number;
}

export interface AdminUser {
  id: number;
  email: string;
  fullName: string;
  phone?: string;
  image?: string;
  role: RoleName;
  banned: boolean;
  storeId?: number | null;
  storeName?: string | null;
  createdAt: string;
}

export interface AdminCreateUserPayload {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
  role: RoleName;
  storeId?: number | null;
}

export interface AdminUpdateUserPayload {
  fullName: string;
  phone?: string;
  role?: RoleName;
  banned?: boolean;
  password?: string;
  storeId?: number | null;
}

export interface UserFilterParams {
  role?: RoleName;
  banned?: boolean;
  keyword?: string;
  storeId?: number;
  page?: number;
  size?: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/** Một dòng bảng "món bán chạy" từ /admin/reports/top-products */
export interface TopProduct {
  /** null khi món đã bị xoá cứng — tên vẫn hiện theo snapshot lúc bán */
  productId: number | null;
  productName: string;
  quantitySold: number;
  revenue: number;
}

/** Loại báo cáo xuất CSV — khớp enum ReportType ở backend */
export type ReportType = 'TOP_PRODUCTS' | 'REVENUE_BY_DAY' | 'REVENUE_BY_CATEGORY' | 'REVENUE_BY_SHIPPER' | 'REVENUE_BY_STORE';

export interface ReportFilterParams {
  /** yyyy-MM-dd; bỏ trống = backend tự lấy 30 ngày gần nhất */
  fromDate?: string;
  toDate?: string;
  limit?: number;
  storeId?: number;
}
