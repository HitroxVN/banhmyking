import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/useAuth';
import { LoadingScreen } from '../ui';
import { roleHomePath } from './navItems';

/**
 * Chặn khách chưa đăng nhập. Dùng làm layout route: `<Route element={<RequireAuth />}>`.
 * Tài khoản vận hành (ADMIN/STAFF/SHIPPER) bị đẩy về đúng phân hệ của mình.
 */
export const RequireAuth = () => {
  const { user, isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <LoadingScreen text="Đang kiểm tra thông tin tài khoản..." full />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (user && user.role !== 'CUSTOMER') {
    return <Navigate to={roleHomePath(user.role)} replace />;
  }

  return <Outlet />;
};
