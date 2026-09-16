import React, { useState, useEffect, useMemo } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/useAuth';

import { tokenStorage } from '../utils/tokenStorage';

export const LoginPage: React.FC = () => {
  const { login, isAuthenticated, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);

  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({});
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Nếu đã đăng nhập: Admin chuyển thẳng về /admin, Staff về /staff, user thông thường về trang chủ
  useEffect(() => {
    if (isAuthenticated) {
      if (user?.role === 'ADMIN') {
        navigate('/admin', { replace: true });
      } else if (user?.role === 'STAFF') {
        navigate('/staff', { replace: true });
      } else if (user?.role === 'SHIPPER') {
        navigate('/shipper', { replace: true });
      } else {
        navigate('/', { replace: true });
      }
    }
  }, [isAuthenticated, user, navigate]);

  // Đọc thông điệp từ URL params (ví dụ: đăng xuất thành công, token hết hạn)
  const urlNotice = useMemo(() => {
    const params = new URLSearchParams(location.search);
    if (params.get('logout') === 'true') {
      return { type: 'success', text: 'Bạn đã đăng xuất thành công khỏi hệ thống.' };
    }
    if (params.get('expired') === 'true') {
      return { type: 'error', text: 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.' };
    }
    if (params.get('registered') === 'true') {
      return { type: 'success', text: 'Đăng ký tài khoản thành công! Vui lòng đăng nhập.' };
    }
    return null;
  }, [location.search]);

  const validate = (): boolean => {
    const errors: { email?: string; password?: string } = {};
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    if (!email.trim()) {
      errors.email = 'Vui lòng nhập địa chỉ email';
    } else if (!emailRegex.test(email.trim())) {
      errors.email = 'Địa chỉ email không đúng định dạng';
    }

    if (!password) {
      errors.password = 'Vui lòng nhập mật khẩu';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!validate()) {
      return;
    }

    setIsSubmitting(true);
    try {
      await login({ email: email.trim(), password });
      const current = tokenStorage.getUserInfo();
      if (current?.role === 'ADMIN') {
        navigate('/admin', { replace: true });
      } else if (current?.role === 'STAFF') {
        navigate('/staff', { replace: true });
      } else if (current?.role === 'SHIPPER') {
        navigate('/shipper', { replace: true });
      } else {
        const from = (location.state as { from?: { pathname?: string } })?.from?.pathname || '/';
        navigate(from, { replace: true });
      }
    } catch (err: unknown) {
      const errObj = err as Error;
      setErrorMessage(errObj.message || 'Đăng nhập thất bại. Vui lòng kiểm tra lại email hoặc mật khẩu.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const activeError = errorMessage || (urlNotice?.type === 'error' ? urlNotice.text : null);
  const activeSuccess = !errorMessage && urlNotice?.type === 'success' ? urlNotice.text : null;

  return (
    <div className="auth-page-wrapper">
      <div className="auth-container">
        {/* Brand Header */}
        <div className="brand-header">
          <div className="brand-logo-badge" title="Bánh Mỳ King">
            🥖
          </div>
          <h1 className="brand-title">BÁNH MỲ KING</h1>
          <p className="brand-tagline">Hương vị đỉnh cao - Đẳng cấp hoàng gia</p>
        </div>

        {/* Auth Card */}
        <div className="auth-card">
          {/* Nav Tabs */}
          <div className="auth-nav-tabs">
            <button type="button" className="auth-tab-btn active">
              Đăng nhập
            </button>
            <Link to="/register" className="auth-tab-btn">
              Đăng ký
            </Link>
          </div>

          <h2 className="auth-card-title">Chào mừng trở lại! 👋</h2>
          <p className="auth-card-subtitle">Nhập email và mật khẩu của bạn để truy cập tài khoản</p>

          {/* Thông báo lỗi */}
          {activeError && (
            <div className="alert-banner alert-error" role="alert">
              <span className="alert-icon">⚠️</span>
              <div>{activeError}</div>
            </div>
          )}

          {/* Thông báo thông tin / thành công */}
          {activeSuccess && (
            <div className="alert-banner alert-success" role="status">
              <span className="alert-icon">✅</span>
              <div>{activeSuccess}</div>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate>
            {/* Email Field */}
            <div className="form-group">
              <label className="form-label" htmlFor="login-email">
                Địa chỉ Email <span className="required-star">*</span>
              </label>
              <div className="input-container">
                <input
                  id="login-email"
                  type="email"
                  className={`form-input ${fieldErrors.email ? 'input-error' : ''}`}
                  placeholder="vidu@banhmyking.vn"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    if (fieldErrors.email) setFieldErrors({ ...fieldErrors, email: undefined });
                  }}
                  autoComplete="email"
                  disabled={isSubmitting}
                />
                <span className="input-prefix-icon">✉️</span>
              </div>
              {fieldErrors.email && <span className="field-error-text">{fieldErrors.email}</span>}
            </div>

            {/* Password Field */}
            <div className="form-group">
              <label className="form-label" htmlFor="login-password">
                Mật khẩu <span className="required-star">*</span>
              </label>
              <div className="input-container">
                <input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  className={`form-input ${fieldErrors.password ? 'input-error' : ''}`}
                  placeholder="Nhập mật khẩu..."
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (fieldErrors.password) setFieldErrors({ ...fieldErrors, password: undefined });
                  }}
                  autoComplete="current-password"
                  disabled={isSubmitting}
                />
                <span className="input-prefix-icon">🔒</span>
                <button
                  type="button"
                  className="input-suffix-btn"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  tabIndex={-1}
                >
                  {showPassword ? '🙈' : '👁️'}
                </button>
              </div>
              {fieldErrors.password && <span className="field-error-text">{fieldErrors.password}</span>}
            </div>

            {/* Helper Row: Remember me */}
            <div className="form-helper-row">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  className="checkbox-input"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                />
                Ghi nhớ đăng nhập
              </label>
              <span className="link-text" style={{ cursor: 'pointer' }} onClick={() => alert('Vui lòng liên hệ quản trị viên để khôi phục mật khẩu')}>
                Quên mật khẩu?
              </span>
            </div>

            {/* Submit Button */}
            <button id="btn-login-submit" type="submit" className="btn-primary" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <span className="spinner-mini"></span>
                  Đang xác thực...
                </>
              ) : (
                'Đăng nhập vào Bánh Mỳ King'
              )}
            </button>
          </form>

          {/* Switch Prompt */}
          <div className="auth-switch-prompt">
            Chưa có tài khoản?{' '}
            <Link to="/register" className="link-text">
              Đăng ký ngay
            </Link>
          </div>

          {/* Quick Demo Logins */}
          <div className="quick-demo-container">
            <div className="quick-demo-title">Tài khoản trải nghiệm nhanh:</div>
            <div className="quick-demo-buttons">
              <button
                type="button"
                className="btn-demo-chip"
                onClick={() => {
                  setEmail('shipper@banhmyking.vn');
                  setPassword('123456');
                }}
              >
                🛵 Tài Xế (Shipper)
              </button>
              <button
                type="button"
                className="btn-demo-chip"
                onClick={() => {
                  setEmail('staff@banhmyking.vn');
                  setPassword('123456');
                }}
              >
                👨‍🍳 Bếp / Nhân Viên
              </button>
              <button
                type="button"
                className="btn-demo-chip"
                onClick={() => {
                  setEmail('admin@banhmyking.vn');
                  setPassword('123456');
                }}
              >
                👑 Quản Trị Viên
              </button>
              <button
                type="button"
                className="btn-demo-chip"
                onClick={() => {
                  setEmail('test@banhmyking.vn');
                  setPassword('123456');
                }}
              >
                🥖 Khách Hàng
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
