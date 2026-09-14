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

  // Admin không tham gia mua hàng, tự động chuyển thẳng về Admin Dashboard
  if (user?.role === 'ADMIN') {
    return <Navigate to="/admin" replace />;
  }

  return <>{children}</>;
};
