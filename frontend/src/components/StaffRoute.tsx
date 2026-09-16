import React from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/useAuth';

export const StaffRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user, isAuthenticated, isLoading } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  if (isLoading) {
    return (
      <div className="auth-loading-screen">
        <div className="spinner-royal"></div>
        <p className="loading-text">Đang xác minh quyền nhân viên...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Cho phép cả STAFF và ADMIN truy cập phân hệ nhân viên
  if (user?.role !== 'STAFF' && user?.role !== 'ADMIN') {
    return (
      <div className="admin-forbidden-wrapper">
        <div className="admin-forbidden-card">
          <div className="forbidden-icon">👨‍🍳</div>
          <span className="forbidden-code">403 FORBIDDEN</span>
          <h1 className="forbidden-title">Khu Vực Bếp & Nhân Viên</h1>
          <p className="forbidden-desc">
            Trang này chỉ dành riêng cho tài khoản <strong>Nhân viên cửa hàng (STAFF)</strong> và Quản trị viên.
            Tài khoản hiện tại của bạn là <strong>{user?.fullName}</strong> với vai trò <span className="role-chip">{user?.role}</span> không có quyền truy cập khu vực này.
          </p>
          <div className="forbidden-actions">
            <button
              type="button"
              className="btn-primary"
              onClick={() => navigate('/')}
            >
              ← Về Trang chủ
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
