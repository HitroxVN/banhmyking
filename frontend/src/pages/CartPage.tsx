import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../hooks/useCart';
import { useAuth } from '../context/useAuth';
import { formatCurrency } from '../utils/formatters';
import type { CartItem } from '../types/cart';

// Món ăn demo có sẵn trong DB (DataInitializer) để người dùng thử nghiệm tính năng thêm món
const DEMO_PRODUCT = {
  id: 1,
  name: 'Bánh mì Pate Chả Lụa',
  price: 30000,
  imageUrl: 'https://images.unsplash.com/photo-1626804475297-41608ea09aeb?auto=format&fit=crop&w=400&q=80',
  description: 'Bánh mì giòn kẹp pate gan béo ngậy và chả lụa hảo hạng',
  options: [
    { id: 1, name: 'Thêm pate', extraPrice: 5000 },
    { id: 2, name: 'Thêm chả lụa', extraPrice: 8000 },
    { id: 3, name: 'Thêm trứng ốp la', extraPrice: 7000 },
  ],
};

const FREESHIP_THRESHOLD = 200000;

export const CartPage: React.FC = () => {
  const { cart, isLoading, isUpdating, error, subtotal, totalQuantity, updateQuantity, removeItem, clearCart, addItem } = useCart();
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  // State cho Quick-Add Demo
  const [selectedOptions, setSelectedOptions] = useState<number[]>([]);
  const [demoQuantity, setDemoQuantity] = useState<number>(1);
  const [isAddingDemo, setIsAddingDemo] = useState<boolean>(false);
  const [toastMessage, setToastMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setToastMessage({ type, text });
    setTimeout(() => {
      setToastMessage(null);
    }, 3500);
  };

  const handleIncrement = async (item: CartItem) => {
    try {
      await updateQuantity(item.id, item.quantity + 1);
    } catch {
      showToast('Không thể tăng số lượng món ăn', 'error');
    }
  };

  const handleDecrement = async (item: CartItem) => {
    if (item.quantity <= 1) {
      const confirmDelete = window.confirm(`Bạn có chắc muốn xóa "${item.productName}" khỏi giỏ hàng?`);
      if (confirmDelete) {
        try {
          await removeItem(item.id);
          showToast(`Đã xóa "${item.productName}" khỏi giỏ hàng`);
        } catch {
          showToast('Không thể xóa món ăn', 'error');
        }
      }
      return;
    }

    try {
      await updateQuantity(item.id, item.quantity - 1);
    } catch {
      showToast('Không thể giảm số lượng món ăn', 'error');
    }
  };

  const handleRemoveItem = async (item: CartItem) => {
    const confirmDelete = window.confirm(`Bạn có chắc muốn xóa "${item.productName}" khỏi giỏ hàng?`);
    if (!confirmDelete) return;

    try {
      await removeItem(item.id);
      showToast(`Đã xóa "${item.productName}" khỏi giỏ hàng`);
    } catch {
      showToast('Không thể xóa món ăn', 'error');
    }
  };

  const handleClearAll = async () => {
    if (!cart || cart.items.length === 0) return;
    const confirmClear = window.confirm('Bạn có chắc chắn muốn xóa toàn bộ món ăn trong giỏ hàng không?');
    if (!confirmClear) return;

    try {
      await clearCart();
      showToast('Đã xóa sạch giỏ hàng');
    } catch {
      showToast('Không thể xóa giỏ hàng', 'error');
    }
  };

  const toggleOption = (optId: number) => {
    setSelectedOptions((prev) =>
      prev.includes(optId) ? prev.filter((id) => id !== optId) : [...prev, optId]
    );
  };

  const handleQuickAdd = async () => {
    setIsAddingDemo(true);
    try {
      await addItem(DEMO_PRODUCT.id, demoQuantity, selectedOptions);
      showToast(`Đã thêm ${demoQuantity}x "${DEMO_PRODUCT.name}" vào giỏ hàng thành công!`);
      // Reset options
      setSelectedOptions([]);
      setDemoQuantity(1);
    } catch {
      showToast('Thêm món vào giỏ hàng thất bại', 'error');
    } finally {
      setIsAddingDemo(false);
    }
  };

  const freeshipRemaining = Math.max(0, FREESHIP_THRESHOLD - subtotal);
  const freeshipProgress = Math.min(100, (subtotal / FREESHIP_THRESHOLD) * 100);

  return (
    <div className="cart-page-wrapper">
      {/* Toast Notification */}
      {toastMessage && (
        <div className={`toast-notification toast-${toastMessage.type}`}>
          <span>{toastMessage.type === 'success' ? '✅' : '❌'}</span>
          <span>{toastMessage.text}</span>
        </div>
      )}

      {/* Top Navbar */}
      <header className="home-navbar">
        <div className="navbar-brand" onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>
          <div className="navbar-brand-badge">🥖</div>
          <span className="navbar-brand-name">BÁNH MỲ KING</span>
        </div>

        <div className="navbar-user-actions">
          <button
            type="button"
            className="btn-nav-secondary"
            onClick={() => navigate('/')}
            title="Quay lại trang cá nhân"
          >
            🏠 Trang chủ
          </button>

          <div className="user-profile-badge">
            <div className="user-avatar">
              {user?.fullName?.charAt(0)?.toUpperCase() || 'U'}
            </div>
            <span className="user-display-name">{user?.fullName || 'Khách hàng'}</span>
          </div>

          <button
            id="btn-logout-cart"
            type="button"
            className="btn-logout"
            onClick={async () => {
              if (window.confirm('Bạn có chắc chắn muốn đăng xuất?')) {
                await logout();
                navigate('/login');
              }
            }}
          >
            🚪 Thoát
          </button>
        </div>
      </header>

      {/* Main Cart Container */}
      <main className="cart-main-container">
        {/* Breadcrumb & Title */}
        <div className="cart-header-row">
          <div>
            <button type="button" className="btn-back-link" onClick={() => navigate('/')}>
              ← Tiếp tục xem thực đơn
            </button>
            <h1 className="cart-page-title">
              🛒 Giỏ hàng của bạn
              {totalQuantity > 0 && <span className="cart-count-badge">{totalQuantity} món</span>}
            </h1>
          </div>

          {cart && cart.items.length > 0 && (
            <button
              id="btn-clear-cart"
              type="button"
              className="btn-clear-cart"
              onClick={handleClearAll}
              disabled={isUpdating}
            >
              🗑️ Xóa toàn bộ giỏ
            </button>
          )}
        </div>

        {/* Global Error Banner */}
        {error && (
          <div className="alert-banner alert-error" style={{ marginBottom: '1.5rem' }}>
            <span>⚠️</span>
            <span>{error}</span>
          </div>
        )}

        {/* Loading Spinner for Initial Fetch */}
        {isLoading && (
          <div className="cart-loading-state">
            <div className="spinner" style={{ width: '40px', height: '40px', borderWidth: '3px' }}></div>
            <p>Đang tải giỏ hàng từ máy chủ...</p>
          </div>
        )}

        {/* Content Layout */}
        {!isLoading && (
          <div className="cart-layout-grid">
            {/* Left Column: Cart Items List & Quick Add */}
            <div className="cart-items-column">
              {(!cart || cart.items.length === 0) ? (
                <div className="empty-cart-card">
                  <div className="empty-cart-illustration">🛍️</div>
                  <h2 className="empty-cart-title">Giỏ hàng của bạn đang trống</h2>
                  <p className="empty-cart-desc">
                    Hãy thêm những ổ bánh mì thơm giòn, ngập tràn topping hảo hạng của Bánh Mỳ King vào giỏ hàng ngay nhé!
                  </p>
                  <button
                    type="button"
                    className="btn-primary"
                    style={{ width: 'auto', padding: '0.85rem 1.75rem' }}
                    onClick={() => {
                      // Scroll to quick-add section
                      const elem = document.getElementById('quick-add-section');
                      elem?.scrollIntoView({ behavior: 'smooth' });
                    }}
                  >
                    👇 Thêm món ăn mẫu để thử nghiệm
                  </button>
                </div>
              ) : (
                <div className="cart-items-list">
                  {cart.items.map((item) => (
                    <div key={item.id} className="cart-item-card" id={`cart-item-${item.id}`}>
                      {/* Product Thumbnail */}
                      <div className="cart-item-image-wrapper">
                        <img
                          src={item.productImageUrl || 'https://images.unsplash.com/photo-1626804475297-41608ea09aeb?auto=format&fit=crop&w=200&q=80'}
                          alt={item.productName}
                          className="cart-item-image"
                          onError={(e) => {
                            // Fallback image if remote url fails
                            (e.target as HTMLImageElement).src = 'https://images.unsplash.com/photo-1626804475297-41608ea09aeb?auto=format&fit=crop&w=200&q=80';
                          }}
                        />
                      </div>

                      {/* Product Details */}
                      <div className="cart-item-info">
                        <h3 className="cart-item-name">{item.productName}</h3>

                        <div className="cart-item-unit-price">
                          Đơn giá: <strong>{formatCurrency(item.unitPrice || item.basePrice)}</strong>
                        </div>

                        {/* Selected Options Badges */}
                        {item.options && item.options.length > 0 ? (
                          <div className="cart-item-options-list">
                            {item.options.map((opt) => (
                              <span key={opt.id || opt.productOptionId} className="option-badge">
                                + {opt.name} ({formatCurrency(opt.extraPrice)})
                              </span>
                            ))}
                          </div>
                        ) : (
                          <div className="cart-item-no-options">Vị truyền thống</div>
                        )}
                      </div>

                      {/* Quantity Controller & Price Action */}
                      <div className="cart-item-actions">
                        <div className="quantity-stepper">
                          <button
                            id={`btn-dec-${item.id}`}
                            type="button"
                            className="btn-step"
                            onClick={() => handleDecrement(item)}
                            disabled={isUpdating}
                            aria-label="Giảm số lượng"
                          >
                            -
                          </button>
                          <span id={`qty-${item.id}`} className="quantity-number">
                            {item.quantity}
                          </span>
                          <button
                            id={`btn-inc-${item.id}`}
                            type="button"
                            className="btn-step"
                            onClick={() => handleIncrement(item)}
                            disabled={isUpdating}
                            aria-label="Tăng số lượng"
                          >
                            +
                          </button>
                        </div>

                        {/* Subtotal for this Item */}
                        <div className="cart-item-subtotal-box">
                          <div className="subtotal-label">Thành tiền</div>
                          <div className="subtotal-amount">
                            {formatCurrency(item.subtotal || (item.unitPrice || item.basePrice) * item.quantity)}
                          </div>
                        </div>

                        {/* Delete Button */}
                        <button
                          id={`btn-remove-${item.id}`}
                          type="button"
                          className="btn-remove-item"
                          onClick={() => handleRemoveItem(item)}
                          disabled={isUpdating}
                          title="Xóa món ăn này"
                        >
                          🗑️
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Quick-Add Demo Section (Allows testing add-to-cart directly without extra pages) */}
              <section id="quick-add-section" className="quick-add-card">
                <div className="quick-add-header">
                  <div>
                    <span className="quick-add-tag">⭐ Thử nghiệm nhanh</span>
                    <h3 className="quick-add-title">Thêm món ăn mẫu từ Thực đơn</h3>
                    <p className="quick-add-subtitle">
                      Món <strong>{DEMO_PRODUCT.name}</strong> đã có sẵn trong cơ sở dữ liệu để bạn test realtime:
                    </p>
                  </div>
                  <div className="quick-add-price">
                    {formatCurrency(DEMO_PRODUCT.price)}
                  </div>
                </div>

                <div className="quick-add-body">
                  <div className="toppings-section-title">Chọn tùy chọn thêm (Toppings):</div>
                  <div className="toppings-grid">
                    {DEMO_PRODUCT.options.map((opt) => {
                      const isChecked = selectedOptions.includes(opt.id);
                      return (
                        <label
                          key={opt.id}
                          className={`topping-checkbox-card ${isChecked ? 'active' : ''}`}
                        >
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() => toggleOption(opt.id)}
                            style={{ display: 'none' }}
                          />
                          <div className="topping-checkbox-indicator">
                            {isChecked ? '✓' : ''}
                          </div>
                          <div>
                            <div className="topping-name">{opt.name}</div>
                            <div className="topping-price">+{formatCurrency(opt.extraPrice)}</div>
                          </div>
                        </label>
                      );
                    })}
                  </div>

                  <div className="quick-add-actions-row">
                    <div className="demo-quantity-picker">
                      <span style={{ fontSize: '0.9rem', color: 'var(--stone-600)', fontWeight: 600 }}>
                        Số lượng:
                      </span>
                      <div className="quantity-stepper">
                        <button
                          type="button"
                          className="btn-step"
                          onClick={() => setDemoQuantity((q) => Math.max(1, q - 1))}
                        >
                          -
                        </button>
                        <span className="quantity-number">{demoQuantity}</span>
                        <button
                          type="button"
                          className="btn-step"
                          onClick={() => setDemoQuantity((q) => q + 1)}
                        >
                          +
                        </button>
                      </div>
                    </div>

                    <button
                      id="btn-quick-add"
                      type="button"
                      className="btn-primary"
                      style={{ width: 'auto', padding: '0.75rem 1.5rem', whiteSpace: 'nowrap' }}
                      onClick={handleQuickAdd}
                      disabled={isAddingDemo || isUpdating}
                    >
                      {isAddingDemo ? 'Đang thêm...' : '🛒 Thêm vào giỏ ngay'}
                    </button>
                  </div>
                </div>
              </section>
            </div>

            {/* Right Column: Order Summary & Checkout Card */}
            <div className="cart-summary-column">
              <div className="order-summary-card">
                <h2 className="summary-card-title">Tóm tắt đơn hàng</h2>

                {/* Freeship Progress Bar */}
                <div className="freeship-progress-box">
                  <div className="freeship-progress-header">
                    {freeshipRemaining > 0 ? (
                      <span>
                        Mua thêm <strong>{formatCurrency(freeshipRemaining)}</strong> để được <strong>FREESHIP</strong>
                      </span>
                    ) : (
                      <span style={{ color: '#059669', fontWeight: 700 }}>
                        🎉 Bạn đã được MIỄN PHÍ VẬN CHUYỂN!
                      </span>
                    )}
                  </div>
                  <div className="freeship-progress-track">
                    <div
                      className="freeship-progress-bar"
                      style={{ width: `${freeshipProgress}%` }}
                    ></div>
                  </div>
                </div>

                {/* Subtotal & Realtime Calculations */}
                <div className="summary-calculation-rows">
                  <div className="summary-row">
                    <span className="summary-label">Tổng số lượng món:</span>
                    <span className="summary-value" id="summary-total-qty">
                      {totalQuantity} món
                    </span>
                  </div>

                  <div className="summary-row">
                    <span className="summary-label">Tạm tính:</span>
                    <span className="summary-value highlight" id="summary-subtotal">
                      {formatCurrency(subtotal)}
                    </span>
                  </div>

                  <div className="summary-row">
                    <span className="summary-label">Phí vận chuyển:</span>
                    <span className="summary-value" style={{ color: 'var(--stone-600)', fontSize: '0.9rem' }}>
                      {freeshipRemaining === 0 ? (
                        <span style={{ color: '#059669', fontWeight: 700 }}>Miễn phí (Freeship)</span>
                      ) : (
                        'Tính tại bước thanh toán'
                      )}
                    </span>
                  </div>
                </div>

                <div className="summary-divider"></div>

                {/* Grand Total */}
                <div className="summary-grand-total-row">
                  <div>
                    <div className="grand-total-label">Tổng thanh toán dự kiến</div>
                    <div className="grand-total-subtext">(Đã bao gồm VAT và phụ thu)</div>
                  </div>
                  <div className="grand-total-price" id="summary-grand-total">
                    {formatCurrency(subtotal)}
                  </div>
                </div>

                {/* Checkout CTA */}
                <button
                  id="btn-checkout"
                  type="button"
                  className="btn-checkout"
                  disabled={!cart || cart.items.length === 0 || isUpdating}
                  onClick={() => {
                    alert(`Đơn hàng của bạn trị giá ${formatCurrency(subtotal)}. Tính năng thanh toán và tạo đơn hàng sẽ tiếp tục ở module Checkout!`);
                  }}
                >
                  🚀 Tiến hành đặt hàng ({formatCurrency(subtotal)})
                </button>

                {/* Trust Badges */}
                <div className="cart-trust-badges">
                  <div className="trust-badge-item">
                    <span>⚡</span> Giao nhanh nóng giòn 30 phút
                  </div>
                  <div className="trust-badge-item">
                    <span>🔒</span> Thanh toán an toàn bảo mật
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};
