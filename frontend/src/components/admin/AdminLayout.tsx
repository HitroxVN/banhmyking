import React from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/useAuth';

interface AdminLayoutProps {
  children: React.ReactNode;
  title: string;
  subtitle?: string;
  onRefresh?: () => void;
  isRefreshing?: boolean;
}

export const AdminLayout: React.FC<AdminLayoutProps> = ({
  children,
  title,
  subtitle,
  onRefresh,
  isRefreshing = false,
}) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = async () => {
    if (window.confirm('Bạn có chắc chắn muốn đăng xuất khỏi tài khoản Quản trị viên?')) {
      await logout();
      navigate('/login');
    }
  };

  return (
    <div className="admin-root-container">
      {/* Sidebar Navigation */}
      <aside className="admin-sidebar">
        <div className="admin-sidebar-header">
          <div className="admin-logo-badge">🥖</div>
          <div className="admin-brand-info">
            <span className="admin-brand-name">BÁNH MỲ KING</span>
            <span className="admin-brand-badge">ADMIN CONSOLE</span>
          </div>
        </div>

        <div className="admin-sidebar-nav-section">
          <div className="admin-nav-group-title">QUẢN TRỊ HỆ THỐNG</div>

          <NavLink
            to="/admin"
            end
            className={({ isActive }) =>
              `admin-nav-item ${isActive || location.pathname === '/admin/dashboard' ? 'active' : ''}`
            }
          >
            <span className="admin-nav-icon">📊</span>
            <span className="admin-nav-label">Tổng quan & Doanh thu</span>
          </NavLink>

          <NavLink
            to="/admin/users"
            className={({ isActive }) =>
              `admin-nav-item ${isActive ? 'active' : ''}`
            }
          >
            <span className="admin-nav-icon">👥</span>
            <span className="admin-nav-label">Quản lý Tài khoản & Phân quyền</span>
          </NavLink>
        </div>

        {/* Sidebar Footer User Info */}
        <div className="admin-sidebar-footer">
          <div className="admin-user-card">
            <div className="admin-avatar">
              {user?.fullName?.charAt(0)?.toUpperCase() || 'A'}
            </div>
            <div className="admin-user-details">
              <span className="admin-user-name" title={user?.fullName}>
                {user?.fullName || 'Quản trị viên'}
              </span>
              <span className="admin-user-role">👑 ADMIN Tối Cao</span>
            </div>
          </div>
          <button
            type="button"
            className="admin-logout-btn"
            onClick={handleLogout}
            title="Đăng xuất"
          >
            🚪 Thoát
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="admin-main-wrapper">
        {/* Top Header Bar */}
        <header className="admin-topbar">
          <div className="admin-header-title-area">
            <h1 className="admin-page-title">{title}</h1>
            {subtitle && <p className="admin-page-subtitle">{subtitle}</p>}
          </div>

          <div className="admin-header-actions">
            {/* Realtime Pulse */}
            <div className="admin-realtime-badge" title="Dữ liệu kết nối trực tiếp với máy chủ">
              <span className="pulse-dot"></span>
              <span className="realtime-text">Realtime Active</span>
            </div>

            {onRefresh && (
              <button
                type="button"
                className="admin-refresh-btn"
                onClick={onRefresh}
                disabled={isRefreshing}
                title="Làm mới số liệu thống kê"
              >
                <span className={`refresh-icon ${isRefreshing ? 'spinning' : ''}`}>🔄</span>
                <span>{isRefreshing ? 'Đang tải...' : 'Làm mới'}</span>
              </button>
            )}
          </div>
        </header>

        {/* Dynamic Page Content */}
        <main className="admin-content-body">{children}</main>
      </div>
    </div>
  );
};
