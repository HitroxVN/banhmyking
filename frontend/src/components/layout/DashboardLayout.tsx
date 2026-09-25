import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import { useAuth } from '../../context/useAuth';
import { useConfirm } from '../ui';
import type { BrandConfig, NavItem } from './navItems';

export interface DashboardLayoutProps {
  navItems: NavItem[];
  brand: BrandConfig;
}

/**
 * Khung vận hành dùng chung cho Staff / Shipper / Admin.
 * Thay cho 3 file layout gần như trùng nhau trước đây.
 */
export const DashboardLayout = ({ navItems, brand }: DashboardLayoutProps) => {
  const { user, logout } = useAuth();
  const confirm = useConfirm();
  const navigate = useNavigate();
  const BrandIcon = brand.icon;

  const handleLogout = async () => {
    const accepted = await confirm({
      title: 'Đăng xuất',
      message: 'Bạn có chắc chắn muốn kết thúc ca làm việc và đăng xuất?',
      confirmText: 'Đăng xuất',
      danger: true,
    });
    if (accepted) {
      await logout();
      navigate('/login');
    }
  };

  return (
    <div className="dash">
      <aside className="dash__sidebar">
        <div className="dash__brand">
          <span className="dash__brand-badge">
            <BrandIcon size={22} />
          </span>
          <span className="dash__brand-text">
            <span className="dash__brand-name">{brand.name}</span>
            <span className="dash__brand-sub">{brand.sub}</span>
          </span>
        </div>

        <nav className="dash__nav">
          <div className="dash__nav-title">{brand.navTitle}</div>
          {navItems.map((item) => {
            const ItemIcon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => `dash__nav-item${isActive ? ' active' : ''}`}
              >
                <span className="dash__nav-icon">
                  <ItemIcon size={19} />
                </span>
                <span className="dash__nav-label">{item.label}</span>
              </NavLink>
            );
          })}
        </nav>

        <div className="dash__user">
          <div className="dash__user-row">
            <span className="dash__avatar">{user?.fullName?.charAt(0)?.toUpperCase() ?? 'K'}</span>
            <span className="dash__user-text">
              <span className="dash__user-name" title={user?.fullName}>
                {user?.fullName ?? brand.roleLabel}
              </span>
              <span className="dash__user-role">{brand.roleLabel}</span>
            </span>
          </div>
          <button type="button" className="dash__logout" onClick={handleLogout}>
            <LogOut size={16} />
            Thoát ca làm việc
          </button>
        </div>
      </aside>

      <div className="dash__main">
        <main className="dash__content">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
