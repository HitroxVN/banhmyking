import type { ReactNode } from 'react';
import '../../styles/components/badge.css';

export type BadgeTone = 'neutral' | 'success' | 'danger' | 'warning' | 'info';

export interface BadgeProps {
  tone?: BadgeTone;
  icon?: ReactNode;
  children: ReactNode;
}

/** Nhãn nhỏ không gắn với trạng thái đơn hàng (vai trò, số lượng, ghi chú…) */
export const Badge = ({ tone = 'neutral', icon, children }: BadgeProps) => (
  <span className={`ui-badge ui-badge--${tone}`}>
    {icon}
    {children}
  </span>
);
