import React from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/useAuth';

interface StaffLayoutProps {
  children: React.ReactNode;
  title: string;
  subtitle?: string;
  onRefresh?: () => void;
  isRefreshing?: boolean;
}

export const StaffLayout: React.FC<StaffLayoutProps> = ({
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
    if (window.confirm('Bạn có chắc chắn muốn đăng xuất khỏi ca làm việc?')) {
      await logout();
      navigate('/login');
    }
  };

  return (
    <div className="staff-root-container">
      {/* Sidebar Navigation */}
      <aside className="staff-sidebar">
        <div className="staff-sidebar-header">
          <div className="staff-logo-badge">👨‍🍳</div>
          <div className="staff-brand-info">
            <span className="staff-brand-name">BÁNH MỲ KING</span>
            <span className="staff-brand-badge">BẾP & ĐIỀU PHỐI (STAFF)</span>
          </div>
        </div>

        <div className="staff-sidebar-nav-section">
          <div className="staff-nav-group-title">QUẢN LÝ VẬN HÀNH</div>

          <NavLink
            to="/staff"
            end
            className={({ isActive }) =>
              `staff-nav-item ${isActive || location.pathname === '/staff/orders' ? 'active' : ''}`
            }
          >
            <span className="staff-nav-icon">📋</span>
            <span className="staff-nav-label">Hàng đợi Đơn hàng (POS)</span>
          </NavLink>

          <NavLink
            to="/staff/menu"
            className={({ isActive }) =>
              `staff-nav-item ${isActive ? 'active' : ''}`
            }
          >
            <span className="staff-nav-icon">🥖</span>
            <span className="staff-nav-label">Quản lý Thực đơn (Menu)</span>
          </NavLink>
        </div>

        {/* Sidebar Footer User Info */}
        <div className="staff-sidebar-footer">
          <div className="staff-user-card">
            <div className="staff-avatar">
              {user?.fullName?.charAt(0)?.toUpperCase() || 'S'}
            </div>
            <div className="staff-user-details">
              <span className="staff-user-name" title={user?.fullName}>
                {user?.fullName || 'Nhân viên'}
              </span>
              <span className="staff-user-role">👨‍🍳 {user?.role || 'STAFF'}</span>
            </div>
          </div>
          <button
            type="button"
            className="staff-logout-btn"
            onClick={handleLogout}
            title="Đăng xuất ca làm việc"
          >
            🚪 Thoát
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="staff-main-wrapper">
        {/* Top Header Bar */}
        <header className="staff-topbar">
          <div className="staff-header-title-area">
            <h1 className="staff-page-title">{title}</h1>
            {subtitle && <p className="staff-page-subtitle">{subtitle}</p>}
          </div>

          <div className="staff-header-actions">
            {/* Realtime Pulse */}
            <div className="staff-realtime-badge" title="Đồng bộ thời gian thực với máy chủ">
              <span className="pulse-dot"></span>
              <span className="realtime-text">Realtime Kitchen Sync</span>
            </div>

            {onRefresh && (
              <button
                type="button"
                className="staff-refresh-btn"
                onClick={onRefresh}
                disabled={isRefreshing}
                title="Làm mới dữ liệu ngay lập tức"
              >
                <span className={`refresh-icon ${isRefreshing ? 'spinning' : ''}`}>🔄</span>
                <span>{isRefreshing ? 'Đang tải...' : 'Làm mới'}</span>
              </button>
            )}
          </div>
        </header>

        {/* Dynamic Page Content */}
        <main className="staff-content-body">{children}</main>
      </div>
    </div>
  );
};
