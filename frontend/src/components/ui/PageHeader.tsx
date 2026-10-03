import type { ReactNode } from 'react';
import { RefreshCw } from 'lucide-react';
import { useRealtimeStatus } from '../../context/useRealtime';
import type { RealtimeStatus } from '../../realtime/types';
import '../../styles/components/page-header.css';

export interface PageHeaderProps {
  title: string;
  subtitle?: ReactNode;
  /** Có callback này thì hiện nút "Làm mới" + chấm realtime */
  onRefresh?: () => void;
  isRefreshing?: boolean;
  refreshLabel?: string;
  /** Nút/khu vực hành động thêm ở góc phải */
  actions?: ReactNode;
}

const LIVE_LABEL: Record<RealtimeStatus, string> = {
  live: 'Trực tiếp',
  reconnecting: 'Đang nối lại…',
  offline: 'Ngoại tuyến · tự làm mới 30 giây',
};

/** Trạng thái thật của luồng realtime (spec realtime §5.6) — thay cho nhãn "Realtime Sync" trang trí */
const LiveIndicator = () => {
  const status = useRealtimeStatus();
  return (
    <span className={`page-head__pulse page-head__pulse--${status}`} role="status" title="Kết nối cập nhật tức thì">
      <span className="page-head__pulse-dot" aria-hidden="true" />
      {LIVE_LABEL[status]}
    </span>
  );
};

/**
 * Tiêu đề trang trong khu vận hành.
 * Trước đây phần này nằm trong topbar của 3 layout riêng (Admin/Staff/Shipper).
 */
export const PageHeader = ({
  title,
  subtitle,
  onRefresh,
  isRefreshing = false,
  refreshLabel = 'Làm mới',
  actions,
}: PageHeaderProps) => (
  <header className="page-head">
    <div>
      <h1 className="page-head__title">{title}</h1>
      {subtitle && <p className="page-head__subtitle">{subtitle}</p>}
    </div>

    <div className="page-head__actions">
      {onRefresh && <LiveIndicator />}

      {actions}

      {onRefresh && (
        <button
          type="button"
          className="page-head__refresh"
          onClick={onRefresh}
          disabled={isRefreshing}
        >
          <RefreshCw size={14} className={isRefreshing ? 'page-head__spin' : undefined} />
          {isRefreshing ? 'Đang tải...' : refreshLabel}
        </button>
      )}
    </div>
  </header>
);
