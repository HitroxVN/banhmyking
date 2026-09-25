import React, { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CircleCheck, Mail, Sandwich, TriangleAlert } from 'lucide-react';
import { authApi } from '../api/authApi';
import '../styles/components/auth.css';

type Status = 'verifying' | 'success' | 'error';

/** Trang đích của link trong email xác thực: /verify-email?token=... */
export const VerifyEmailPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';

  // Thiếu token thì biết ngay từ đầu — không cần effect để set state
  const [status, setStatus] = useState<Status>(token ? 'verifying' : 'error');
  const [message, setMessage] = useState(
    token ? 'Đang xác thực email...' : 'Đường dẫn xác thực thiếu token. Vui lòng mở lại link trong email.'
  );

  const [email, setEmail] = useState('');
  const [resendNotice, setResendNotice] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [isResending, setIsResending] = useState(false);

  // Chặn gọi 2 lần khi React StrictMode chạy lại effect ở dev —
  // token dùng một lần nên lần gọi thứ hai sẽ báo "đã được sử dụng".
  const verifiedRef = useRef(false);

  useEffect(() => {
    if (!token || verifiedRef.current) return;
    verifiedRef.current = true;

    authApi
      .verifyEmail(token)
      .then((msg) => {
        setStatus('success');
        setMessage(msg);
      })
      .catch((err: Error) => {
        setStatus('error');
        setMessage(err.message);
      });
  }, [token]);

  const handleResend = async (e: React.FormEvent) => {
    e.preventDefault();
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

  return (
    <div className="auth-page-wrapper">
      <div className="auth-container">
        <div className="brand-header">
          <div className="brand-logo-badge" title="Bánh Mỳ King">
            <Sandwich size={26} />
          </div>
          <h1 className="brand-title">BÁNH MỲ KING</h1>
          <p className="brand-tagline">Xác thực email để hoàn tất đăng ký</p>
        </div>

        <div className="auth-card">
          {status === 'verifying' && (
            <>
              <h2 className="auth-card-title">Đang xác thực email</h2>
              <div className="alert-banner" role="status">
                <span className="spinner-mini"></span>
                <div>Vui lòng đợi trong giây lát...</div>
              </div>
            </>
          )}

          {status === 'success' && (
            <>
              <h2 className="auth-card-title">
                <CircleCheck size={20} aria-hidden="true" /> Hoàn tất
              </h2>
              <div className="alert-banner alert-success" role="status">
                <CircleCheck size={17} aria-hidden="true" />
                <div>{message}</div>
              </div>
              <p className="auth-card-subtitle">
                Tài khoản của bạn đã sẵn sàng. Hãy đăng nhập để bắt đầu đặt hàng.
              </p>
              <Link to="/login" className="auth-submit" style={{ display: 'block', textAlign: 'center' }}>
                Đăng nhập ngay
              </Link>
            </>
          )}

          {status === 'error' && (
            <>
              <h2 className="auth-card-title">
                <TriangleAlert size={20} aria-hidden="true" /> Xác thực thất bại
              </h2>
              <div className="alert-banner alert-error" role="alert">
                <TriangleAlert size={17} aria-hidden="true" />
                <div>{message}</div>
              </div>

              {/* Link hỏng/hết hạn là dead-end nếu không có cách xin link mới */}
              <p className="auth-card-subtitle">Nhập email đã đăng ký để nhận link xác thực mới:</p>

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

              <form onSubmit={handleResend} noValidate>
                <div className="form-group">
                  <label className="form-label" htmlFor="verify-email">
                    Email <span className="required-star">*</span>
                  </label>
                  <div className="input-container">
                    <input
                      id="verify-email"
                      type="email"
                      className="form-input"
                      placeholder="vidu@banhmyking.vn"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      autoComplete="email"
                      required
                      disabled={isResending}
                    />
                    <span className="input-prefix-icon" aria-hidden="true">
                      <Mail size={17} />
                    </span>
                  </div>
                </div>
                <button type="submit" className="auth-submit" disabled={isResending || !email.trim()}>
                  {isResending ? (
                    <>
                      <span className="spinner-mini"></span>
                      Đang gửi lại...
                    </>
                  ) : (
                    'Gửi lại email xác thực'
                  )}
                </button>
              </form>
            </>
          )}

          <div className="auth-switch-prompt">
            <Link to="/login" className="link-text">
              Về trang đăng nhập
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};
