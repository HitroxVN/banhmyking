import type { OrderStatus } from '../../types/order';
import { ORDER_STATUS_LABEL } from '../../utils/orderStatus';
import '../../styles/components/badge.css';

/** Màu theo trạng thái — nhãn lấy từ ORDER_STATUS_LABEL (một nguồn duy nhất). */
const STATUS_CLASS: Record<OrderStatus, string> = {
  PENDING: 'pending',
  CONFIRMED: 'confirmed',
  PREPARING: 'preparing',
  READY_FOR_PICKUP: 'ready',
  DELIVERING: 'delivering',
  DELIVERED: 'delivered',
  CANCELLED: 'cancelled',
  FAILED: 'failed',
};

export interface StatusBadgeProps {
  status: OrderStatus;
  /** Hiện chấm màu đầu nhãn (mặc định có) */
  dot?: boolean;
}

export const StatusBadge = ({ status, dot = true }: StatusBadgeProps) => {
  const cls = STATUS_CLASS[status];
  const label = ORDER_STATUS_LABEL[status];
  if (!cls || !label) {
    return <span className="ui-status ui-status--pending">{status}</span>;
  }
  return (
    <span className={`ui-status ui-status--${cls}`}>
      {dot && <span className="ui-status__dot" aria-hidden="true" />}
      {label}
    </span>
  );
};
