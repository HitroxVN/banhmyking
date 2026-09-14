import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/useAuth';

export const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user, isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="auth-loading-screen">
        <div className="spinner-royal"></div>
        <p className="loading-text">Đang kiểm tra thông tin tài khoản...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Admin, Staff và Shipper không tham gia luồng mua hàng thông thường, tự động chuyển về portal tương ứng
  if (user?.role === 'ADMIN') {
    return <Navigate to="/admin" replace />;
  }
  if (user?.role === 'STAFF') {
    return <Navigate to="/staff" replace />;
  }
  if (user?.role === 'SHIPPER') {
    return <Navigate to="/shipper" replace />;
  }

  return <>{children}</>;
};
