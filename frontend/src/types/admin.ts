import type { RoleName } from './auth';

export interface DashboardMetrics {
  totalRevenue: number;
  todayRevenue: number;
  totalOrders: number;
  todayOrders: number;
  pendingOrders: number;
  processingOrders: number;
  deliveredOrders: number;
  cancelledOrders: number;
  orderSuccessRate: number;
  totalUsers: number;
  activeUsers: number;
  bannedUsers: number;
  customerCount: number;
  staffCount: number;
  shipperCount: number;
  adminCount: number;
}

export interface DailyRevenue {
  date: string;
  dayOfWeek: string;
  revenue: number;
  orderCount: number;
}

export interface OrderStatusStat {
  status: string;
  label: string;
  count: number;
  percentage: number;
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
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
