import type { FC } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthProvider';
import { CartProvider } from './context/CartProvider';
import { SiteSettingsProvider } from './context/SiteSettingsProvider';
import { ConfirmProvider, ToastProvider } from './components/ui';
import { CustomerLayout } from './components/layout/CustomerLayout';
import { DashboardLayout } from './components/layout/DashboardLayout';
import { RequireAuth } from './components/layout/RequireAuth';
import { RequireRole } from './components/layout/RequireRole';
import { ADMIN_BRAND, ADMIN_NAV, SHIPPER_BRAND, SHIPPER_NAV, STAFF_BRAND, STAFF_NAV } from './components/layout/navItems';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { VerifyEmailPage } from './pages/VerifyEmailPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { MenuPage } from './pages/MenuPage';
import { LandingPage } from './pages/LandingPage';
import { ProductDetailPage } from './pages/ProductDetailPage';
import { AboutPage, ContactPage, FaqPage, PrivacyPage, TermsPage } from './pages/static/StaticPages';
import { CartPage } from './pages/CartPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { PaymentPage } from './pages/PaymentPage';
import { OrdersPage } from './pages/OrdersPage';
import { OrderTrackingPage } from './pages/OrderTrackingPage';
import { ProfilePage } from './pages/ProfilePage';
import { PromotionsPage } from './pages/PromotionsPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { ReviewManagerPage } from './pages/ReviewManagerPage';
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage';
import { AdminOrdersPage } from './pages/admin/AdminOrdersPage';
import { AdminCategoriesPage } from './pages/admin/AdminCategoriesPage';
import { AdminPromotionsPage } from './pages/admin/AdminPromotionsPage';
import { AdminReportsPage } from './pages/admin/AdminReportsPage';
import { AdminUsersPage } from './pages/admin/AdminUsersPage';
import { AdminSiteSettingsPage } from './pages/admin/AdminSiteSettingsPage';
import { StaffOrderQueuePage } from './pages/staff/StaffOrderQueuePage';
import { StaffMenuPage } from './pages/staff/StaffMenuPage';
import { ShipperOrdersPage } from './pages/shipper/ShipperOrdersPage';

export const App: FC = () => (
  <SiteSettingsProvider>
    <AuthProvider>
    <ToastProvider>
      <ConfirmProvider>
        <CartProvider>
          <BrowserRouter>
            <Routes>
              {/* Công khai */}
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              {/* Trang đích của link xác thực email gửi từ backend */}
              <Route path="/verify-email" element={<VerifyEmailPage />} />
              {/* Quên mật khẩu: nhập email → link trong mail → đặt mật khẩu mới */}
              <Route path="/forgot-password" element={<ForgotPasswordPage />} />
              <Route path="/reset-password" element={<ResetPasswordPage />} />

              {/* Khách hàng — phần này mở cho cả người chưa đăng nhập */}
              <Route element={<CustomerLayout />}>
                <Route index element={<LandingPage />} />
                <Route path="menu" element={<MenuPage />} />
                <Route path="products/:productId" element={<ProductDetailPage />} />
                <Route path="about" element={<AboutPage />} />
                <Route path="contact" element={<ContactPage />} />
                <Route path="faq" element={<FaqPage />} />
                <Route path="terms" element={<TermsPage />} />
                <Route path="privacy" element={<PrivacyPage />} />

                {/* Cần đăng nhập */}
                <Route element={<RequireAuth />}>
                  <Route path="cart" element={<CartPage />} />
                  <Route path="checkout" element={<CheckoutPage />} />
                  <Route path="payment/:orderCode" element={<PaymentPage />} />
                  <Route path="orders" element={<OrdersPage />} />
                  <Route path="orders/:orderCode" element={<OrderTrackingPage />} />
                  <Route path="profile" element={<ProfilePage />} />
                  <Route path="promotions" element={<PromotionsPage />} />
                </Route>
              </Route>

              {/* Quản trị */}
              <Route path="/admin" element={<RequireRole roles={['ADMIN']} area="Quản trị hệ thống" loadingText="Đang xác minh quyền quản trị viên..." />}>
                <Route element={<DashboardLayout navItems={ADMIN_NAV} brand={ADMIN_BRAND} />}>
                  <Route index element={<Navigate to="dashboard" replace />} />
                  <Route path="dashboard" element={<AdminDashboardPage />} />
                  <Route path="orders" element={<AdminOrdersPage />} />
                  <Route path="categories" element={<AdminCategoriesPage />} />
                  <Route path="promotions" element={<AdminPromotionsPage />} />
                  <Route path="reports" element={<AdminReportsPage />} />
                  <Route path="reviews" element={<ReviewManagerPage />} />
                  <Route path="users" element={<AdminUsersPage />} />
                  <Route path="settings" element={<AdminSiteSettingsPage />} />
                </Route>
              </Route>

              {/* Bếp & điều phối */}
              <Route path="/staff" element={<RequireRole roles={['STAFF', 'ADMIN']} area="Bếp & nhân viên" loadingText="Đang xác minh quyền nhân viên..." />}>
                <Route element={<DashboardLayout navItems={STAFF_NAV} brand={STAFF_BRAND} />}>
                  <Route index element={<Navigate to="orders" replace />} />
                  <Route path="orders" element={<StaffOrderQueuePage />} />
                  <Route path="menu" element={<StaffMenuPage />} />
                  <Route path="reviews" element={<ReviewManagerPage />} />
                </Route>
              </Route>

              {/* Tài xế giao hàng */}
              <Route path="/shipper" element={<RequireRole roles={['SHIPPER', 'ADMIN']} area="Tài xế giao hàng" loadingText="Đang xác minh quyền tài xế..." />}>
                <Route element={<DashboardLayout navItems={SHIPPER_NAV} brand={SHIPPER_BRAND} />}>
                  <Route index element={<ShipperOrdersPage />} />
                  <Route path="orders" element={<ShipperOrdersPage />} />
                </Route>
              </Route>

              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </BrowserRouter>
        </CartProvider>
      </ConfirmProvider>
    </ToastProvider>
    </AuthProvider>
  </SiteSettingsProvider>
);

export default App;
