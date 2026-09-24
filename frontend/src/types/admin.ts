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
  createdAt: string;
}

export interface AdminCreateUserPayload {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
  role: RoleName;
}

export interface AdminUpdateUserPayload {
  fullName: string;
  phone?: string;
  role?: RoleName;
  banned?: boolean;
  password?: string;
}

export interface UserFilterParams {
  role?: RoleName;
  banned?: boolean;
  keyword?: string;
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
