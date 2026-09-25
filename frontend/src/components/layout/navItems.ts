import {
  Bike,
  ChefHat,
  ClipboardList,
  Crown,
  FolderTree,
  LayoutDashboard,
  Users,
  UtensilsCrossed,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { RoleName } from '../../types/auth';

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Khớp chính xác đường dẫn (dùng cho route gốc của phân hệ) */
  end?: boolean;
}

export interface BrandConfig {
  /** Tên thương hiệu trên sidebar */
  name: string;
  /** Dòng phụ mô tả phân hệ */
  sub: string;
  icon: LucideIcon;
  /** Nhãn vai trò hiện dưới tên người dùng */
  roleLabel: string;
  /** Tiêu đề nhóm menu */
  navTitle: string;
}

export const STAFF_BRAND: BrandConfig = {
  name: 'BÁNH MỲ KING',
  sub: 'BẾP & ĐIỀU PHỐI (STAFF)',
  icon: ChefHat,
  roleLabel: 'Nhân viên cửa hàng',
  navTitle: 'QUẢN LÝ VẬN HÀNH',
};

export const STAFF_NAV: NavItem[] = [
  { to: '/staff/orders', label: 'Hàng đợi Đơn hàng (POS)', icon: ClipboardList },
  { to: '/staff/menu', label: 'Quản lý Thực đơn (Menu)', icon: UtensilsCrossed },
];

export const SHIPPER_BRAND: BrandConfig = {
  name: 'BÁNH MỲ KING',
  sub: 'ĐIỀU PHỐI SHIPPER',
  icon: Bike,
  roleLabel: 'Tài xế giao hàng',
  navTitle: 'QUẢN LÝ GIAO HÀNG',
};

export const SHIPPER_NAV: NavItem[] = [
  { to: '/shipper', label: 'Đơn Hàng Giao Nhận', icon: Bike, end: true },
];

export const ADMIN_BRAND: BrandConfig = {
  name: 'BÁNH MỲ KING',
  sub: 'ADMIN CONSOLE',
  icon: Crown,
  roleLabel: 'Quản trị viên',
  navTitle: 'QUẢN TRỊ HỆ THỐNG',
};

export const ADMIN_NAV: NavItem[] = [
  { to: '/admin/dashboard', label: 'Tổng quan', icon: LayoutDashboard },
  { to: '/admin/orders', label: 'Đơn hàng', icon: ClipboardList },
  { to: '/admin/categories', label: 'Danh mục món', icon: FolderTree },
  { to: '/admin/users', label: 'Tài khoản', icon: Users },
];

/** Trang chủ của từng vai trò — dùng cho redirect sau đăng nhập / trang 403 */
export const roleHomePath = (role: RoleName | undefined): string => {
  switch (role) {
    case 'ADMIN':
      return '/admin';
    case 'STAFF':
      return '/staff';
    case 'SHIPPER':
      return '/shipper';
    default:
      return '/';
  }
};
