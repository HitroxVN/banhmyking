import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { LogOut, PauseCircle, PlayCircle } from 'lucide-react';
import { storeApi } from '../../api/storeApi';
import { useAuth } from '../../context/useAuth';
import { useSiteSettings } from '../../context/useSiteSettings';
import { useStoreScope } from '../../context/useStoreScope';
import { Button, useConfirm, useToast } from '../ui';
import { StoreScopeSelect } from '../store/StoreScopeSelect';
import type { BrandConfig, NavItem } from './navItems';

export interface DashboardLayoutProps {
  navItems: NavItem[];
  brand: BrandConfig;
  /** Hiện cơ sở đang làm việc (và nút tạm ngưng nhận đơn cho MANAGER/ADMIN) */
  showStore?: boolean;
}

/**
 * Khung vận hành dùng chung cho Staff / Shipper / Admin.
 * Thay cho 3 file layout gần như trùng nhau trước đây.
 */
export const DashboardLayout = ({ navItems, brand, showStore = false }: DashboardLayoutProps) => {
  const { user, logout } = useAuth();
  const { settings } = useSiteSettings();
  const confirm = useConfirm();
  const navigate = useNavigate();
  const scope = useStoreScope();
  const toast = useToast();
  const [accepting, setAccepting] = useState(true);
  const BrandIcon = brand.icon;

  useEffect(() => {
    if (!showStore || scope.storeId == null) return;
    let alive = true;
    storeApi
      .listPublic()
      .then((stores) => {
        if (alive) setAccepting(stores.find((s) => s.id === scope.storeId)?.acceptingOrders ?? true);
      })
      .catch(() => {
        // Không tải được trạng thái thì giữ mặc định, nút vẫn dùng được
      });
    return () => {
      alive = false;
    };
  }, [showStore, scope.storeId]);

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
            <span className="dash__brand-name">{settings.siteName || brand.name}</span>
            <span className="dash__brand-sub">{brand.sub}</span>
          </span>
        </div>

        {showStore && (
          <div className="dash__store">
            {scope.canChoose ? (
              <StoreScopeSelect
                label="Cơ sở đang xem"
                allowAll={false}
                value={scope.storeId}
                onChange={(id) => scope.setStore(id, null)}
              />
            ) : (
              <span className="dash__store-name">{scope.storeName ?? 'Chưa được gán cơ sở'}</span>
            )}
          </div>
        )}

        {showStore && (user?.role === 'MANAGER' || user?.role === 'ADMIN') && scope.storeId != null && (
          <div className="dash__store">
            <Button
              size="sm"
              variant={accepting ? 'danger' : 'success'}
              icon={accepting ? <PauseCircle size={16} /> : <PlayCircle size={16} />}
              onClick={async () => {
                try {
                  const store = await storeApi.setAccepting(scope.storeId as number, !accepting);
                  setAccepting(store.acceptingOrders);
                  toast.success(store.acceptingOrders ? 'Đã mở lại nhận đơn' : 'Đã tạm ngưng nhận đơn');
                } catch (err) {
                  toast.error(err instanceof Error ? err.message : 'Thao tác thất bại');
                }
              }}
            >
              {accepting ? 'Tạm ngưng nhận đơn' : 'Mở lại nhận đơn'}
            </Button>
          </div>
        )}

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
            {user?.image ? (
              <img className="dash__avatar" src={user.image} alt="" />
            ) : (
              <span className="dash__avatar">{user?.fullName?.charAt(0)?.toUpperCase() ?? 'K'}</span>
            )}
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
