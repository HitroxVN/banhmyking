import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { CircleCheck, Mail, Sandwich, TriangleAlert } from 'lucide-react';
import { authApi } from '../api/authApi';
import '../styles/components/auth.css';

/** Bước 1 của quên mật khẩu: nhập email để nhận link đặt lại. */
export const ForgotPasswordPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [sentNotice, setSentNotice] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    const target = email.trim();
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!target) {
      setFieldError('Vui lòng nhập địa chỉ email');
      return;
    }
    if (!emailRegex.test(target)) {
      setFieldError('Địa chỉ email không đúng định dạng');
      return;
    }
    setFieldError(null);

    setIsSubmitting(true);
    try {
      setSentNotice(await authApi.forgotPassword(target));
    } catch (err: unknown) {
      setErrorMessage((err as Error).message || 'Không gửi được yêu cầu. Vui lòng thử lại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="auth-page-wrapper">
      <div className="auth-container">
        <div className="brand-header">
          <div className="brand-logo-badge" title="Bánh Mỳ King">
            <Sandwich size={26} />
          </div>
          <h1 className="brand-title">BÁNH MỲ KING</h1>
          <p className="brand-tagline">Khôi phục quyền truy cập tài khoản</p>
        </div>

        <div className="auth-card">
          <h2 className="auth-card-title">
            Quên mật khẩu <Mail size={20} aria-hidden="true" />
          </h2>

          {sentNotice ? (
            <>
              <div className="alert-banner alert-success" role="status">
                <CircleCheck size={17} aria-hidden="true" />
                <div>{sentNotice}</div>
              </div>
              <p className="helper-note">
                Link đặt lại có hiệu lực trong 30 phút. Không thấy email? Kiểm tra thư mục Spam/Quảng cáo.
              </p>
              <Link to="/login" className="auth-submit" style={{ display: 'block', textAlign: 'center' }}>
                Về trang đăng nhập
              </Link>
            </>
          ) : (
            <>
              <p className="auth-card-subtitle">
                Nhập email đã đăng ký, chúng tôi sẽ gửi link để bạn đặt mật khẩu mới.
              </p>

              {errorMessage && (
                <div className="alert-banner alert-error" role="alert">
                  <TriangleAlert size={17} aria-hidden="true" />
                  <div>{errorMessage}</div>
                </div>
              )}

              <form onSubmit={handleSubmit} noValidate>
                <div className="form-group">
                  <label className="form-label" htmlFor="forgot-email">
                    Email <span className="required-star">*</span>
                  </label>
                  <div className="input-container">
                    <input
                      id="forgot-email"
                      type="email"
                      className={`form-input${fieldError ? ' input-error' : ''}`}
                      placeholder="vidu@banhmyking.vn"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      autoComplete="email"
                      disabled={isSubmitting}
                    />
                    <span className="input-prefix-icon" aria-hidden="true">
                      <Mail size={17} />
                    </span>
                  </div>
                  {fieldError && <span className="field-error-text">{fieldError}</span>}
                </div>

                <button type="submit" className="auth-submit" disabled={isSubmitting}>
                  {isSubmitting ? (
                    <>
                      <span className="spinner-mini"></span>
                      Đang gửi...
                    </>
                  ) : (
                    'Gửi link đặt lại mật khẩu'
                  )}
                </button>
              </form>
            </>
          )}

          <div className="auth-switch-prompt">
            Nhớ ra mật khẩu rồi?{' '}
            <Link to="/login" className="link-text">
              Đăng nhập
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};
