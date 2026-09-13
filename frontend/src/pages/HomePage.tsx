import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/useAuth';
import { tokenStorage } from '../utils/tokenStorage';
import { authApi } from '../api/authApi';

export const HomePage: React.FC = () => {
  const { user, logout, refreshUserProfile } = useAuth();
  const navigate = useNavigate();
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [apiTestResult, setApiTestResult] = useState<string | null>(null);
  const [isTestingApi, setIsTestingApi] = useState(false);

  const handleLogout = async () => {
    if (window.confirm('Bạn có chắc chắn muốn đăng xuất khỏi tài khoản không?')) {
      setIsLoggingOut(true);
      try {
        await logout();
        navigate('/login?logout=true', { replace: true });
      } catch (err) {
        console.error('Logout error:', err);
        navigate('/login?logout=true', { replace: true });
      } finally {
        setIsLoggingOut(false);
      }
    }
  };

  const handleTestInterceptor = async () => {
    setIsTestingApi(true);
    setApiTestResult(null);
    try {
      const data = await authApi.getMe();
      await refreshUserProfile();
      setApiTestResult(`✅ Thành công! Header "Authorization: Bearer <token>" được gửi hợp lệ. Server phản hồi thông tin user: ${data.fullName} (${data.email}) - Role: ${data.role}`);
    } catch (err: unknown) {
      const errObj = err as Error;
      setApiTestResult(`❌ Thất bại: ${errObj.message}`);
    } finally {
      setIsTestingApi(false);
    }
  };

  const token = tokenStorage.getAccessToken();

  return (
    <div className="home-page-wrapper">
      {/* Top Navbar */}
      <header className="home-navbar">
        <div className="navbar-brand">
          <div className="navbar-brand-badge">🥖</div>
          <span className="navbar-brand-name">BÁNH MỲ KING</span>
        </div>

        <div className="navbar-user-actions">
          <div className="user-profile-badge">
            <div className="user-avatar">
              {user?.fullName?.charAt(0)?.toUpperCase() || 'U'}
            </div>
            <span className="user-display-name">{user?.fullName || 'Khách hàng'}</span>
          </div>

          <button id="btn-logout" type="button" className="btn-logout" onClick={handleLogout} disabled={isLoggingOut}>
            {isLoggingOut ? 'Đang thoát...' : '🚪 Đăng xuất'}
          </button>
        </div>
      </header>

      {/* Main Content */}
      <main className="home-main-content">
        <div className="profile-summary-card">
          <div className="profile-card-header">
            <div className="profile-avatar-large">
              {user?.fullName?.charAt(0)?.toUpperCase() || '👑'}
            </div>
            <div>
              <h1 style={{ fontSize: '1.75rem', fontWeight: 800 }}>Xin chào, {user?.fullName || 'Quý khách'}!</h1>
              <p style={{ opacity: 0.85, fontSize: '0.95rem' }}>Chào mừng bạn đến với hệ thống đặt món Bánh Mỳ King</p>
              <div className="profile-role-pill">Vai trò: {user?.role || 'CUSTOMER'}</div>
            </div>
          </div>

          <div className="profile-card-body">
            <h2 style={{ fontSize: '1.15rem', fontWeight: 700, marginBottom: '1.25rem', color: 'var(--stone-800)' }}>
              Thông tin tài khoản cá nhân
            </h2>

            <div className="profile-info-grid">
              <div className="profile-info-item">
                <div className="info-item-label">Họ và tên</div>
                <div className="info-item-value">{user?.fullName || '---'}</div>
              </div>

              <div className="profile-info-item">
                <div className="info-item-label">Địa chỉ Email</div>
                <div className="info-item-value">{user?.email || '---'}</div>
              </div>

              <div className="profile-info-item">
                <div className="info-item-label">Số điện thoại</div>
                <div className="info-item-value">{user?.phone || 'Chưa cập nhật'}</div>
              </div>

              <div className="profile-info-item">
                <div className="info-item-label">Mã định danh User ID</div>
                <div className="info-item-value">#{user?.id || '---'}</div>
              </div>
            </div>

            {/* Token Status & Axios Interceptor Verification Box */}
            <div className="token-status-banner">
              <div className="token-badge-pulse"></div>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 700, fontSize: '0.95rem', color: 'var(--primary-900)' }}>
                  Trạng thái xác thực: ĐÃ ĐĂNG NHẬP (Active JWT Session)
                </div>
                <div style={{ fontSize: '0.85rem', color: 'var(--stone-600)', marginTop: '2px' }}>
                  Token: <code>{token ? `${token.substring(0, 24)}...${token.substring(token.length - 12)}` : 'Chưa có'}</code>
                </div>
              </div>
              <button
                type="button"
                className="btn-primary"
                style={{ width: 'auto', padding: '0.6rem 1rem', fontSize: '0.875rem' }}
                onClick={handleTestInterceptor}
                disabled={isTestingApi}
              >
                {isTestingApi ? 'Đang gọi API...' : '⚡ Kiểm tra Axios Interceptor'}
              </button>
            </div>

            {apiTestResult && (
              <div
                className={`alert-banner ${apiTestResult.startsWith('✅') ? 'alert-success' : 'alert-error'}`}
                style={{ marginTop: '1rem' }}
              >
                <div>{apiTestResult}</div>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
};
