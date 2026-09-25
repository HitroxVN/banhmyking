import React, { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CircleCheck, Eye, EyeOff, Lock, Sandwich, TriangleAlert } from 'lucide-react';
import { authApi } from '../api/authApi';
import '../styles/components/auth.css';

/** Bước 2 của quên mật khẩu: trang đích của link trong mail — /reset-password?token=... */
export const ResetPasswordPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<{ password?: string; confirmPassword?: string }>({});
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    const errors: { password?: string; confirmPassword?: string } = {};
    if (!password) {
      errors.password = 'Vui lòng nhập mật khẩu mới';
    } else if (password.length < 8) {
      errors.password = 'Mật khẩu phải có ít nhất 8 ký tự';
    }
    if (!confirmPassword) {
      errors.confirmPassword = 'Vui lòng xác nhận mật khẩu';
    } else if (confirmPassword !== password) {
      errors.confirmPassword = 'Mật khẩu xác nhận không khớp';
    }

    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setIsSubmitting(true);
    try {
      await authApi.resetPassword(token, password);
      setDone(true);
    } catch (err: unknown) {
      setErrorMessage((err as Error).message || 'Đặt lại mật khẩu không thành công.');
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
          <p className="brand-tagline">Đặt mật khẩu mới cho tài khoản</p>
        </div>

        <div className="auth-card">
          {done ? (
            <>
              <h2 className="auth-card-title">
                <CircleCheck size={20} aria-hidden="true" /> Hoàn tất
              </h2>
              <div className="alert-banner alert-success" role="status">
                <CircleCheck size={17} aria-hidden="true" />
                <div>
                  Đặt lại mật khẩu thành công. Vì an toàn, mọi phiên đăng nhập cũ đã được đăng xuất.
                </div>
              </div>
              <Link to="/login" className="auth-submit" style={{ display: 'block', textAlign: 'center' }}>
                Đăng nhập bằng mật khẩu mới
              </Link>
            </>
          ) : !token ? (
            <>
              <h2 className="auth-card-title">
                <TriangleAlert size={20} aria-hidden="true" /> Link không hợp lệ
              </h2>
              <div className="alert-banner alert-error" role="alert">
                <TriangleAlert size={17} aria-hidden="true" />
                <div>Đường dẫn thiếu token. Vui lòng mở lại link trong email hoặc yêu cầu link mới.</div>
              </div>
              <Link to="/forgot-password" className="auth-submit" style={{ display: 'block', textAlign: 'center' }}>
                Yêu cầu link mới
              </Link>
            </>
          ) : (
            <>
              <h2 className="auth-card-title">Đặt mật khẩu mới</h2>
              <p className="auth-card-subtitle">Mật khẩu mới cần tối thiểu 8 ký tự.</p>

              {errorMessage && (
                <div className="alert-banner alert-error" role="alert">
                  <TriangleAlert size={17} aria-hidden="true" />
                  <div>{errorMessage}</div>
                </div>
              )}

              <form onSubmit={handleSubmit} noValidate>
                <div className="form-group">
                  <label className="form-label" htmlFor="reset-password">
                    Mật khẩu mới <span className="required-star">*</span>
                  </label>
                  <div className="input-container">
                    <input
                      id="reset-password"
                      type={showPassword ? 'text' : 'password'}
                      className={`form-input${fieldErrors.password ? ' input-error' : ''}`}
                      placeholder="Tối thiểu 8 ký tự"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      autoComplete="new-password"
                      disabled={isSubmitting}
                    />
                    <span className="input-prefix-icon" aria-hidden="true">
                      <Lock size={17} />
                    </span>
                    <button
                      type="button"
                      className="input-suffix-btn"
                      onClick={() => setShowPassword((v) => !v)}
                      aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                      aria-pressed={showPassword}
                      tabIndex={-1}
                    >
                      {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                  {fieldErrors.password && <span className="field-error-text">{fieldErrors.password}</span>}
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="reset-password-confirm">
                    Xác nhận mật khẩu <span className="required-star">*</span>
                  </label>
                  <div className="input-container">
                    <input
                      id="reset-password-confirm"
                      type={showPassword ? 'text' : 'password'}
                      className={`form-input${fieldErrors.confirmPassword ? ' input-error' : ''}`}
                      placeholder="Nhập lại mật khẩu mới"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      autoComplete="new-password"
                      disabled={isSubmitting}
                    />
                    <span className="input-prefix-icon" aria-hidden="true">
                      <Lock size={17} />
                    </span>
                  </div>
                  {fieldErrors.confirmPassword && (
                    <span className="field-error-text">{fieldErrors.confirmPassword}</span>
                  )}
                </div>

                <button type="submit" className="auth-submit" disabled={isSubmitting}>
                  {isSubmitting ? (
                    <>
                      <span className="spinner-mini"></span>
                      Đang lưu...
                    </>
                  ) : (
                    'Đặt lại mật khẩu'
                  )}
                </button>
              </form>

              {/* Link hết hạn/hỏng là dead-end nếu không xin được link mới */}
              <div className="auth-switch-prompt">
                Link đã hết hạn?{' '}
                <Link to="/forgot-password" className="link-text">
                  Yêu cầu link mới
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
