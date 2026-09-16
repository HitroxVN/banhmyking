import React from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/useAuth';

export const ShipperRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user, isAuthenticated, isLoading } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  if (isLoading) {
    return (
      <div className="auth-loading-screen">
        <div className="spinner-royal"></div>
        <p className="loading-text">Đang xác minh quyền tài xế...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Cho phép SHIPPER và ADMIN (để giám sát / kiểm thử)
  if (user?.role !== 'SHIPPER' && user?.role !== 'ADMIN') {
    return (
      <div className="admin-forbidden-wrapper">
        <div className="admin-forbidden-card">
          <div className="forbidden-icon">🛵</div>
          <span className="forbidden-code">403 FORBIDDEN</span>
          <h1 className="forbidden-title">Khu Vực Tài Xế Giao Hàng</h1>
          <p className="forbidden-desc">
            Trang này chỉ dành riêng cho tài khoản <strong>Tài xế giao hàng (SHIPPER)</strong>.
            Tài khoản hiện tại của bạn là <strong>{user?.fullName}</strong> với vai trò <span className="role-chip">{user?.role}</span> không có quyền truy cập khu vực này.
          </p>
          <div className="forbidden-actions">
            <button
              type="button"
              className="btn-primary"
              onClick={() => {
                if (user?.role === 'STAFF') navigate('/staff');
                else navigate('/');
              }}
            >
              ← Về khu vực làm việc
            </button>
            <button
              type="button"
              className="btn-outline"
              onClick={() => navigate('/login')}
            >
              Đổi tài khoản khác
            </button>
          </div>
        </div>
      </div>
    );
  }

  return <>{children}</>;
};
