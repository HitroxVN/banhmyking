import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, NavLink, Outlet, useNavigate, useSearchParams } from 'react-router-dom';
import { Home, LogOut, Receipt, Sandwich, Search, ShoppingCart, UserRound } from 'lucide-react';
import { useAuth } from '../../context/useAuth';
import { useCart } from '../../context/useCart';
import { useConfirm } from '../ui';

/** Khung trang dành cho khách: navbar sticky + footer. */
export const CustomerLayout = () => {
  const { user, logout } = useAuth();
  const { totalQuantity } = useCart();
  const confirm = useConfirm();
  const navigate = useNavigate();

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
      navigate(trimmed ? `/?keyword=${encodeURIComponent(trimmed)}` : '/', { replace: true });
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
    navigate(trimmed ? `/?keyword=${encodeURIComponent(trimmed)}` : '/');
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

  return (
    <div className="cshop">
      <nav className="cshop__nav">
        <div className="cshop__nav-inner">
          <Link to="/" className="cshop__logo">
            <span className="cshop__logo-badge">
              <Sandwich size={22} />
            </span>
            <span className="cshop__logo-name">Bánh Mỳ King</span>
          </Link>

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
            <NavLink
              to="/cart"
              className="cshop__icon-btn"
              title="Giỏ hàng"
              aria-label={`Giỏ hàng, ${totalQuantity} món`}
            >
              <ShoppingCart size={21} />
              {totalQuantity > 0 && <span className="cshop__cart-badge">{totalQuantity}</span>}
            </NavLink>

            <div className="cshop__user" ref={userRef}>
              <button
                type="button"
                className="cshop__avatar-btn"
                onClick={() => setMenuOpen((open) => !open)}
                aria-haspopup="menu"
                aria-expanded={menuOpen}
              >
                <span className="cshop__avatar">{user?.fullName?.charAt(0)?.toUpperCase() ?? 'K'}</span>
                <span>{user?.fullName?.split(' ').pop() ?? 'Khách'}</span>
              </button>

              {menuOpen && (
                <div className="cshop__dropdown" role="menu">
                  <div className="cshop__dropdown-head">
                    <span className="cshop__dropdown-name">{user?.fullName}</span>
                    <span className="cshop__dropdown-email">{user?.email}</span>
                  </div>

                  <Link to="/" className="cshop__dropdown-item" role="menuitem" onClick={() => setMenuOpen(false)}>
                    <Home size={16} />
                    Trang chủ
                  </Link>
                  <Link to="/orders" className="cshop__dropdown-item" role="menuitem" onClick={() => setMenuOpen(false)}>
                    <Receipt size={16} />
                    Đơn của tôi
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
          </div>
        </div>
      </nav>

      <main className="cshop__main">
        <Outlet />
      </main>

      <footer className="cshop__footer">
        © {new Date().getFullYear()} Bánh Mỳ King — bánh mì nóng giòn giao tận nơi.
      </footer>
    </div>
  );
};
