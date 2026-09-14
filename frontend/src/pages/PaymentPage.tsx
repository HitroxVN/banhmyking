import React, { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { orderApi } from '../api/orderApi';
import { paymentApi } from '../api/paymentApi';
import { formatCurrency } from '../utils/formatters';
import { VIETQR_CONFIG, generateVietQrUrl } from '../config/vietqr';
import type { OrderResponse, PaymentMethod } from '../types/order';
import type { PaymentResponse, ProcessPaymentRequest } from '../types/payment';

export const PaymentPage: React.FC = () => {
  const { orderCode } = useParams<{ orderCode: string }>();
  const navigate = useNavigate();
  const location = useLocation();

  // Đọc dữ liệu order truyền từ navigate state (nếu có)
  const initialOrder = (location.state as { order?: OrderResponse } | undefined)?.order;

  const [order, setOrder] = useState<OrderResponse | null>(initialOrder || null);
  const [payment, setPayment] = useState<PaymentResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(!initialOrder);
  const [error, setError] = useState<string | null>(null);

  // Form Payment Method Selection State
  const [selectedMethod, setSelectedMethod] = useState<PaymentMethod>(
    initialOrder?.paymentMethod || 'BANK_TRANSFER'
  );
  const [walletProvider, setWalletProvider] = useState<'MOMO' | 'ZALOPAY' | 'VNPAY'>('MOMO');

  // Submit Processing State
  const [isProcessing, setIsProcessing] = useState<boolean>(false);
  const [isCodConfirmed, setIsCodConfirmed] = useState<boolean>(false);
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [copiedCode, setCopiedCode] = useState<boolean>(false);
  const [copiedMemo, setCopiedMemo] = useState<boolean>(false);
  const [copiedTxn, setCopiedTxn] = useState<boolean>(false);

  // Tải chi tiết đơn hàng & trạng thái thanh toán từ backend
  useEffect(() => {
    if (!orderCode) return;

    let isMounted = true;
    const fetchOrderAndPayment = async () => {
      try {
        const orderData = await orderApi.getOrderByCode(orderCode);
        if (isMounted) {
          setOrder(orderData);
          if (orderData.paymentMethod) {
            setSelectedMethod(orderData.paymentMethod);
          }
        }

        try {
          const paymentData = await paymentApi.getPaymentByOrderCode(orderCode);
          if (isMounted) {
            setPayment(paymentData);
            if (paymentData.method) {
              setSelectedMethod(paymentData.method);
            }
          }
        } catch {
          // Bỏ qua nếu bản ghi payment đang khởi tạo
        }
      } catch (err: unknown) {
        if (isMounted) {
          interface AxiosErrorPayload {
            response?: {
              data?: {
                message?: string;
              };
            };
          }
          const axiosErr = err as AxiosErrorPayload;
          setError(
            axiosErr.response?.data?.message ||
              'Không tìm thấy thông tin đơn hàng với mã này.'
          );
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    fetchOrderAndPayment();
    return () => {
      isMounted = false;
    };
  }, [orderCode]);

  // Lắng nghe trạng thái thanh toán tự động (SePay Webhook / Techcombank)
  useEffect(() => {
    if (!orderCode) return;
    // Chỉ kích hoạt polling khi người dùng đang ở tab Chuyển khoản QR và chưa hoàn tất thanh toán
    if (selectedMethod !== 'BANK_TRANSFER') return;
    if (payment?.status === 'PAID' || order?.status === 'CONFIRMED') return;

    const intervalId = setInterval(async () => {
      try {
        const latestPayment = await paymentApi.getPaymentByOrderCode(orderCode);
        if (latestPayment && latestPayment.status === 'PAID') {
          setPayment(latestPayment);
          const latestOrder = await orderApi.getOrderByCode(orderCode);
          setOrder(latestOrder);
        }
      } catch {
        // Polling ngầm, bỏ qua lỗi mạng tạm thời
      }
    }, 2500);

    return () => clearInterval(intervalId);
  }, [orderCode, selectedMethod, payment?.status, order?.status]);

  // Xử lý gọi API Payment
  const handleProcessPayment = async () => {
    if (!orderCode || !order) return;

    setIsProcessing(true);
    setPaymentError(null);

    try {
      const payload: ProcessPaymentRequest = {
        method: selectedMethod,
        transactionRef:
          selectedMethod === 'E_WALLET'
            ? `${walletProvider}-${Date.now().toString().slice(-8)}`
            : `TXN-${Date.now().toString().slice(-8)}`,
        simulateFailure: false,
      };

      const updatedPayment = await paymentApi.processPayment(orderCode, payload);
      setPayment(updatedPayment);

      if (selectedMethod === 'COD') {
        setIsCodConfirmed(true);
      }

      // Cập nhật lại trạng thái Order
      const updatedOrder = await orderApi.getOrderByCode(orderCode);
      setOrder(updatedOrder);
    } catch (err: unknown) {
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
        'Giao dịch thanh toán không thành công. Vui lòng kiểm tra lại phương thức hoặc thử lại sau.';
      setPaymentError(message);
    } finally {
      setIsProcessing(false);
    }
  };

  const handleCopy = (text: string, type: 'code' | 'memo' | 'txn') => {
    navigator.clipboard.writeText(text);
    if (type === 'code') {
      setCopiedCode(true);
      setTimeout(() => setCopiedCode(false), 2000);
    } else if (type === 'memo') {
      setCopiedMemo(true);
      setTimeout(() => setCopiedMemo(false), 2000);
    } else {
      setCopiedTxn(true);
      setTimeout(() => setCopiedTxn(false), 2000);
    }
  };

  const isPaid = payment?.status === 'PAID' || order?.paymentStatus === 'PAID';
  const isCodOrder = selectedMethod === 'COD' || order?.paymentMethod === 'COD';
  const showReceipt = isPaid || (isCodOrder && isCodConfirmed);

  return (
    <div className="payment-page-wrapper">
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
            title="Quay lại trang chủ"
          >
            🏠 Trang chủ
          </button>
        </div>
      </header>

      <main className="payment-main-container">
        {/* Loading Spinner */}
        {isLoading && (
          <div className="cart-loading-state" style={{ padding: '4rem 1rem' }}>
            <div className="spinner" style={{ width: '45px', height: '45px', borderWidth: '3.5px' }}></div>
            <p style={{ marginTop: '1rem', color: 'var(--stone-600)', fontWeight: 600 }}>
              Đang tải dữ liệu thanh toán đơn hàng...
            </p>
          </div>
        )}

        {/* Global Error Banner */}
        {!isLoading && error && (
          <div className="empty-cart-card">
            <div className="empty-cart-illustration">⚠️</div>
            <h2 className="empty-cart-title">Không tìm thấy đơn hàng</h2>
            <p className="empty-cart-desc">{error}</p>
            <button
              type="button"
              className="btn-primary"
              style={{ width: 'auto', padding: '0.85rem 1.75rem' }}
              onClick={() => navigate('/')}
            >
              ← Quay về trang chủ
            </button>
          </div>
        )}

        {/* MAIN CONTENT */}
        {!isLoading && order && (
          <div className="payment-content-stack">
            {/* TH 1: MÀN HÌNH HÓA ĐƠN KHI ĐÃ THANH TOÁN / ĐẶT HÀNG THÀNH CÔNG (AC 4) */}
            {showReceipt ? (
              <div className="receipt-container">
                {/* Header Thành công */}
                <div className="receipt-success-header">
                  <div className="receipt-badge-icon">{isPaid ? '🎉' : '🥖'}</div>
                  <h1 className="receipt-title">{isPaid ? 'Thanh toán thành công!' : 'Đặt hàng thành công!'}</h1>
                  <p className="receipt-subtitle">
                    {isPaid
                      ? 'Giao dịch của bạn đã được xác nhận. Đơn hàng đang được Bánh Mỳ King chuyển sang bếp chuẩn bị.'
                      : 'Đơn hàng COD của bạn đã được ghi nhận. Quý khách vui lòng chuẩn bị tiền mặt khi nhận bánh mì.'}
                  </p>
                </div>

                {/* Thẻ Hóa đơn điện tử */}
                <div className="receipt-paper-card">
                  {/* Paid Stamp Con dấu */}
                  <div className={`paid-stamp ${!isPaid ? 'cod-stamp' : ''}`}>
                    <span className="paid-stamp-text">{isPaid ? 'ĐÃ THANH TOÁN' : 'ĐÃ XÁC NHẬN'}</span>
                    <span className="paid-stamp-date">
                      {isPaid
                        ? (payment?.paidAt ? new Date(payment.paidAt).toLocaleDateString('vi-VN') : 'HOÀN TẤT')
                        : 'TIỀN MẶT (COD)'}
                    </span>
                  </div>

                  {/* Brand Header */}
                  <div className="receipt-brand-row">
                    <div>
                      <div className="receipt-brand-title">🥖 BÁNH MỲ KING</div>
                      <div className="receipt-brand-desc">Hóa đơn giá trị gia tăng điện tử</div>
                    </div>
                    <div className="receipt-order-id-box">
                      <div className="receipt-id-label">MÃ ĐƠN HÀNG</div>
                      <div className="receipt-id-code">{order.orderCode}</div>
                    </div>
                  </div>

                  <div className="receipt-divider"></div>

                  {/* Thông tin giao dịch & Người nhận */}
                  <div className="receipt-meta-grid">
                    <div>
                      <span className="receipt-meta-label">Mã giao dịch:</span>
                      <div className="meta-txn-row">
                        <strong className="receipt-meta-value">{payment?.gatewayTxnId || 'TXN-CONFIRMED'}</strong>
                        <button
                          type="button"
                          className="btn-copy-small"
                          onClick={() => handleCopy(payment?.gatewayTxnId || order.orderCode, 'txn')}
                        >
                          {copiedTxn ? '✓' : 'Copy'}
                        </button>
                      </div>
                    </div>

                    <div>
                      <span className="receipt-meta-label">Thời gian:</span>
                      <strong className="receipt-meta-value">
                        {payment?.paidAt
                          ? new Date(payment.paidAt).toLocaleString('vi-VN')
                          : new Date().toLocaleString('vi-VN')}
                      </strong>
                    </div>

                    <div>
                      <span className="receipt-meta-label">Phương thức:</span>
                      <strong className="receipt-meta-value">
                        {payment?.method === 'BANK_TRANSFER'
                          ? 'Chuyển khoản VietQR'
                          : payment?.method === 'E_WALLET'
                          ? 'Ví điện tử / Thẻ quốc tế'
                          : 'Tiền mặt khi nhận hàng (COD)'}
                      </strong>
                    </div>

                    <div>
                      <span className="receipt-meta-label">Trạng thái đơn:</span>
                      <span className="status-tag status-confirmed">
                        ● {order.status === 'CONFIRMED' ? 'Đã xác nhận & Đang làm bánh' : order.status}
                      </span>
                    </div>

                    <div style={{ gridColumn: 'span 2' }}>
                      <span className="receipt-meta-label">Người nhận & Địa chỉ:</span>
                      <div className="receipt-meta-value">
                        <strong>{order.receiverName}</strong> ({order.receiverPhone}) - {order.shippingAddress}
                      </div>
                    </div>
                  </div>

                  <div className="receipt-divider"></div>

                  {/* Bảng chi tiết món ăn */}
                  <div className="receipt-items-table">
                    <div className="receipt-table-header">
                      <span>Món ăn / Tùy chọn</span>
                      <span style={{ textAlign: 'center' }}>SL</span>
                      <span style={{ textAlign: 'right' }}>Thành tiền</span>
                    </div>

                    {order.items?.map((item) => (
                      <div key={item.id} className="receipt-table-row">
                        <div>
                          <div className="receipt-item-name">{item.productName}</div>
                          {item.options && item.options.length > 0 && (
                            <div className="receipt-item-options">
                              + {item.options.map((o) => `${o.optionName} (${formatCurrency(o.optionPrice)})`).join(', ')}
                            </div>
                          )}
                        </div>
                        <div style={{ textAlign: 'center', fontWeight: 600 }}>x{item.quantity}</div>
                        <div style={{ textAlign: 'right', fontWeight: 700 }}>
                          {formatCurrency(item.lineTotal)}
                        </div>
                      </div>
                    ))}
                  </div>

                  <div className="receipt-divider"></div>

                  {/* Tổng tiền thanh toán */}
                  <div className="receipt-totals-box">
                    <div className="receipt-total-row">
                      <span>Tạm tính các món:</span>
                      <span>{formatCurrency(order.subtotal)}</span>
                    </div>
                    <div className="receipt-total-row">
                      <span>Phí vận chuyển:</span>
                      <span>
                        {order.shippingFee && order.shippingFee > 0
                          ? formatCurrency(order.shippingFee)
                          : 'Miễn phí'}
                      </span>
                    </div>
                    {order.discountAmount && order.discountAmount > 0 ? (
                      <div className="receipt-total-row" style={{ color: '#dc2626' }}>
                        <span>Giảm giá voucher:</span>
                        <span>-{formatCurrency(order.discountAmount)}</span>
                      </div>
                    ) : null}

                    <div className="receipt-grand-row">
                      <span>TỔNG TIỀN ĐÃ THANH TOÁN:</span>
                      <span className="receipt-grand-val">{formatCurrency(order.total)}</span>
                    </div>
                  </div>

                  <div className="receipt-footer-note">
                    Cảm ơn bạn đã tin dùng bánh mì giòn nóng của Bánh Mỳ King! Chúc bạn ngon miệng.
                  </div>
                </div>

                {/* Nút hành động quay về trang chủ hoặc xem đơn hàng */}
                <div className="receipt-actions-row">
                  <button
                    type="button"
                    className="btn-primary"
                    onClick={() => navigate('/')}
                    style={{ width: 'auto', padding: '0.85rem 2rem' }}
                  >
                    🏠 Quay về trang chủ
                  </button>
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={() => navigate('/cart')}
                    style={{ width: 'auto', padding: '0.85rem 1.75rem' }}
                  >
                    🛒 Xem lại giỏ hàng
                  </button>
                </div>
              </div>
            ) : (
              /* TH 2: GIAO DIỆN CHỌN PHƯƠNG THỨC & THỰC HIỆN THANH TOÁN (AC 1, 2, 3) */
              <div className="payment-checkout-flow">
                {/* Banner Tóm tắt mã đơn & Tổng tiền cần thanh toán */}
                <div className="payment-order-summary-header">
                  <div className="payment-header-left">
                    <span className="payment-tag">💳 Bước thanh toán</span>
                    <h1 className="payment-page-heading">Thanh toán đơn hàng</h1>
                    <div className="payment-order-code-box">
                      <span>Mã đơn:</span>
                      <strong className="order-code-text">{order.orderCode}</strong>
                      <button
                        type="button"
                        className="btn-copy-small"
                        onClick={() => handleCopy(order.orderCode, 'code')}
                      >
                        {copiedCode ? '✓' : 'Copy'}
                      </button>
                    </div>
                  </div>

                  <div className="payment-header-right">
                    <span className="payment-amount-label">Số tiền cần thanh toán:</span>
                    <span className="payment-amount-number" id="payment-amount-number">
                      {formatCurrency(order.total)}
                    </span>
                  </div>
                </div>

                {/* Banner Lỗi giao dịch trực quan (AC 3) */}
                {paymentError && (
                  <div className="alert-banner alert-error" id="payment-error-banner" style={{ margin: '1.5rem 0' }}>
                    <div style={{ fontSize: '1.5rem' }}>❌</div>
                    <div>
                      <strong style={{ display: 'block', fontSize: '1rem', marginBottom: '0.2rem' }}>
                        Giao dịch thanh toán bị từ chối
                      </strong>
                      <span>{paymentError}</span>
                      <p style={{ fontSize: '0.85rem', marginTop: '0.35rem', color: '#7f1d1d' }}>
                        💡 Vui lòng kiểm tra lại số dư thẻ/tài khoản hoặc chọn phương thức thanh toán khác bên dưới.
                      </p>
                    </div>
                  </div>
                )}

                {/* Layout Chọn phương thức & Review */}
                <div className="payment-layout-grid" style={{ marginTop: '1.5rem' }}>
                  {/* Cột Trái: Chọn phương thức thanh toán khả dụng */}
                  <div className="payment-guide-column">
                    <div className="payment-method-box">
                      <h2 className="section-card-title" style={{ marginBottom: '1.25rem' }}>
                        Chọn phương thức thanh toán khả dụng:
                      </h2>

                      <div className="payment-options-stack">
                        {/* 1. VietQR Bank Transfer */}
                        <label
                          className={`payment-option-card ${selectedMethod === 'BANK_TRANSFER' ? 'active' : ''}`}
                          onClick={() => setSelectedMethod('BANK_TRANSFER')}
                        >
                          <input
                            type="radio"
                            name="payMethod"
                            value="BANK_TRANSFER"
                            checked={selectedMethod === 'BANK_TRANSFER'}
                            onChange={() => setSelectedMethod('BANK_TRANSFER')}
                            style={{ display: 'none' }}
                          />
                          <div className="payment-option-radio">
                            <span className="radio-dot">{selectedMethod === 'BANK_TRANSFER' ? '●' : '○'}</span>
                          </div>
                          <div className="payment-option-content">
                            <div className="payment-option-title-row">
                              <span className="payment-icon">📲</span>
                              <span className="payment-option-name">Chuyển khoản Ngân hàng (VietQR 24/7)</span>
                              <span className="payment-badge-fast">Khuyên dùng</span>
                            </div>
                            <p className="payment-option-desc">
                              Quét mã VietQR bằng ứng dụng mọi ngân hàng. Hệ thống tự động xác nhận sau khi chuyển.
                            </p>
                          </div>
                        </label>

                        {/* 2. E-Wallet */}
                        <label
                          className={`payment-option-card ${selectedMethod === 'E_WALLET' ? 'active' : ''}`}
                          onClick={() => setSelectedMethod('E_WALLET')}
                        >
                          <input
                            type="radio"
                            name="payMethod"
                            value="E_WALLET"
                            checked={selectedMethod === 'E_WALLET'}
                            onChange={() => setSelectedMethod('E_WALLET')}
                            style={{ display: 'none' }}
                          />
                          <div className="payment-option-radio">
                            <span className="radio-dot">{selectedMethod === 'E_WALLET' ? '●' : '○'}</span>
                          </div>
                          <div className="payment-option-content">
                            <div className="payment-option-title-row">
                              <span className="payment-icon">💳</span>
                              <span className="payment-option-name">Ví điện tử / Thẻ quốc tế</span>
                              <span className="payment-badge-popular">MoMo / ZaloPay / Visa</span>
                            </div>
                            <p className="payment-option-desc">
                              Thanh toán tức thì qua cổng ví điện tử MoMo, ZaloPay, VNPay hoặc thẻ Visa/Mastercard.
                            </p>
                          </div>
                        </label>

                        {/* 3. COD */}
                        <label
                          className={`payment-option-card ${selectedMethod === 'COD' ? 'active' : ''}`}
                          onClick={() => setSelectedMethod('COD')}
                        >
                          <input
                            type="radio"
                            name="payMethod"
                            value="COD"
                            checked={selectedMethod === 'COD'}
                            onChange={() => setSelectedMethod('COD')}
                            style={{ display: 'none' }}
                          />
                          <div className="payment-option-radio">
                            <span className="radio-dot">{selectedMethod === 'COD' ? '●' : '○'}</span>
                          </div>
                          <div className="payment-option-content">
                            <div className="payment-option-title-row">
                              <span className="payment-icon">💵</span>
                              <span className="payment-option-name">Tiền mặt khi nhận hàng (COD)</span>
                            </div>
                            <p className="payment-option-desc">
                              Thanh toán bằng tiền mặt trực tiếp cho shipper khi nhận bánh mì.
                            </p>
                          </div>
                        </label>
                      </div>

                      {/* Chi tiết cho phương thức được chọn */}
                      {selectedMethod === 'BANK_TRANSFER' && (
                        <div className="vietqr-container" style={{ marginTop: '1.5rem' }}>
                          <div className="vietqr-image-wrapper">
                            <img
                              src={generateVietQrUrl(order.total, order.orderCode)}
                              alt="VietQR"
                              className="vietqr-image"
                            />
                          </div>

                          <div className="vietqr-instructions">
                            <div className="bank-info-item">
                              <span className="bank-info-label">Ngân hàng:</span>
                              <span className="bank-info-val highlight">{VIETQR_CONFIG.bankId.toUpperCase()}</span>
                            </div>
                            <div className="bank-info-item">
                              <span className="bank-info-label">Số tài khoản:</span>
                              <span className="bank-info-val highlight">{VIETQR_CONFIG.accountNo}</span>
                            </div>
                            <div className="bank-info-item">
                              <span className="bank-info-label">Chủ TK:</span>
                              <span className="bank-info-val">{VIETQR_CONFIG.accountName}</span>
                            </div>
                            <div className="bank-info-item">
                              <span className="bank-info-label">Nội dung CK:</span>
                              <div className="memo-copy-row">
                                <span className="bank-info-val memo-badge">{order.orderCode}</span>
                                <button
                                  type="button"
                                  className="btn-copy-small"
                                  onClick={() => handleCopy(order.orderCode, 'memo')}
                                >
                                  {copiedMemo ? '✓' : 'Copy'}
                                </button>
                              </div>
                            </div>
                          </div>

                          {/* Khối trạng thái lắng nghe giao dịch SePay */}
                          <div className="sepay-live-status-box">
                            <div className="sepay-live-pulse-container">
                              <span className="sepay-live-pulse-ping"></span>
                              <span className="sepay-live-pulse-dot"></span>
                            </div>
                            <div className="sepay-live-content">
                              <div className="sepay-live-header">
                                <span className="sepay-live-title">Tự động xác nhận giao dịch Techcombank</span>
                                <span className="sepay-live-tag">LIVE</span>
                              </div>
                              <p className="sepay-live-desc">
                                Hệ thống đang tự động lắng nghe biến động số dư. Khi chuyển khoản thành công, màn hình sẽ <strong>tự động chuyển sang ĐÃ THANH TOÁN</strong> mà không cần tải lại trang.
                              </p>
                            </div>
                          </div>
                        </div>
                      )}

                      {selectedMethod === 'E_WALLET' && (
                        <div className="wallet-selector-box" style={{ marginTop: '1.5rem' }}>
                          <div className="wallet-label">Chọn cổng ví điện tử liên kết:</div>
                          <div className="wallet-providers-grid">
                            <button
                              type="button"
                              className={`wallet-btn ${walletProvider === 'MOMO' ? 'active' : ''}`}
                              onClick={() => setWalletProvider('MOMO')}
                            >
                              🌸 MoMo
                            </button>
                            <button
                              type="button"
                              className={`wallet-btn ${walletProvider === 'ZALOPAY' ? 'active' : ''}`}
                              onClick={() => setWalletProvider('ZALOPAY')}
                            >
                              💚 ZaloPay
                            </button>
                            <button
                              type="button"
                              className={`wallet-btn ${walletProvider === 'VNPAY' ? 'active' : ''}`}
                              onClick={() => setWalletProvider('VNPAY')}
                            >
                              🏦 VNPAY / Thẻ
                            </button>
                          </div>
                        </div>
                      )}

                      {/* Nút hành động thanh toán */}
                      <button
                        id="btn-process-payment"
                        type="button"
                        className="btn-submit-order"
                        onClick={handleProcessPayment}
                        disabled={isProcessing}
                        style={{ marginTop: '1.5rem' }}
                      >
                        {isProcessing ? (
                          <div className="submit-spinner-box">
                            <div className="spinner" style={{ width: '22px', height: '22px', borderWidth: '2.5px' }}></div>
                            <span>Đang kiểm tra trạng thái thanh toán...</span>
                          </div>
                        ) : (
                          <span>
                            {selectedMethod === 'COD'
                              ? ` Xác nhận đặt hàng (COD: ${formatCurrency(order.total)})`
                              : selectedMethod === 'BANK_TRANSFER'
                              ? `⚡ Tôi đã chuyển khoản xong (Kiểm tra ngay)`
                              : `🚀 Xác nhận & Thanh toán ngay (${formatCurrency(order.total)})`}
                          </span>
                        )}
                      </button>
                    </div>
                  </div>

                  {/* Cột Phải: Tóm tắt đơn hàng & Địa chỉ */}
                  <div className="order-breakdown-column">
                    <div className="order-summary-card">
                      <h3 className="summary-card-title">Tóm tắt đơn hàng</h3>

                      <div className="checkout-mini-items-list">
                        {order.items?.map((item) => (
                          <div key={item.id} className="checkout-mini-item">
                            <div className="checkout-mini-item-info">
                              <div className="checkout-mini-item-title">
                                <span className="mini-qty">{item.quantity}x</span>
                                <span className="mini-name">{item.productName}</span>
                              </div>
                              {item.options && item.options.length > 0 && (
                                <div className="checkout-mini-toppings">
                                  + {item.options.map((o) => `${o.optionName} (${formatCurrency(o.optionPrice)})`).join(', ')}
                                </div>
                              )}
                            </div>
                            <div className="checkout-mini-item-price">{formatCurrency(item.lineTotal)}</div>
                          </div>
                        ))}
                      </div>

                      <div className="summary-divider"></div>

                      <div className="summary-calculation-rows">
                        <div className="summary-row">
                          <span className="summary-label">Tạm tính:</span>
                          <span className="summary-value">{formatCurrency(order.subtotal)}</span>
                        </div>
                        <div className="summary-row">
                          <span className="summary-label">Phí vận chuyển:</span>
                          <span className="summary-value">
                            {order.shippingFee && order.shippingFee > 0 ? (
                              formatCurrency(order.shippingFee)
                            ) : (
                              <span style={{ color: '#059669', fontWeight: 600 }}>Miễn phí</span>
                            )}
                          </span>
                        </div>
                        {order.discountAmount && order.discountAmount > 0 ? (
                          <div className="summary-row" style={{ color: '#dc2626' }}>
                            <span className="summary-label">Khuyến mãi:</span>
                            <span className="summary-value">-{formatCurrency(order.discountAmount)}</span>
                          </div>
                        ) : null}
                      </div>

                      <div className="summary-divider"></div>

                      <div className="summary-grand-total-row">
                        <div>
                          <div className="grand-total-label">Tổng thanh toán</div>
                          <div className="grand-total-subtext">Đã bao gồm VAT</div>
                        </div>
                        <div className="grand-total-price">{formatCurrency(order.total)}</div>
                      </div>

                      <div className="shipping-summary-box" style={{ marginTop: '1.25rem', paddingTop: '1rem', borderTop: '1px solid var(--stone-200)' }}>
                        <div style={{ fontSize: '0.82rem', color: 'var(--stone-500)', marginBottom: '0.2rem' }}>Giao tới:</div>
                        <div style={{ fontSize: '0.92rem', fontWeight: 700, color: 'var(--stone-800)' }}>
                          {order.receiverName} - {order.receiverPhone}
                        </div>
                        <div style={{ fontSize: '0.85rem', color: 'var(--stone-600)', marginTop: '0.2rem' }}>
                          {order.shippingAddress}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
};
