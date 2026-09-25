import type { FC } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthProvider';
import { CartProvider } from './context/CartProvider';
import { ConfirmProvider, ToastProvider } from './components/ui';
import { CustomerLayout } from './components/layout/CustomerLayout';
import { DashboardLayout } from './components/layout/DashboardLayout';
import { RequireAuth } from './components/layout/RequireAuth';
import { RequireRole } from './components/layout/RequireRole';
import { ADMIN_BRAND, ADMIN_NAV, SHIPPER_BRAND, SHIPPER_NAV, STAFF_BRAND, STAFF_NAV } from './components/layout/navItems';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { MenuPage } from './pages/MenuPage';
import { CartPage } from './pages/CartPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { PaymentPage } from './pages/PaymentPage';
import { OrdersPage } from './pages/OrdersPage';
import { OrderTrackingPage } from './pages/OrderTrackingPage';
import { ProfilePage } from './pages/ProfilePage';
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage';
import { AdminOrdersPage } from './pages/admin/AdminOrdersPage';
import { AdminCategoriesPage } from './pages/admin/AdminCategoriesPage';
import { AdminUsersPage } from './pages/admin/AdminUsersPage';
import { StaffOrderQueuePage } from './pages/staff/StaffOrderQueuePage';
import { StaffMenuPage } from './pages/staff/StaffMenuPage';
import { ShipperOrdersPage } from './pages/shipper/ShipperOrdersPage';

export const App: FC = () => (
  <AuthProvider>
    <ToastProvider>
      <ConfirmProvider>
        <CartProvider>
          <BrowserRouter>
            <Routes>
              {/* Công khai */}
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />

              {/* Khách hàng */}
              <Route element={<CustomerLayout />}>
                <Route element={<RequireAuth />}>
                  <Route index element={<MenuPage />} />
                  <Route path="cart" element={<CartPage />} />
                  <Route path="checkout" element={<CheckoutPage />} />
                  <Route path="payment/:orderCode" element={<PaymentPage />} />
                  <Route path="orders" element={<OrdersPage />} />
                  <Route path="orders/:orderCode" element={<OrderTrackingPage />} />
                  <Route path="profile" element={<ProfilePage />} />
                </Route>
              </Route>

              {/* Quản trị */}
              <Route path="/admin" element={<RequireRole roles={['ADMIN']} area="Quản trị hệ thống" loadingText="Đang xác minh quyền quản trị viên..." />}>
                <Route element={<DashboardLayout navItems={ADMIN_NAV} brand={ADMIN_BRAND} />}>
                  <Route index element={<Navigate to="dashboard" replace />} />
                  <Route path="dashboard" element={<AdminDashboardPage />} />
                  <Route path="orders" element={<AdminOrdersPage />} />
                  <Route path="categories" element={<AdminCategoriesPage />} />
                  <Route path="users" element={<AdminUsersPage />} />
                </Route>
              </Route>

              {/* Bếp & điều phối */}
              <Route path="/staff" element={<RequireRole roles={['STAFF', 'ADMIN']} area="Bếp & nhân viên" loadingText="Đang xác minh quyền nhân viên..." />}>
                <Route element={<DashboardLayout navItems={STAFF_NAV} brand={STAFF_BRAND} />}>
                  <Route index element={<Navigate to="orders" replace />} />
                  <Route path="orders" element={<StaffOrderQueuePage />} />
                  <Route path="menu" element={<StaffMenuPage />} />
                </Route>
              </Route>

              {/* Tài xế giao hàng */}
              <Route path="/shipper" element={<RequireRole roles={['SHIPPER', 'ADMIN']} area="Tài xế giao hàng" loadingText="Đang xác minh quyền tài xế..." />}>
                <Route element={<DashboardLayout navItems={SHIPPER_NAV} brand={SHIPPER_BRAND} />}>
                  <Route index element={<ShipperOrdersPage />} />
                  <Route path="orders" element={<ShipperOrdersPage />} />
                </Route>
              </Route>

              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </CartProvider>
      </ConfirmProvider>
    </ToastProvider>
  </AuthProvider>
);

export default App;
