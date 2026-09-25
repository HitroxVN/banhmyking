import { useNavigate } from 'react-router-dom';
import { Bike, ChevronRight, ChefHat, Crown, Users } from 'lucide-react';
import { Button } from '../ui';
import type { DashboardMetrics } from '../../types/admin';
import '../../styles/components/dashboard.css';

interface UserBreakdownCardProps {
  metrics: DashboardMetrics;
}

const ROLES = [
  { key: 'customerCount', label: 'Khách hàng', icon: <Users size={18} /> },
  { key: 'staffCount', label: 'Nhân viên', icon: <ChefHat size={18} /> },
  { key: 'shipperCount', label: 'Tài xế giao hàng', icon: <Bike size={18} /> },
] as const;

export const UserBreakdownCard = ({ metrics }: UserBreakdownCardProps) => {
  const navigate = useNavigate();
  // Backend không trả riêng số admin — suy ra từ tổng tài khoản trừ 3 vai trò còn lại
  const adminCount = Math.max(
    0,
    metrics.totalUsers - metrics.customerCount - metrics.staffCount - metrics.shipperCount
  );

  return (
    <section className="card">
      <div className="card__head">
        <div>
          <h3 className="chart__title">Cơ cấu người dùng</h3>
          <p className="chart__sub">Tổng {metrics.totalUsers} tài khoản trong hệ thống</p>
        </div>
        <Button
          size="sm"
          variant="secondary"
          icon={<ChevronRight size={16} />}
          onClick={() => navigate('/admin/users')}
        >
          Quản lý tài khoản
        </Button>
      </div>

      <div className="card__body">
        <div className="roles__grid">
          {ROLES.map((role) => (
            <div key={role.key} className="role-tile">
              <span className="role-tile__icon">{role.icon}</span>
              <span>
                <span className="role-tile__count">{metrics[role.key]}</span>
                <span className="role-tile__label">{role.label}</span>
              </span>
            </div>
          ))}

          <div className="role-tile">
            <span className="role-tile__icon">
              <Crown size={18} />
            </span>
            <span>
              <span className="role-tile__count">{adminCount}</span>
              <span className="role-tile__label">Quản trị viên</span>
            </span>
          </div>
        </div>

        <div className="roles__foot">
          <span className="roles__foot-item">
            <Users size={15} /> Tổng tài khoản: <strong>{metrics.totalUsers}</strong>
          </span>
        </div>
      </div>
    </section>
  );
};
