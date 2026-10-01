import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Bike,
  Home,
  LogOut,
  Mail,
  MapPin,
  Phone,
  Receipt,
  Sandwich,
  Search,
  ShieldCheck,
  ShoppingCart,
  Ticket,
  Timer,
  UserRound,
} from 'lucide-react';
import { useAuth } from '../../context/useAuth';
import { useCart } from '../../context/useCart';
import { useSiteSettings } from '../../context/useSiteSettings';
import { useConfirm } from '../ui';
import { formatCurrency } from '../../utils/formatters';

/** Các bước thanh toán đã có giỏ hàng riêng trong trang nên không cần pill nổi */
const FLOAT_CART_HIDDEN_ON = ['/cart', '/checkout', '/payment'];

/** Khung trang dành cho khách: dải promo + navbar sticky + footer + pill giỏ hàng nổi. */
export const CustomerLayout = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const { totalQuantity, subtotal } = useCart();
  const { settings } = useSiteSettings();
  const confirm = useConfirm();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const [searchParams] = useSearchParams();
  const urlKeyword = searchParams.get('keyword') ?? '';
  const [keyword, setKeyword] = useState(urlKeyword);
  const [menuOpen, setMenuOpen] = useState(false);
  const userRef = useRef<HTMLDivElement>(null);

  // URL là nguồn sự thật: back/forward hoặc bấm logo thì ô tìm kiếm đi theo
  useEffect(() => {
    setKeyword(urlKeyword);
  }, [urlKeyword]);

  // Gõ tới đâu lọc tới đó — debounce 300ms rồi mới đẩy lên URL cho MenuPage đọc
  useEffect(() => {
    const trimmed = keyword.trim();
    if (trimmed === urlKeyword) return;

    const timer = window.setTimeout(() => {
      navigate(trimmed ? `/menu?keyword=${encodeURIComponent(trimmed)}` : '/menu', { replace: true });
    }, 300);

    return () => window.clearTimeout(timer);
  }, [keyword, urlKeyword, navigate]);

  // Đóng dropdown khi bấm ra ngoài hoặc nhấn ESC
  useEffect(() => {
    if (!menuOpen) return;

    const onPointerDown = (event: MouseEvent) => {
      if (!userRef.current?.contains(event.target as Node)) setMenuOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMenuOpen(false);
    };

    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [menuOpen]);

  const handleSearch = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = keyword.trim();
    navigate(trimmed ? `/menu?keyword=${encodeURIComponent(trimmed)}` : '/menu');
  };

  const handleLogout = async () => {
    setMenuOpen(false);
    const accepted = await confirm({
      title: 'Đăng xuất',
      message: 'Bạn có chắc chắn muốn đăng xuất khỏi tài khoản này?',
      confirmText: 'Đăng xuất',
      danger: true,
    });
    if (accepted) {
      await logout();
      navigate('/login');
    }
  };

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `cshop__link${isActive ? ' cshop__link--active' : ''}`;

  const showFloatCart =
    isAuthenticated &&
    totalQuantity > 0 &&
    !FLOAT_CART_HIDDEN_ON.some((prefix) => pathname.startsWith(prefix));

  return (
    <div className="cshop">
      <div className="cshop__strip">
        <div className="cshop__strip-inner">
          {settings.announcementPrimary && (
            <span className="cshop__strip-item">
              <Timer size={14} />
              {settings.announcementPrimary}
            </span>
          )}
          {settings.announcementSecondary && (
            <span className="cshop__strip-item cshop__strip-item--end">
              <Bike size={14} />
              {settings.announcementSecondary}
            </span>
          )}
        </div>
      </div>

      <nav className="cshop__nav">
        <div className="cshop__nav-inner">
          <Link to="/" className="cshop__logo">
            <span className="cshop__logo-badge">
              <Sandwich size={22} />
            </span>
            <span className="cshop__logo-text">
              <span className="cshop__logo-name">{settings.siteName}</span>
              <span className="cshop__logo-tag">{settings.tagline}</span>
            </span>
          </Link>

          <div className="cshop__links">
            <NavLink to="/menu" className={navLinkClass}>
              Thực đơn
            </NavLink>
            <NavLink to="/stores" className={navLinkClass}>
              Cửa hàng
            </NavLink>
            <NavLink to="/about" className={navLinkClass}>
              Giới thiệu
            </NavLink>
            <NavLink to="/contact" className={navLinkClass}>
              Liên hệ
            </NavLink>
            <NavLink to="/orders" className={navLinkClass}>
              Đơn của tôi
            </NavLink>
          </div>

          <form className="cshop__search" onSubmit={handleSearch} role="search">
            <span className="cshop__search-icon">
              <Search size={17} />
            </span>
            <input
              className="cshop__search-input"
              type="search"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="Tìm bánh mì, nước uống..."
              aria-label="Tìm món ăn"
            />
          </form>

          <div className="cshop__actions">
            {isAuthenticated && (
              <Link
                to="/cart"
                className="cshop__cart-pill"
                aria-label={`Giỏ hàng, ${totalQuantity} món, tạm tính ${formatCurrency(subtotal)}`}
              >
                <span className="cshop__cart-pill-icon">
                  <ShoppingCart size={19} />
                  {totalQuantity > 0 && <span className="cshop__cart-badge">{totalQuantity}</span>}
                </span>
                <span className="cshop__cart-pill-total">{formatCurrency(subtotal)}</span>
              </Link>
            )}

            {!user ? (
              <div className="cshop__auth">
                <Link to="/login" className="ui-btn ui-btn--ghost ui-btn--sm">
                  Đăng nhập
                </Link>
                <Link to="/register" className="ui-btn ui-btn--primary ui-btn--sm">
                  Đăng ký
                </Link>
              </div>
            ) : (
              <div className="cshop__user" ref={userRef}>
                <button
                  type="button"
                  className="cshop__avatar-btn"
                  onClick={() => setMenuOpen((open) => !open)}
                  aria-haspopup="menu"
                  aria-expanded={menuOpen}
                >
                  {user.image ? (
                    <img className="cshop__avatar" src={user.image} alt="" />
                  ) : (
                    <span className="cshop__avatar">{user.fullName?.charAt(0)?.toUpperCase() ?? 'K'}</span>
                  )}
                  <span>{user.fullName?.split(' ').pop() ?? 'Khách'}</span>
                </button>

                {menuOpen && (
                  <div className="cshop__dropdown" role="menu">
                    <div className="cshop__dropdown-head">
                      <span className="cshop__dropdown-name">{user.fullName}</span>
                      <span className="cshop__dropdown-email">{user.email}</span>
                    </div>

                    <Link to="/menu" className="cshop__dropdown-item" role="menuitem" onClick={() => setMenuOpen(false)}>
                      <Sandwich size={16} />
                      Thực đơn
                    </Link>
                    <Link to="/" className="cshop__dropdown-item" role="menuitem" onClick={() => setMenuOpen(false)}>
                      <Home size={16} />
                      Trang chủ
                    </Link>
                    <Link to="/orders" className="cshop__dropdown-item" role="menuitem" onClick={() => setMenuOpen(false)}>
                      <Receipt size={16} />
                      Đơn của tôi
                    </Link>
                    <Link to="/promotions" className="cshop__dropdown-item" role="menuitem" onClick={() => setMenuOpen(false)}>
                      <Ticket size={16} />
                      Ưu đãi của tôi
                    </Link>
                    <Link to="/profile" className="cshop__dropdown-item" role="menuitem" onClick={() => setMenuOpen(false)}>
                      <UserRound size={16} />
                      Hồ sơ cá nhân
                    </Link>
                    <Link to="/cart" className="cshop__dropdown-item" role="menuitem" onClick={() => setMenuOpen(false)}>
                      <ShoppingCart size={16} />
                      Giỏ hàng
                    </Link>
                    <button
                      type="button"
                      className="cshop__dropdown-item cshop__dropdown-item--danger"
                      role="menuitem"
                      onClick={handleLogout}
                    >
                      <LogOut size={16} />
                      Đăng xuất
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </nav>

      <main className="cshop__main">
        <Outlet />
      </main>

      <footer className="cshop__footer">
        <div className="cshop__foot-inner cshop__foot-inner--with-contact">
          <div className="cshop__foot-brand">
            <Link to="/" className="cshop__logo">
              <span className="cshop__logo-badge">
                <Sandwich size={22} />
              </span>
              <span className="cshop__logo-text">
                <span className="cshop__logo-name">{settings.siteName}</span>
                <span className="cshop__logo-tag">{settings.tagline}</span>
              </span>
            </Link>
            <p className="cshop__foot-desc">{settings.footerDescription}</p>
          </div>

          <div className="cshop__foot-col">
            <p className="cshop__foot-title">Khám phá</p>
            <Link to="/menu">Thực đơn</Link>
            <Link to="/stores">Hệ thống cửa hàng</Link>
            <Link to="/cart">Giỏ hàng</Link>
            <Link to="/orders">Đơn của tôi</Link>
            <Link to="/about">Giới thiệu</Link>
            <Link to="/contact">Liên hệ</Link>
            <Link to="/faq">Câu hỏi thường gặp</Link>
          </div>

          <div className="cshop__foot-col">
            <p className="cshop__foot-title">Phục vụ</p>
            <span className="cshop__foot-note">
              <Timer size={15} />
              Giao nội thành trong 30 phút
            </span>
            <span className="cshop__foot-note">
              <Bike size={15} />
              Miễn phí giao đơn từ 200.000đ
            </span>
            <span className="cshop__foot-note">
              <ShieldCheck size={15} />
              Nướng theo đơn, không làm sẵn
            </span>
          </div>

          <div className="cshop__foot-col">
              <p className="cshop__foot-title">Liên hệ</p>
              {settings.contactPhone && (
                <a className="cshop__foot-note" href={`tel:${settings.contactPhone.replace(/\s/g, '')}`}>
                  <Phone size={15} />
                  {settings.contactPhone}
                </a>
              )}
              {settings.contactEmail && (
                <a className="cshop__foot-note" href={`mailto:${settings.contactEmail}`}>
                  <Mail size={15} />
                  {settings.contactEmail}
                </a>
              )}
              {settings.contactAddress && (
                <span className="cshop__foot-note">
                  <MapPin size={15} />
                  {settings.contactAddress}
                </span>
              )}
              <Link className="cshop__foot-note" to="/stores">
                <MapPin size={15} />
                Xem hệ thống cửa hàng
              </Link>
            </div>
        </div>

        <div className="cshop__foot-bottom">
          <span>© {new Date().getFullYear()} {settings.siteName}</span>
          <span className="cshop__foot-legal">
            <Link to="/terms">Điều khoản</Link>
            <Link to="/privacy">Bảo mật</Link>
          </span>
          <span className="cshop__foot-pay">
            <span className="cshop__foot-pay-pill">Tiền mặt khi nhận hàng</span>
            <span className="cshop__foot-pay-pill">VietQR</span>
          </span>
        </div>
      </footer>

      {showFloatCart && (
        <Link to="/cart" className="cshop__float-cart">
          <span className="cshop__float-cart-icon">
            <ShoppingCart size={20} />
            <span className="cshop__float-cart-badge">{totalQuantity}</span>
          </span>
          <span className="cshop__float-cart-text">
            <span className="cshop__float-cart-label">Xem giỏ hàng</span>
            <span className="cshop__float-cart-total">{formatCurrency(subtotal)}</span>
          </span>
        </Link>
      )}
    </div>
  );
};
