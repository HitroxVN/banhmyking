import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Loader2 } from 'lucide-react';
import '../../styles/components/button.css';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'success';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Hiện spinner và khoá nút trong lúc chờ API */
  loading?: boolean;
  /** Chiếm toàn bộ chiều ngang khung chứa */
  block?: boolean;
  icon?: ReactNode;
}

export const Button = ({
  variant = 'primary',
  size = 'md',
  loading = false,
  block = false,
  icon,
  children,
  className = '',
  disabled,
  type = 'button',
  ...rest
}: ButtonProps) => (
  <button
    type={type}
    className={`ui-btn ui-btn--${variant} ui-btn--${size}${block ? ' ui-btn--block' : ''}${className ? ` ${className}` : ''}`}
    disabled={disabled || loading}
    {...rest}
  >
    {loading ? <Loader2 className="ui-btn__spin" size={16} aria-hidden="true" /> : icon}
    {children}
  </button>
);
