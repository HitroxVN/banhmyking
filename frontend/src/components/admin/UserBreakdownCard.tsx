import React from 'react';
import { useNavigate } from 'react-router-dom';
import type { DashboardMetrics } from '../../types/admin';

interface UserBreakdownCardProps {
  metrics: DashboardMetrics;
}

export const UserBreakdownCard: React.FC<UserBreakdownCardProps> = ({ metrics }) => {
  const navigate = useNavigate();

  return (
    <div className="user-breakdown-card">
      <div className="breakdown-header">
        <div>
          <h3 className="breakdown-title">Cơ cấu người dùng & Phân quyền</h3>
          <p className="breakdown-subtitle">Tổng số tài khoản trong hệ thống: {metrics.totalUsers}</p>
        </div>
        <button
          type="button"
          className="btn-link-action"
          onClick={() => navigate('/admin/users')}
        >
          Quản lý tài khoản →
        </button>
      </div>

      <div className="role-distribution-grid">
        <div className="role-stat-tile customer">
          <div className="role-tile-icon">🛒</div>
          <div className="role-tile-body">
            <span className="role-tile-count">{metrics.customerCount}</span>
            <span className="role-tile-label">Khách hàng (CUSTOMER)</span>
          </div>
        </div>

        <div className="role-stat-tile staff">
          <div className="role-tile-icon">👨‍🍳</div>
          <div className="role-tile-body">
            <span className="role-tile-count">{metrics.staffCount}</span>
            <span className="role-tile-label">Nhân viên bếp/bán hàng (STAFF)</span>
          </div>
        </div>

        <div className="role-stat-tile shipper">
          <div className="role-tile-icon">🛵</div>
          <div className="role-tile-body">
            <span className="role-tile-count">{metrics.shipperCount}</span>
            <span className="role-tile-label">Giao hàng (SHIPPER)</span>
          </div>
        </div>

        <div className="role-stat-tile admin">
          <div className="role-tile-icon">👑</div>
          <div className="role-tile-body">
            <span className="role-tile-count">{metrics.adminCount}</span>
            <span className="role-tile-label">Quản trị viên (ADMIN)</span>
          </div>
        </div>
      </div>

      <div className="account-status-summary">
        <div className="status-summary-item active">
          <span className="status-indicator-dot green"></span>
          <span>Đang hoạt động: <strong>{metrics.activeUsers}</strong></span>
        </div>
        <div className="status-summary-item banned">
          <span className="status-indicator-dot red"></span>
          <span>Đã bị khóa: <strong>{metrics.bannedUsers}</strong></span>
        </div>
      </div>
    </div>
  );
};
