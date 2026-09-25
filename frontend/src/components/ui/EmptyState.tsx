import type { ReactNode } from 'react';
import { Inbox } from 'lucide-react';
import '../../styles/components/empty-state.css';

export interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: ReactNode;
  /** Nút hành động gợi ý (ví dụ "Xem thực đơn") */
  action?: ReactNode;
}

export const EmptyState = ({ icon, title, description, action }: EmptyStateProps) => (
  <div className="ui-empty">
    <span className="ui-empty__icon" aria-hidden="true">
      {icon ?? <Inbox size={28} />}
    </span>
    <h3 className="ui-empty__title">{title}</h3>
    {description && <p className="ui-empty__desc">{description}</p>}
    {action && <div className="ui-empty__action">{action}</div>}
  </div>
);
