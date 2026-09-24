import { Badge } from '../ui';
import type { OrderStatusStat } from '../../types/admin';
import '../../styles/components/dashboard.css';

interface OrderStatusBreakdownProps {
  stats: OrderStatusStat[];
  totalOrders: number;
  successRate: number;
}

/** Màu theo trạng thái đơn — dùng token --status-* chung với StatusBadge */
const STATUS_COLOR: Record<string, string> = {
  PENDING: 'var(--status-pending)',
  CONFIRMED: 'var(--status-confirmed)',
  PREPARING: 'var(--status-preparing)',
  PROCESSING: 'var(--status-preparing)',
  READY_FOR_PICKUP: 'var(--status-ready)',
  DELIVERING: 'var(--status-delivering)',
  DELIVERED: 'var(--status-delivered)',
  CANCELLED: 'var(--status-cancelled)',
  FAILED: 'var(--status-failed)',
};

const colorOf = (status: string) => STATUS_COLOR[status] ?? 'var(--stone-400)';

export const OrderStatusBreakdown = ({ stats, totalOrders, successRate }: OrderStatusBreakdownProps) => (
  <section className="card">
    <div className="card__head">
      <h3 className="chart__title">Phân bố trạng thái đơn hàng</h3>
      <Badge tone="neutral">{totalOrders} đơn</Badge>
    </div>

    <div className="card__body">
      <div className="brk__rate">
        <span>
          <span className="brk__rate-label">Tỷ lệ hoàn thành</span>
          <span className="brk__rate-sub">Đơn giao thành công / tổng đơn</span>
        </span>
        <span className="brk__rate-value">{successRate}%</span>
      </div>

      <div className="brk__bar" role="img" aria-label={`Phân bố ${totalOrders} đơn theo trạng thái`}>
        {stats.map((item) => (
          <div
            key={item.status}
            className="brk__seg"
            style={{ width: `${Math.max(item.percentage, 0)}%`, backgroundColor: colorOf(item.status) }}
            title={`${item.statusLabel}: ${item.count} đơn (${item.percentage}%)`}
          />
        ))}
      </div>

      <div className="brk__list">
        {stats.map((item) => (
          <div key={item.status} className="brk__row">
            <span className="brk__row-left">
              <span className="brk__dot" style={{ backgroundColor: colorOf(item.status) }} />
              {item.statusLabel}
            </span>
            <span>
              <span className="brk__count">{item.count} đơn</span>
              <span className="brk__pct">({item.percentage}%)</span>
            </span>
          </div>
        ))}
      </div>
    </div>
  </section>
);
