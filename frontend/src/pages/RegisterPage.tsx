import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Eye, EyeOff, Lock, Mail, Sandwich, ShieldCheck, Smartphone, TriangleAlert, User } from 'lucide-react';
import { useAuth } from '../context/useAuth';
import '../styles/components/auth.css';

export const RegisterPage: React.FC = () => {
  const { register, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  interface FormErrors {
    fullName?: string;
    email?: string;
    phone?: string;
    password?: string;
    confirmPassword?: string;
  }
  const [fieldErrors, setFieldErrors] = useState<FormErrors>({});
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Nếu đã đăng nhập thì về trang chủ
  useEffect(() => {
    if (isAuthenticated) {
      navigate('/', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  // Tính độ mạnh mật khẩu
  const getPasswordStrength = (pass: string): { score: number; label: string; className: string } => {
    if (!pass) return { score: 0, label: '', className: '' };
    let score = 0;
    if (pass.length >= 8) score += 1;
    if (/[A-Z]/.test(pass) && /[a-z]/.test(pass)) score += 1;
    if (/[0-9]/.test(pass)) score += 1;
    if (/[^A-Za-z0-9]/.test(pass)) score += 1;

    if (score <= 1) return { score: 1, label: 'Yếu (Nên thêm số và chữ hoa)', className: 'weak' };
    if (score <= 3) return { score: 2, label: 'Khá tốt', className: 'medium' };
    return { score: 3, label: 'Rất mạnh', className: 'strong' };
  };

  const strength = getPasswordStrength(password);

  const validate = (): boolean => {
    const errors: FormErrors = {};
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    // Hỗ trợ số điện thoại Việt Nam 10 chữ số (03, 05, 07, 08, 09)
    const phoneRegex = /^(0|\+84)(3|5|7|8|9)[0-9]{8}$/;

    if (!fullName.trim()) {
      errors.fullName = 'Vui lòng nhập họ và tên của bạn';
    } else if (fullName.trim().length < 2) {
      errors.fullName = 'Họ và tên phải có ít nhất 2 ký tự';
    }

    if (!email.trim()) {
      errors.email = 'Vui lòng nhập địa chỉ email';
    } else if (!emailRegex.test(email.trim())) {
      errors.email = 'Địa chỉ email không hợp lệ (ví dụ: name@domain.com)';
    }

    if (phone.trim() && !phoneRegex.test(phone.trim().replace(/\s/g, ''))) {
      errors.phone = 'Số điện thoại không đúng định dạng Việt Nam (10 chữ số)';
    }

    if (!password) {
      errors.password = 'Vui lòng nhập mật khẩu';
    } else if (password.length < 8) {
      errors.password = 'Mật khẩu phải chứa ít nhất 8 ký tự';
    } else if (password.length > 100) {
      errors.password = 'Mật khẩu không được vượt quá 100 ký tự';
    }

    if (!confirmPassword) {
      errors.confirmPassword = 'Vui lòng xác nhận mật khẩu';
    } else if (password !== confirmPassword) {
      errors.confirmPassword = 'Mật khẩu xác nhận không trùng khớp';
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
      await register({
        fullName: fullName.trim(),
        email: email.trim(),
        phone: phone.trim() ? phone.trim() : undefined,
        password,
      });
      // Đăng ký thành công -> tự động lưu token và chuyển về trang chủ
      navigate('/', { replace: true });
    } catch (err: unknown) {
      const errObj = err as Error;
      setErrorMessage(errObj.message || 'Đăng ký không thành công. Vui lòng kiểm tra lại thông tin.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="auth-page-wrapper">
      <div className="auth-container">
        {/* Brand Header */}
        <div className="brand-header">
          <div className="brand-logo-badge" title="Bánh Mỳ King">
            <Sandwich size={26} />
          </div>
          <h1 className="brand-title">BÁNH MỲ KING</h1>
          <p className="brand-tagline">Tạo tài khoản để nhận ngàn ưu đãi hấp dẫn</p>
        </div>

        {/* Auth Card */}
        <div className="auth-card">
          {/* Nav Tabs */}
          <div className="auth-nav-tabs">
            <Link to="/login" className="auth-tab-btn">
              Đăng nhập
            </Link>
            <button type="button" className="auth-tab-btn active">
              Đăng ký
            </button>
          </div>

          <h2 className="auth-card-title">Tạo tài khoản mới</h2>
          <p className="auth-card-subtitle">Điền thông tin bên dưới để trở thành thành viên Bánh Mỳ King</p>

          {/* Error Banner */}
          {errorMessage && (
            <div className="alert-banner alert-error" role="alert">
              <TriangleAlert size={17} aria-hidden="true" />
              <div>{errorMessage}</div>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate>
            {/* Họ và tên */}
            <div className="form-group">
              <label className="form-label" htmlFor="reg-fullname">
                Họ và tên <span className="required-star">*</span>
              </label>
              <div className="input-container">
                <input
                  id="reg-fullname"
                  type="text"
                  className={`form-input ${fieldErrors.fullName ? 'input-error' : ''}`}
                  placeholder="Nguyễn Văn A"
                  value={fullName}
                  onChange={(e) => {
                    setFullName(e.target.value);
                    if (fieldErrors.fullName) setFieldErrors({ ...fieldErrors, fullName: undefined });
                  }}
                  disabled={isSubmitting}
                />
                <span className="input-prefix-icon" aria-hidden="true">
                  <User size={17} />
                </span>
              </div>
              {fieldErrors.fullName && <span className="field-error-text">{fieldErrors.fullName}</span>}
            </div>

            {/* Email */}
            <div className="form-group">
              <label className="form-label" htmlFor="reg-email">
                Email <span className="required-star">*</span>
              </label>
              <div className="input-container">
                <input
                  id="reg-email"
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

            {/* Số điện thoại */}
            <div className="form-group">
              <label className="form-label" htmlFor="reg-phone">
                Số điện thoại
              </label>
              <div className="input-container">
                <input
                  id="reg-phone"
                  type="tel"
                  className={`form-input ${fieldErrors.phone ? 'input-error' : ''}`}
                  placeholder="0912 345 678"
                  value={phone}
                  onChange={(e) => {
                    setPhone(e.target.value);
                    if (fieldErrors.phone) setFieldErrors({ ...fieldErrors, phone: undefined });
                  }}
                  autoComplete="tel"
                  disabled={isSubmitting}
                />
                <span className="input-prefix-icon" aria-hidden="true">
                  <Smartphone size={17} />
                </span>
              </div>
              {fieldErrors.phone && <span className="field-error-text">{fieldErrors.phone}</span>}
            </div>

            {/* Mật khẩu */}
            <div className="form-group">
              <label className="form-label" htmlFor="reg-password">
                Mật khẩu <span className="required-star">*</span>
              </label>
              <div className="input-container">
                <input
                  id="reg-password"
                  type={showPassword ? 'text' : 'password'}
                  className={`form-input ${fieldErrors.password ? 'input-error' : ''}`}
                  placeholder="Tối thiểu 8 ký tự..."
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (fieldErrors.password) setFieldErrors({ ...fieldErrors, password: undefined });
                  }}
                  autoComplete="new-password"
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

              {/* Thanh đo độ mạnh mật khẩu */}
              {password && (
                <div className="password-strength-container">
                  <div className="password-strength-bars">
                    <div className={`strength-bar ${strength.score >= 1 ? strength.className : ''}`}></div>
                    <div className={`strength-bar ${strength.score >= 2 ? strength.className : ''}`}></div>
                    <div className={`strength-bar ${strength.score >= 3 ? strength.className : ''}`}></div>
                  </div>
                  <span className="strength-label">Độ mạnh: {strength.label}</span>
                </div>
              )}
            </div>

            {/* Xác nhận mật khẩu */}
            <div className="form-group">
              <label className="form-label" htmlFor="reg-confirm-password">
                Xác nhận mật khẩu <span className="required-star">*</span>
              </label>
              <div className="input-container">
                <input
                  id="reg-confirm-password"
                  type={showConfirmPassword ? 'text' : 'password'}
                  className={`form-input ${fieldErrors.confirmPassword ? 'input-error' : ''}`}
                  placeholder="Nhập lại mật khẩu..."
                  value={confirmPassword}
                  onChange={(e) => {
                    setConfirmPassword(e.target.value);
                    if (fieldErrors.confirmPassword) setFieldErrors({ ...fieldErrors, confirmPassword: undefined });
                  }}
                  autoComplete="new-password"
                  disabled={isSubmitting}
                />
                <span className="input-prefix-icon" aria-hidden="true">
                  <ShieldCheck size={17} />
                </span>
                <button
                  type="button"
                  className="input-suffix-btn"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  aria-label={showConfirmPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  tabIndex={-1}
                >
                  {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {fieldErrors.confirmPassword && <span className="field-error-text">{fieldErrors.confirmPassword}</span>}
            </div>

            {/* Submit Button */}
            <button id="btn-register-submit" type="submit" className="auth-submit" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <span className="spinner-mini"></span>
                  Đang khởi tạo tài khoản...
                </>
              ) : (
                'Đăng ký tài khoản Bánh Mỳ King'
              )}
            </button>
          </form>

          {/* Switch Prompt */}
          <div className="auth-switch-prompt">
            Đã có tài khoản?{' '}
            <Link to="/login" className="link-text">
              Đăng nhập ngay
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};
