import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/useAuth';

interface ShipperLayoutProps {
  children: React.ReactNode;
  title: string;
  subtitle?: string;
  onRefresh?: () => void;
  isRefreshing?: boolean;
}

export const ShipperLayout: React.FC<ShipperLayoutProps> = ({
  children,
  title,
  subtitle,
  onRefresh,
  isRefreshing = false,
}) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    if (window.confirm('Bạn có chắc chắn muốn kết thúc ca làm việc và đăng xuất?')) {
      await logout();
      navigate('/login');
    }
  };

  return (
    <div className="shipper-root-container">
      {/* Sidebar Navigation */}
      <aside className="shipper-sidebar">
        <div className="shipper-sidebar-header">
          <div className="shipper-logo-badge">🛵</div>
          <div className="shipper-brand-info">
            <span className="shipper-brand-name">BÁNH MỲ KING</span>
            <span className="shipper-brand-badge">ĐIỀU PHỐI SHIPPER</span>
          </div>
        </div>

        <div className="shipper-sidebar-nav-section">
          <div className="shipper-nav-group-title">QUẢN LÝ GIAO HÀNG</div>

          <NavLink
            to="/shipper"
            end
            className={({ isActive }) =>
              `shipper-nav-item ${isActive ? 'active' : ''}`
            }
          >
            <span className="shipper-nav-icon">📦</span>
            <span className="shipper-nav-label">Đơn Hàng Giao Nhận</span>
          </NavLink>
        </div>

        {/* Sidebar Footer User Info */}
        <div className="shipper-sidebar-footer">
          <div className="shipper-user-card">
            <div className="shipper-avatar">
              {user?.fullName?.charAt(0)?.toUpperCase() || 'S'}
            </div>
            <div className="shipper-user-details">
              <span className="shipper-user-name" title={user?.fullName}>
                {user?.fullName || 'Tài xế'}
              </span>
              <span className="shipper-user-role">🛵 {user?.role || 'SHIPPER'}</span>
            </div>
          </div>
          <button
            type="button"
            className="shipper-logout-btn-sidebar"
            onClick={handleLogout}
            title="Đăng xuất ca làm việc"
          >
            🚪 Đăng xuất
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="shipper-main-wrapper">
        {/* Top Header Bar */}
        <header className="shipper-topbar">
          <div className="shipper-header-title-area">
            <h1 className="shipper-page-title">{title}</h1>
            {subtitle && <p className="shipper-page-subtitle">{subtitle}</p>}
          </div>

          <div className="shipper-header-actions">
            {/* Realtime Pulse */}
            <div className="shipper-realtime-badge" title="Đồng bộ thời gian thực với hệ thống">
              <span className="pulse-dot"></span>
              <span className="realtime-text">Realtime Dispatcher Sync</span>
            </div>

            {onRefresh && (
              <button
                type="button"
                id="shipper-btn-web-refresh"
                className="shipper-refresh-btn"
                onClick={onRefresh}
                disabled={isRefreshing}
                title="Làm mới danh sách đơn hàng"
              >
                <span className={`refresh-icon ${isRefreshing ? 'spinning' : ''}`}>🔄</span>
                <span>{isRefreshing ? 'Đang cập nhật...' : 'Làm mới'}</span>
              </button>
            )}
          </div>
        </header>

        {/* Dynamic Page Content */}
        <main className="shipper-content-body">{children}</main>
      </div>
    </div>
  );
};
