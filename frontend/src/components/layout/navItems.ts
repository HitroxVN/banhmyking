import {
  BarChart3,
  Bike,
  ChefHat,
  ClipboardList,
  Crown,
  FolderTree,
  LayoutDashboard,
  LineChart,
  PackageCheck,
  Settings,
  Star,
  Store as StoreIcon,
  Ticket,
  Users,
  UsersRound,
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

/** ADMIN vào khu /staff: giữ khung bếp nhưng hiện đúng vai trò */
export const ADMIN_STAFF_BRAND: BrandConfig = {
  ...STAFF_BRAND,
  sub: 'BẾP & ĐIỀU PHỐI (ADMIN)',
  roleLabel: 'Quản trị viên',
};

export const STAFF_NAV: NavItem[] = [
  { to: '/staff/orders', label: 'Hàng đợi Đơn hàng (POS)', icon: ClipboardList },
  { to: '/staff/menu', label: 'Tình trạng món', icon: PackageCheck },
  { to: '/staff/reviews', label: 'Đánh giá của khách', icon: Star },
];

export const MANAGER_NAV: NavItem[] = [
  ...STAFF_NAV,
  { to: '/staff/reports', label: 'Báo cáo cơ sở', icon: BarChart3 },
  { to: '/staff/team', label: 'Nhân viên', icon: UsersRound },
];

export const MANAGER_BRAND: BrandConfig = {
  name: 'BÁNH MỲ KING',
  sub: 'QUẢN LÝ CƠ SỞ',
  icon: ChefHat,
  roleLabel: 'Quản lý cơ sở',
  navTitle: 'VẬN HÀNH CƠ SỞ',
};

/** Menu của khu /staff theo vai trò */
export const navForRole = (role: RoleName | undefined): NavItem[] => (role === 'MANAGER' ? MANAGER_NAV : STAFF_NAV);

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
  { to: '/admin/stores', label: 'Cơ sở', icon: StoreIcon },
  { to: '/admin/categories', label: 'Danh mục món', icon: FolderTree },
  { to: '/admin/menu', label: 'Thực đơn', icon: UtensilsCrossed },
  { to: '/admin/promotions', label: 'Mã giảm giá', icon: Ticket },
  { to: '/admin/reports', label: 'Báo cáo', icon: LineChart },
  { to: '/admin/reviews', label: 'Đánh giá', icon: Star },
  { to: '/admin/users', label: 'Tài khoản', icon: Users },
  { to: '/admin/settings', label: 'Cấu hình trang web', icon: Settings },
];

/** Trang chủ của từng vai trò — dùng cho redirect sau đăng nhập / trang 403 */
export const roleHomePath = (role: RoleName | undefined): string => {
  switch (role) {
    case 'ADMIN':
      return '/admin';
    case 'STAFF':
    case 'MANAGER':
      return '/staff';
    case 'SHIPPER':
      return '/shipper';
    default:
      // Khách vào thẳng thực đơn: `/` giờ là trang giới thiệu, không phải chỗ đặt món
      return '/menu';
  }
};
