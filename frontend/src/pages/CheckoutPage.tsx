import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../hooks/useCart';
import { useAuth } from '../context/useAuth';
import { addressApi } from '../api/addressApi';
import { orderApi } from '../api/orderApi';
import { formatCurrency } from '../utils/formatters';
import type { AddressResponse } from '../types/address';
import type { CreateOrderRequest, PaymentMethod } from '../types/order';

interface FormErrors {
  receiverName?: string;
  receiverPhone?: string;
  shippingAddress?: string;
}

export const CheckoutPage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { cart, isLoading: isCartLoading, refreshCart, subtotal, totalQuantity } = useCart();

  // State cho sổ địa chỉ đã lưu
  const [savedAddresses, setSavedAddresses] = useState<AddressResponse[]>([]);
  const [isLoadingAddresses, setIsLoadingAddresses] = useState<boolean>(true);
  const [addressMode, setAddressMode] = useState<'saved' | 'new'>('new');
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);

  // Form State
  const [receiverName, setReceiverName] = useState<string>('');
  const [receiverPhone, setReceiverPhone] = useState<string>('');
  const [shippingAddress, setShippingAddress] = useState<string>('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('COD');
  const [promotionCode, setPromotionCode] = useState<string>('');
  const [note, setNote] = useState<string>('');

  // Validation & Submit State
  const [errors, setErrors] = useState<FormErrors>({});
  const [touched, setTouched] = useState<{ [key: string]: boolean }>({});
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [apiError, setApiError] = useState<string | null>(null);

  // Tải danh sách địa chỉ đã lưu khi mount
  useEffect(() => {
    let isMounted = true;
    const fetchAddresses = async () => {
      try {
        const addresses = await addressApi.getAddresses();
        if (isMounted) {
          setSavedAddresses(addresses);
          if (addresses.length > 0) {
            setAddressMode('saved');
            // Ưu tiên địa chỉ mặc định, hoặc địa chỉ đầu tiên
            const defaultAddr = addresses.find((a) => a.defaultAddress) || addresses[0];
            setSelectedAddressId(defaultAddr.id);
            setReceiverName(defaultAddr.receiverName);
            setReceiverPhone(defaultAddr.receiverPhone);
            setShippingAddress(defaultAddr.fullAddress);
          } else {
            // Chưa có địa chỉ lưu -> Điền mặc định từ thông tin User
            setAddressMode('new');
            if (user?.fullName) setReceiverName(user.fullName);
            if (user?.phone) setReceiverPhone(user.phone);
          }
        }
      } catch {
        if (isMounted) {
          setAddressMode('new');
          if (user?.fullName) setReceiverName(user.fullName);
          if (user?.phone) setReceiverPhone(user.phone);
        }
      } finally {
        if (isMounted) setIsLoadingAddresses(false);
      }
    };

    fetchAddresses();
    return () => {
      isMounted = false;
    };
  }, [user]);

  // Xử lý chọn địa chỉ đã lưu
  const handleSelectSavedAddress = (addr: AddressResponse) => {
    setSelectedAddressId(addr.id);
    setReceiverName(addr.receiverName);
    setReceiverPhone(addr.receiverPhone);
    setShippingAddress(addr.fullAddress);
    // Xóa lỗi validation nếu có
    setErrors({});
  };

  // Chuyển sang nhập địa chỉ mới
  const handleSwitchToNewAddress = () => {
    setAddressMode('new');
    setSelectedAddressId(null);
    setReceiverName(user?.fullName || '');
    setReceiverPhone(user?.phone || '');
    setShippingAddress('');
    setErrors({});
    setTouched({});
  };

  // Validation logic
  const validateField = (name: string, value: string): string | undefined => {
    const trimmed = value.trim();
    if (name === 'receiverName') {
      if (!trimmed) return 'Vui lòng nhập họ và tên người nhận';
      if (trimmed.length < 2) return 'Họ và tên người nhận tối thiểu 2 ký tự';
    }
    if (name === 'receiverPhone') {
      if (!trimmed) return 'Vui lòng nhập số điện thoại nhận hàng';
      const phoneRegex = /^(0[3|5|7|8|9])[0-9]{8}$/;
      if (!phoneRegex.test(trimmed)) {
        return 'Số điện thoại không hợp lệ (10 chữ số, bắt đầu bằng 03, 05, 07, 08, 09)';
      }
    }
    if (name === 'shippingAddress') {
      if (!trimmed) return 'Vui lòng nhập địa chỉ nhận hàng chi tiết';
      if (trimmed.length < 5) return 'Địa chỉ nhận hàng quá ngắn (tối thiểu 5 ký tự)';
    }
    return undefined;
  };

  const validateForm = (): boolean => {
    const newErrors: FormErrors = {};
    const nameErr = validateField('receiverName', receiverName);
    if (nameErr) newErrors.receiverName = nameErr;

    const phoneErr = validateField('receiverPhone', receiverPhone);
    if (phoneErr) newErrors.receiverPhone = phoneErr;

    const addressErr = validateField('shippingAddress', shippingAddress);
    if (addressErr) newErrors.shippingAddress = addressErr;

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleBlur = (field: string) => {
    setTouched((prev) => ({ ...prev, [field]: true }));
    let val = '';
    if (field === 'receiverName') val = receiverName;
    if (field === 'receiverPhone') val = receiverPhone;
    if (field === 'shippingAddress') val = shippingAddress;

    const err = validateField(field, val);
    setErrors((prev) => ({ ...prev, [field]: err }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setApiError(null);

    // Validate form
    setTouched({
      receiverName: true,
      receiverPhone: true,
      shippingAddress: true,
    });

    if (!validateForm()) {
      return;
    }

    if (!cart || cart.items.length === 0) {
      setApiError('Giỏ hàng của bạn đang trống. Vui lòng thêm món trước khi đặt hàng.');
      return;
    }

    setIsSubmitting(true);

    try {
      const payload: CreateOrderRequest = {
        addressId: addressMode === 'saved' && selectedAddressId ? selectedAddressId : undefined,
        receiverName: receiverName.trim(),
        receiverPhone: receiverPhone.trim(),
        shippingAddress: shippingAddress.trim(),
        paymentMethod,
        promotionCode: promotionCode.trim() ? promotionCode.trim().toUpperCase() : undefined,
        note: note.trim() ? note.trim() : undefined,
      };

      const createdOrder = await orderApi.createOrder(payload);

      // Đồng bộ xóa giỏ hàng phía frontend sau khi đơn hàng được tạo thành công
      await refreshCart();

      // Điều hướng chuẩn xác sang trang thanh toán
      navigate(`/payment/${createdOrder.orderCode}`, {
        state: { order: createdOrder },
        replace: true,
      });
    } catch (err: unknown) {
      // Bắt thông điệp lỗi nghiệp vụ từ backend
      interface AxiosErrorPayload {
        response?: {
          data?: {
            message?: string;
            errors?: Record<string, string>;
          };
        };
      }
      const axiosErr = err as AxiosErrorPayload;
      const message =
        axiosErr.response?.data?.message ||
        'Không thể tạo đơn hàng. Vui lòng kiểm tra lại thông tin và thử lại.';
      setApiError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const isInitialLoading = isCartLoading || isLoadingAddresses;

  return (
    <div className="checkout-page-wrapper">
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
            onClick={() => navigate('/cart')}
            title="Quay lại giỏ hàng"
          >
            🛒 Giỏ hàng ({totalQuantity})
          </button>
        </div>
      </header>

      {/* Main Container */}
      <main className="checkout-main-container">
        {/* Breadcrumb Header */}
        <div className="checkout-header-row">
          <button type="button" className="btn-back-link" onClick={() => navigate('/cart')}>
            ← Quay lại giỏ hàng
          </button>
          <h1 className="checkout-page-title">📦 Thông tin giao hàng & Đặt món</h1>
        </div>

        {/* Global Alert Error */}
        {apiError && (
          <div className="alert-banner alert-error" style={{ marginBottom: '1.5rem' }}>
            <span>⚠️</span>
            <span>{apiError}</span>
          </div>
        )}

        {/* Loading Skeleton */}
        {isInitialLoading && (
          <div className="checkout-skeleton-grid">
            <div className="skeleton-card" style={{ height: '360px' }}>
              <div className="skeleton-line" style={{ width: '40%', height: '24px', marginBottom: '1.5rem' }}></div>
              <div className="skeleton-line" style={{ width: '100%', height: '48px', marginBottom: '1rem' }}></div>
              <div className="skeleton-line" style={{ width: '100%', height: '48px', marginBottom: '1rem' }}></div>
              <div className="skeleton-line" style={{ width: '100%', height: '80px' }}></div>
            </div>
            <div className="skeleton-card" style={{ height: '280px' }}>
              <div className="skeleton-line" style={{ width: '50%', height: '24px', marginBottom: '1.5rem' }}></div>
              <div className="skeleton-line" style={{ width: '100%', height: '36px', marginBottom: '0.75rem' }}></div>
              <div className="skeleton-line" style={{ width: '100%', height: '36px', marginBottom: '0.75rem' }}></div>
              <div className="skeleton-line" style={{ width: '100%', height: '48px' }}></div>
            </div>
          </div>
        )}

        {/* Empty Cart Notice */}
        {!isInitialLoading && (!cart || cart.items.length === 0) && (
          <div className="empty-cart-card">
            <div className="empty-cart-illustration">🛒</div>
            <h2 className="empty-cart-title">Giỏ hàng của bạn đang trống</h2>
            <p className="empty-cart-desc">
              Vui lòng chọn các món ăn yêu thích tại thực đơn trước khi tiến hành thanh toán nhé!
            </p>
            <button
              type="button"
              className="btn-primary"
              style={{ width: 'auto', padding: '0.85rem 1.75rem' }}
              onClick={() => navigate('/cart')}
            >
              ← Quay lại xem giỏ hàng
            </button>
          </div>
        )}

        {/* Main Checkout Form Layout */}
        {!isInitialLoading && cart && cart.items.length > 0 && (
          <form className="checkout-layout-grid" onSubmit={handleSubmit} noValidate>
            {/* Left Column: Delivery Info & Payment Methods */}
            <div className="checkout-form-column">
              {/* Card 1: Thông tin nhận hàng */}
              <div className="checkout-section-card">
                <div className="section-card-header">
                  <div className="section-icon-badge">📍</div>
                  <div>
                    <h2 className="section-card-title">Địa chỉ nhận hàng</h2>
                    <p className="section-card-subtitle">Vui lòng cung cấp địa chỉ chính xác để shipper giao nhanh nhất</p>
                  </div>
                </div>

                {/* Chọn địa chỉ đã lưu hoặc Nhập mới nếu có sổ địa chỉ */}
                {savedAddresses.length > 0 && (
                  <div className="address-tabs-selector">
                    <button
                      type="button"
                      className={`address-tab-btn ${addressMode === 'saved' ? 'active' : ''}`}
                      onClick={() => setAddressMode('saved')}
                    >
                      🔖 Chọn địa chỉ đã lưu ({savedAddresses.length})
                    </button>
                    <button
                      type="button"
                      className={`address-tab-btn ${addressMode === 'new' ? 'active' : ''}`}
                      onClick={handleSwitchToNewAddress}
                    >
                      ➕ Nhập địa chỉ mới
                    </button>
                  </div>
                )}

                {/* Danh sách địa chỉ đã lưu */}
                {addressMode === 'saved' && savedAddresses.length > 0 && (
                  <div className="saved-addresses-list">
                    {savedAddresses.map((addr) => {
                      const isSelected = selectedAddressId === addr.id;
                      return (
                        <div
                          key={addr.id}
                          className={`saved-address-item ${isSelected ? 'selected' : ''}`}
                          onClick={() => handleSelectSavedAddress(addr)}
                        >
                          <div className="saved-address-radio">
                            <span className="radio-dot">{isSelected ? '●' : '○'}</span>
                          </div>
                          <div className="saved-address-details">
                            <div className="saved-address-name-row">
                              <span className="saved-name">{addr.receiverName}</span>
                              <span className="saved-phone">({addr.receiverPhone})</span>
                              {addr.defaultAddress && (
                                <span className="saved-default-badge">Mặc định</span>
                              )}
                            </div>
                            <div className="saved-address-full">{addr.fullAddress}</div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}

                {/* Form fields nhập thông tin (hiển thị khi nhập mới hoặc khi chỉnh sửa) */}
                <div className="form-fields-stack">
                  {/* Họ tên & SĐT */}
                  <div className="form-row-two-cols">
                    <div className="form-group">
                      <label className="form-label" htmlFor="receiverName">
                        Họ và tên người nhận <span className="required-star">*</span>
                      </label>
                      <input
                        id="receiverName"
                        type="text"
                        className={`form-input ${touched.receiverName && errors.receiverName ? 'input-error' : ''}`}
                        placeholder="Ví dụ: Nguyễn Văn A"
                        value={receiverName}
                        onChange={(e) => {
                          setReceiverName(e.target.value);
                          if (touched.receiverName) {
                            setErrors((prev) => ({
                              ...prev,
                              receiverName: validateField('receiverName', e.target.value),
                            }));
                          }
                        }}
                        onBlur={() => handleBlur('receiverName')}
                        disabled={isSubmitting}
                      />
                      {touched.receiverName && errors.receiverName && (
                        <span className="field-error-text" id="receiverName-error">
                          {errors.receiverName}
                        </span>
                      )}
                    </div>

                    <div className="form-group">
                      <label className="form-label" htmlFor="receiverPhone">
                        Số điện thoại nhận hàng <span className="required-star">*</span>
                      </label>
                      <input
                        id="receiverPhone"
                        type="tel"
                        className={`form-input ${touched.receiverPhone && errors.receiverPhone ? 'input-error' : ''}`}
                        placeholder="Ví dụ: 0901234567"
                        value={receiverPhone}
                        onChange={(e) => {
                          setReceiverPhone(e.target.value);
                          if (touched.receiverPhone) {
                            setErrors((prev) => ({
                              ...prev,
                              receiverPhone: validateField('receiverPhone', e.target.value),
                            }));
                          }
                        }}
                        onBlur={() => handleBlur('receiverPhone')}
                        disabled={isSubmitting}
                      />
                      {touched.receiverPhone && errors.receiverPhone && (
                        <span className="field-error-text" id="receiverPhone-error">
                          {errors.receiverPhone}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Địa chỉ chi tiết */}
                  <div className="form-group">
                    <label className="form-label" htmlFor="shippingAddress">
                      Địa chỉ nhận hàng chi tiết <span className="required-star">*</span>
                    </label>
                    <textarea
                      id="shippingAddress"
                      rows={3}
                      className={`form-input form-textarea ${touched.shippingAddress && errors.shippingAddress ? 'input-error' : ''}`}
                      placeholder="Số nhà, tên tòa nhà, tên đường, phường/xã, quận/huyện, TP.HCM..."
                      value={shippingAddress}
                      onChange={(e) => {
                        setShippingAddress(e.target.value);
                        if (touched.shippingAddress) {
                          setErrors((prev) => ({
                            ...prev,
                            shippingAddress: validateField('shippingAddress', e.target.value),
                          }));
                        }
                      }}
                      onBlur={() => handleBlur('shippingAddress')}
                      disabled={isSubmitting}
                    />
                    {touched.shippingAddress && errors.shippingAddress && (
                      <span className="field-error-text" id="shippingAddress-error">
                        {errors.shippingAddress}
                      </span>
                    )}
                  </div>

                  {/* Ghi chú đơn hàng */}
                  <div className="form-group">
                    <label className="form-label" htmlFor="orderNote">
                      Ghi chú đơn hàng (Tùy chọn)
                    </label>
                    <input
                      id="orderNote"
                      type="text"
                      className="form-input"
                      placeholder="Ví dụ: Giao trước 12h, không cay, xin thêm túi..."
                      value={note}
                      onChange={(e) => setNote(e.target.value)}
                      disabled={isSubmitting}
                    />
                  </div>
                </div>
              </div>

              {/* Card 2: Phương thức thanh toán */}
              <div className="checkout-section-card">
                <div className="section-card-header">
                  <div className="section-icon-badge">💳</div>
                  <div>
                    <h2 className="section-card-title">Phương thức thanh toán</h2>
                    <p className="section-card-subtitle">Lựa chọn hình thức thanh toán thuận tiện nhất cho bạn</p>
                  </div>
                </div>

                <div className="payment-options-stack">
                  {/* COD */}
                  <label
                    className={`payment-option-card ${paymentMethod === 'COD' ? 'active' : ''}`}
                    onClick={() => setPaymentMethod('COD')}
                  >
                    <input
                      type="radio"
                      name="paymentMethod"
                      value="COD"
                      checked={paymentMethod === 'COD'}
                      onChange={() => setPaymentMethod('COD')}
                      style={{ display: 'none' }}
                      disabled={isSubmitting}
                    />
                    <div className="payment-option-radio">
                      <span className="radio-dot">{paymentMethod === 'COD' ? '●' : '○'}</span>
                    </div>
                    <div className="payment-option-content">
                      <div className="payment-option-title-row">
                        <span className="payment-icon">💵</span>
                        <span className="payment-option-name">Thanh toán khi nhận hàng (COD)</span>
                        <span className="payment-badge-popular">Phổ biến</span>
                      </div>
                      <p className="payment-option-desc">
                        Thanh toán bằng tiền mặt trực tiếp cho shipper khi nhận được ổ bánh mì nóng giòn.
                      </p>
                    </div>
                  </label>

                  {/* BANK TRANSFER (VietQR) */}
                  <label
                    className={`payment-option-card ${paymentMethod === 'BANK_TRANSFER' ? 'active' : ''}`}
                    onClick={() => setPaymentMethod('BANK_TRANSFER')}
                  >
                    <input
                      type="radio"
                      name="paymentMethod"
                      value="BANK_TRANSFER"
                      checked={paymentMethod === 'BANK_TRANSFER'}
                      onChange={() => setPaymentMethod('BANK_TRANSFER')}
                      style={{ display: 'none' }}
                      disabled={isSubmitting}
                    />
                    <div className="payment-option-radio">
                      <span className="radio-dot">{paymentMethod === 'BANK_TRANSFER' ? '●' : '○'}</span>
                    </div>
                    <div className="payment-option-content">
                      <div className="payment-option-title-row">
                        <span className="payment-icon">📲</span>
                        <span className="payment-option-name">Chuyển khoản Ngân hàng / Quét mã VietQR</span>
                        <span className="payment-badge-fast">Tiện lợi 24/7</span>
                      </div>
                      <p className="payment-option-desc">
                        Chuyển khoản nhanh qua mã QR tự động điền sẵn số tiền và mã đơn hàng sau khi đặt.
                      </p>
                    </div>
                  </label>
                </div>
              </div>
            </div>

            {/* Right Column: Order Review & Submit */}
            <div className="checkout-summary-column">
              <div className="order-summary-card">
                <h2 className="summary-card-title">Đơn hàng của bạn ({totalQuantity} món)</h2>

                {/* Items Mini List */}
                <div className="checkout-mini-items-list">
                  {cart.items.map((item) => (
                    <div key={item.id} className="checkout-mini-item">
                      <div className="checkout-mini-item-info">
                        <div className="checkout-mini-item-title">
                          <span className="mini-qty">{item.quantity}x</span>
                          <span className="mini-name">{item.productName}</span>
                        </div>
                        {item.options && item.options.length > 0 && (
                          <div className="checkout-mini-toppings">
                            + {item.options.map((o) => `${o.name} (${formatCurrency(o.extraPrice)})`).join(', ')}
                          </div>
                        )}
                      </div>
                      <div className="checkout-mini-item-price">
                        {formatCurrency(item.subtotal)}
                      </div>
                    </div>
                  ))}
                </div>

                <div className="summary-divider"></div>

                {/* Voucher / Promo code */}
                <div className="promo-input-wrapper">
                  <input
                    type="text"
                    className="promo-input"
                    placeholder="Mã ưu đãi (Ví dụ: BANHMYKING10)"
                    value={promotionCode}
                    onChange={(e) => setPromotionCode(e.target.value)}
                    disabled={isSubmitting}
                  />
                </div>

                <div className="summary-calculation-rows">
                  <div className="summary-row">
                    <span className="summary-label">Tạm tính:</span>
                    <span className="summary-value">{formatCurrency(subtotal)}</span>
                  </div>
                  <div className="summary-row">
                    <span className="summary-label">Phí vận chuyển:</span>
                    <span className="summary-value" style={{ color: '#059669', fontWeight: 600 }}>
                      Miễn phí (Freeship)
                    </span>
                  </div>
                </div>

                <div className="summary-divider"></div>

                {/* Grand Total */}
                <div className="summary-grand-total-row">
                  <div>
                    <div className="grand-total-label">Tổng thanh toán</div>
                    <div className="grand-total-subtext">Đã bao gồm VAT và ưu đãi</div>
                  </div>
                  <div className="grand-total-price" id="checkout-total-price">
                    {formatCurrency(subtotal)}
                  </div>
                </div>

                {/* Submit Button with Loading State */}
                <button
                  id="btn-submit-order"
                  type="submit"
                  className="btn-submit-order"
                  disabled={isSubmitting || !cart || cart.items.length === 0}
                >
                  {isSubmitting ? (
                    <div className="submit-spinner-box">
                      <div className="spinner" style={{ width: '20px', height: '20px', borderWidth: '2.5px' }}></div>
                      <span>Đang khởi tạo đơn hàng...</span>
                    </div>
                  ) : (
                    <span>🚀 Xác nhận đặt hàng ({formatCurrency(subtotal)})</span>
                  )}
                </button>

                {/* Trust Badges */}
                <div className="cart-trust-badges" style={{ marginTop: '1rem' }}>
                  <div className="trust-badge-item">
                    <span>⚡</span> Chuẩn bị và giao nóng giòn 30 phút
                  </div>
                  <div className="trust-badge-item">
                    <span>🛡️</span> Cam kết hoàn tiền 100% nếu không hài lòng
                  </div>
                </div>
              </div>
            </div>
          </form>
        )}
      </main>
    </div>
  );
};
