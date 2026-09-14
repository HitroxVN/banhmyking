import React from 'react';
import type { OrderStatusStat } from '../../types/admin';

interface OrderStatusBreakdownProps {
  stats: OrderStatusStat[];
  totalOrders: number;
  successRate: number;
}

export const OrderStatusBreakdown: React.FC<OrderStatusBreakdownProps> = ({
  stats,
  totalOrders,
  successRate,
}) => {
  const getStatusColor = (status: string) => {
    switch (status) {
      case 'DELIVERED':
        return '#10b981'; // green
      case 'DELIVERING':
        return '#3b82f6'; // blue
      case 'PROCESSING':
        return '#f59e0b'; // amber
      case 'PENDING':
        return '#8b5cf6'; // purple
      case 'CANCELLED':
        return '#ef4444'; // red
      default:
        return '#94a3b8'; // gray
    }
  };

  return (
    <div className="status-breakdown-card">
      <div className="status-breakdown-header">
        <h3 className="breakdown-title">Phân bố trạng thái đơn hàng</h3>
        <span className="breakdown-badge">Tổng {totalOrders} đơn</span>
      </div>

      {/* Success Rate Highlight */}
      <div className="success-rate-banner">
        <div className="success-rate-info">
          <span className="success-rate-label">Tỷ lệ hoàn thành đơn</span>
          <span className="success-rate-sub">Đơn giao thành công / Tổng đơn</span>
        </div>
        <div className="success-rate-value">
          <span className="rate-num">{successRate}%</span>
        </div>
      </div>

      {/* Multi-segment Progress Bar */}
      <div className="multi-progress-bar">
        {stats.map((item) => (
          <div
            key={item.status}
            className="progress-segment"
            style={{
              width: `${Math.max(item.percentage, 0)}%`,
              backgroundColor: getStatusColor(item.status),
            }}
            title={`${item.label}: ${item.count} đơn (${item.percentage}%)`}
          />
        ))}
      </div>

      {/* Status Detailed Items List */}
      <div className="status-items-list">
        {stats.map((item) => {
          const color = getStatusColor(item.status);
          return (
            <div key={item.status} className="status-item-row">
              <div className="status-item-left">
                <span className="status-dot" style={{ backgroundColor: color }}></span>
                <span className="status-label">{item.label}</span>
              </div>
              <div className="status-item-right">
                <span className="status-count">{item.count} đơn</span>
                <span className="status-percent">({item.percentage}%)</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
