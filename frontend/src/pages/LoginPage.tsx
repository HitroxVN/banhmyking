import React, { useState, useEffect, useMemo } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { CircleCheck, Eye, EyeOff, Hand, Lock, Mail, Sandwich, TriangleAlert } from 'lucide-react';
import { authApi } from '../api/authApi';
import type { ApiError } from '../api/axiosClient';
import { useAuth } from '../context/useAuth';

import { tokenStorage } from '../utils/tokenStorage';
import '../styles/components/auth.css';

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
  const [needsVerification, setNeedsVerification] = useState(false);
  const [resendNotice, setResendNotice] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [isResending, setIsResending] = useState(false);

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
    setNeedsVerification(false);
    setResendNotice(null);

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
      const errObj = err as ApiError;
      setErrorMessage(errObj.message || 'Đăng nhập thất bại. Vui lòng kiểm tra lại email hoặc mật khẩu.');
      // Tài khoản chưa xác thực email → cho xin lại link ngay tại đây
      setNeedsVerification(errObj.errorCode === 'EMAIL_NOT_VERIFIED');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResendVerification = async () => {
    setResendNotice(null);
    setIsResending(true);
    try {
      setResendNotice({ type: 'success', text: await authApi.resendVerification(email.trim()) });
    } catch (err: unknown) {
      setResendNotice({ type: 'error', text: (err as Error).message || 'Không gửi lại được email xác thực.' });
    } finally {
      setIsResending(false);
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
            <Sandwich size={26} />
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

          <h2 className="auth-card-title">
            Chào mừng trở lại! <Hand size={20} aria-hidden="true" />
          </h2>
          <p className="auth-card-subtitle">Nhập email và mật khẩu của bạn để truy cập tài khoản</p>

          {/* Thông báo lỗi */}
          {activeError && (
            <div className="alert-banner alert-error" role="alert">
              <TriangleAlert size={17} aria-hidden="true" />
              <div>{activeError}</div>
            </div>
          )}

          {/* Thông báo thông tin / thành công */}
          {activeSuccess && (
            <div className="alert-banner alert-success" role="status">
              <CircleCheck size={17} aria-hidden="true" />
              <div>{activeSuccess}</div>
            </div>
          )}

          {/* Tài khoản chưa xác thực email — cho gửi lại link tại chỗ */}
          {needsVerification && (
            <>
              {resendNotice && (
                <div
                  className={`alert-banner ${resendNotice.type === 'error' ? 'alert-error' : 'alert-success'}`}
                  role="alert"
                >
                  {resendNotice.type === 'error' ? (
                    <TriangleAlert size={17} aria-hidden="true" />
                  ) : (
                    <CircleCheck size={17} aria-hidden="true" />
                  )}
                  <div>{resendNotice.text}</div>
                </div>
              )}
              <button
                type="button"
                className="auth-submit"
                onClick={handleResendVerification}
                disabled={isResending}
              >
                {isResending ? (
                  <>
                    <span className="spinner-mini"></span>
                    Đang gửi lại...
                  </>
                ) : (
                  'Gửi lại email xác thực'
                )}
              </button>
            </>
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
                <span className="input-prefix-icon" aria-hidden="true">
                  <Mail size={17} />
                </span>
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
                <span className="input-prefix-icon" aria-hidden="true">
                  <Lock size={17} />
                </span>
                <button
                  type="button"
                  className="input-suffix-btn"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {fieldErrors.password && <span className="field-error-text">{fieldErrors.password}</span>}
            </div>

            {/* Helper Row: Remember me */}
            <div className="form-helper-row">
              <label className="ui-check">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                />
                Ghi nhớ đăng nhập
              </label>
              <Link to="/forgot-password" className="link-text">
                Quên mật khẩu?
              </Link>
            </div>

            {/* Submit Button */}
            <button id="btn-login-submit" type="submit" className="auth-submit" disabled={isSubmitting}>
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

        </div>
      </div>
    </div>
  );
};
