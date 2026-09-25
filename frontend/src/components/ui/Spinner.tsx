import { Loader2 } from 'lucide-react';
import '../../styles/components/spinner.css';

export interface SpinnerProps {
  size?: number;
  /** Nhãn cho screen reader */
  label?: string;
  className?: string;
}

export const Spinner = ({ size = 22, label = 'Đang tải', className = '' }: SpinnerProps) => (
  <Loader2 className={`ui-spinner${className ? ` ${className}` : ''}`} size={size} aria-label={label} />
);

export interface LoadingScreenProps {
  text?: string;
  /** Chiếm trọn màn hình thay vì chỉ một khối nội dung */
  full?: boolean;
}

/** Màn hình chờ dùng chung cho route guard và các trang tải lần đầu */
export const LoadingScreen = ({ text = 'Đang tải dữ liệu...', full = false }: LoadingScreenProps) => (
  <div className={`ui-loading-screen${full ? ' ui-loading-screen--full' : ''}`} role="status">
    <Spinner size={30} label={text} />
    <p className="ui-loading-screen__text">{text}</p>
  </div>
);
