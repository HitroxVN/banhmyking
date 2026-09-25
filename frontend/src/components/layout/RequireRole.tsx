import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import { useAuth } from '../../context/useAuth';
import { Button, LoadingScreen } from '../ui';
import { roleHomePath } from './navItems';
import type { RoleName } from '../../types/auth';

export interface RequireRoleProps {
  /** Những vai trò được phép vào phân hệ này */
  roles: RoleName[];
  /** Mô tả ngắn khu vực, hiện trong thông báo 403 */
  area: string;
  /** Cảnh báo khi đang xác minh quyền */
  loadingText?: string;
}

/** Chặn theo vai trò — gộp AdminRoute / StaffRoute / ShipperRoute cũ thành một. */
export const RequireRole = ({ roles, area, loadingText = 'Đang xác minh quyền truy cập...' }: RequireRoleProps) => {
  const { user, isAuthenticated, isLoading } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  if (isLoading) {
    return <LoadingScreen text={loadingText} full />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (!user || !roles.includes(user.role)) {
    return (
      <div className="u403">
        <div className="u403__card">
          <span className="u403__icon">
            <ShieldAlert size={28} />
          </span>
          <span className="u403__code">403 FORBIDDEN</span>
          <h1 className="u403__title">Quyền truy cập bị từ chối</h1>
          <p className="u403__desc">
            Khu vực <strong>{area}</strong> chỉ dành cho tài khoản {roles.join(' / ')}. Tài khoản{' '}
            <strong>{user?.fullName}</strong> đang có vai trò <strong>{user?.role}</strong> nên không thể truy cập.
          </p>
          <div className="u403__actions">
            <Button variant="primary" onClick={() => navigate(roleHomePath(user?.role))}>
              Về khu vực làm việc
            </Button>
            <Button variant="secondary" onClick={() => navigate('/login')}>
              Đổi tài khoản khác
            </Button>
          </div>
        </div>
      </div>
    );
  }

  return <Outlet />;
};
