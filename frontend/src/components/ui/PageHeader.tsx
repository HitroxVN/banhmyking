import type { ReactNode } from 'react';
import { RefreshCw } from 'lucide-react';
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
      {onRefresh && (
        <span className="page-head__pulse" title="Đồng bộ thời gian thực với máy chủ">
          <span className="page-head__pulse-dot" aria-hidden="true" />
          Realtime Sync
        </span>
      )}

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
