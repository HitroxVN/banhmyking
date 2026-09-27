import { Component, type ErrorInfo, type ReactNode } from 'react';
import { RotateCcw, TriangleAlert } from 'lucide-react';
import { Button } from '../ui';
import '../../styles/components/auth.css';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  error: Error | null;
}

/**
 * Chặn lỗi render làm trắng cả app. Cố tình KHÔNG dùng BrandLockup hay bất kỳ
 * component nào cần context — nếu provider là thứ văng lỗi thì màn hình này
 * cũng phải render được.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Lỗi render không bắt được:', error, info.componentStack);
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    return (
      <div className="auth-page-wrapper">
        <div className="auth-container">
          <div className="auth-card" role="alert">
            <h2 className="auth-card-title">
              Có lỗi xảy ra <TriangleAlert size={20} aria-hidden="true" />
            </h2>
            <p className="auth-card-subtitle">
              Trang gặp sự cố khi hiển thị. Thử tải lại — giỏ hàng và đơn đang làm vẫn còn nguyên.
            </p>

            <Button block icon={<RotateCcw size={16} />} onClick={() => window.location.reload()}>
              Tải lại trang
            </Button>

            <p className="auth-switch-prompt">{error.message}</p>
          </div>
        </div>
      </div>
    );
  }
}
