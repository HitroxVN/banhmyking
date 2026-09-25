import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Banknote, MapPin, QrCode, Receipt, Sandwich, ShieldCheck, ShoppingBag, Ticket, Timer, Truck } from 'lucide-react';
import { useCart } from '../context/useCart';
import { useAuth } from '../context/useAuth';
import { addressApi } from '../api/addressApi';
import { orderApi } from '../api/orderApi';
import { promotionApi } from '../api/promotionApi';
import { deliveryApi } from '../api/deliveryApi';
import { Badge, Button, EmptyState, Input, Spinner, Textarea, useToast } from '../components/ui';
import { formatCurrency } from '../utils/formatters';
import type { AddressResponse } from '../types/address';
import type { CreateOrderRequest, PaymentMethod } from '../types/order';
import type { PromotionResponse } from '../types/promotion';
import type { DeliveryFeeResult } from '../types/delivery';
import '../styles/components/order.css';
import '../styles/components/checkout.css';

interface FormErrors {
  receiverName?: string;
  receiverPhone?: string;
  shippingAddress?: string;
}

const validateField = (name: keyof FormErrors, value: string): string | undefined => {
  const trimmed = value.trim();
  if (name === 'receiverName') {
    if (!trimmed) return 'Vui lòng nhập họ và tên người nhận';
    if (trimmed.length < 2) return 'Họ và tên người nhận tối thiểu 2 ký tự';
  }
  if (name === 'receiverPhone') {
    if (!trimmed) return 'Vui lòng nhập số điện thoại nhận hàng';
    if (!/^(0[35789])[0-9]{8}$/.test(trimmed)) {
      return 'Số điện thoại không hợp lệ (10 chữ số, bắt đầu bằng 03, 05, 07, 08, 09)';
    }
  }
  if (name === 'shippingAddress') {
    if (!trimmed) return 'Vui lòng nhập địa chỉ nhận hàng chi tiết';
    if (trimmed.length < 5) return 'Địa chỉ nhận hàng quá ngắn (tối thiểu 5 ký tự)';
  }
  return undefined;
};

export const CheckoutPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { cart, isLoading: isCartLoading, refreshCart, subtotal, totalQuantity } = useCart();
  const toast = useToast();

  const [savedAddresses, setSavedAddresses] = useState<AddressResponse[]>([]);
  const [isLoadingAddresses, setIsLoadingAddresses] = useState(true);
  const [addressMode, setAddressMode] = useState<'saved' | 'new'>('new');
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);

  const [receiverName, setReceiverName] = useState('');
  const [receiverPhone, setReceiverPhone] = useState('');
  const [shippingAddress, setShippingAddress] = useState('');
  const [note, setNote] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('COD');

  const [errors, setErrors] = useState<FormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);

  const [fee, setFee] = useState<DeliveryFeeResult | null>(null);
  const [isLoadingFee, setIsLoadingFee] = useState(false);

  const [promoInput, setPromoInput] = useState('');
  const [appliedPromotion, setAppliedPromotion] = useState<PromotionResponse | null>(null);
  const [isApplyingPromo, setIsApplyingPromo] = useState(false);

  // Sổ địa chỉ: ưu tiên địa chỉ mặc định, chưa có thì điền sẵn thông tin tài khoản
  useEffect(() => {
    let alive = true;

    addressApi
      .getAddresses()
      .then((addresses) => {
        if (!alive) return;
        setSavedAddresses(addresses);
        if (addresses.length === 0) return;

        const preferred = addresses.find((item) => item.defaultAddress) ?? addresses[0];
        setAddressMode('saved');
        setSelectedAddressId(preferred.id);
        setReceiverName(preferred.receiverName);
        setReceiverPhone(preferred.receiverPhone);
        setShippingAddress(preferred.fullAddress);
      })
      .catch(() => {
        if (!alive) return;
        setAddressMode('new');
        setReceiverName(user?.fullName ?? '');
        setReceiverPhone(user?.phone ?? '');
      })
      .finally(() => {
        if (alive) setIsLoadingAddresses(false);
      });

    return () => {
      alive = false;
    };
  }, [user]);

  // Phí ship xem trước — gọi cùng tham số mà POST /orders sẽ dùng (không gửi distanceKm)
  useEffect(() => {
    const address = shippingAddress.trim();
    if (subtotal <= 0 || address.length < 5) {
      setFee(null);
      return;
    }

    let cancelled = false;
    setIsLoadingFee(true);

    const timer = window.setTimeout(() => {
      deliveryApi
        .getFee({ subtotal, shippingAddress: address })
        .then((result) => {
          if (!cancelled) setFee(result);
        })
        .catch(() => {
          if (!cancelled) setFee(null);
        })
        .finally(() => {
          if (!cancelled) setIsLoadingFee(false);
        });
    }, 400);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [shippingAddress, subtotal]);

  const shippingFee = fee?.shippingFee ?? 0;
  // Số tiền giảm do backend tính (cùng công thức với lúc tạo đơn) — FE không tự tính lại
  const discount = appliedPromotion?.discountApplied ?? 0;
  const total = Math.max(0, subtotal + shippingFee - discount);
  const items = cart?.items ?? [];
  const isEmpty = !isCartLoading && items.length === 0;

  const selectAddress = (address: AddressResponse) => {
    setSelectedAddressId(address.id);
    setReceiverName(address.receiverName);
    setReceiverPhone(address.receiverPhone);
    setShippingAddress(address.fullAddress);
    setErrors({});
  };

  const switchToNewAddress = () => {
    setAddressMode('new');
    setSelectedAddressId(null);
    setReceiverName(user?.fullName ?? '');
    setReceiverPhone(user?.phone ?? '');
    setShippingAddress('');
    setErrors({});
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setApiError(null);

    const nextErrors: FormErrors = {
      receiverName: validateField('receiverName', receiverName),
      receiverPhone: validateField('receiverPhone', receiverPhone),
      shippingAddress: validateField('shippingAddress', shippingAddress),
    };
    setErrors(nextErrors);
    if (Object.values(nextErrors).some(Boolean)) return;

    if (isEmpty) {
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
        note: note.trim() || undefined,
        promotionCode: appliedPromotion?.code,
      };

      const created = await orderApi.createOrder(payload);
      await refreshCart();
      navigate(`/payment/${created.orderCode}`, { state: { order: created }, replace: true });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Đặt hàng thất bại. Vui lòng thử lại.';
      setApiError(message);
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  // Áp mã: `orderAmount` = subtotal (trước phí ship) để khớp validateForOrder của backend
  const applyPromo = async () => {
    const code = promoInput.trim();
    if (!code || isApplyingPromo) return;

    setIsApplyingPromo(true);
    try {
      const promotion = await promotionApi.validate({
        code,
        orderAmount: subtotal,
        shippingFee: fee?.shippingFee,
        userId: user?.id,
      });
      setAppliedPromotion(promotion);
      setPromoInput(promotion.code);
      toast.success(`Đã áp dụng mã ${promotion.code}`);
    } catch (err) {
      setAppliedPromotion(null);
      // Backend đã trả message tiếng Việt (hết hạn / chưa đủ đơn tối thiểu / hết lượt)
      toast.error(err instanceof Error ? err.message : 'Mã giảm giá không dùng được');
    } finally {
      setIsApplyingPromo(false);
    }
  };

  const clearPromo = () => {
    setAppliedPromotion(null);
    setPromoInput('');
  };

  if (isCartLoading) {
    return (
      <div className="page-state">
        <Spinner size={30} />
      </div>
    );
  }

  if (isEmpty) {
    return (
      <>
        <div className="page-bar">
          <h1 className="page-bar__title">Thanh toán</h1>
        </div>
        <EmptyState
          icon={<ShoppingBag size={30} />}
          title="Chưa có món nào để thanh toán"
          description="Giỏ hàng của bạn đang trống. Chọn món trong thực đơn trước nhé."
          action={<Button onClick={() => navigate('/')}>Xem thực đơn</Button>}
        />
      </>
    );
  }

  return (
    <>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/">Thực đơn</Link> / <Link to="/cart">Giỏ hàng</Link> / Thanh toán
          </p>
          <h1 className="page-bar__title">Thanh toán</h1>
        </div>
      </div>

      {apiError && (
        <div className="alert-banner alert-error page-alert">
          <div>{apiError}</div>
        </div>
      )}

      <form className="order-grid" onSubmit={handleSubmit} noValidate>
        <div>
          <section className="card">
            <div className="card__head">
              <h2 className="card__title">
                <MapPin size={19} />
                Thông tin nhận hàng
              </h2>
            </div>
            <div className="card__body">
              {isLoadingAddresses ? (
                <Spinner size={22} />
              ) : (
                <>
                  {savedAddresses.length > 0 && (
                    <div className="ck__mode">
                      <button
                        type="button"
                        className={`ck__mode-btn${addressMode === 'saved' ? ' ck__mode-btn--on' : ''}`}
                        onClick={() => {
                          setAddressMode('saved');
                          const preferred = savedAddresses.find((a) => a.id === selectedAddressId) ?? savedAddresses[0];
                          selectAddress(preferred);
                        }}
                      >
                        Địa chỉ đã lưu
                      </button>
                      <button
                        type="button"
                        className={`ck__mode-btn${addressMode === 'new' ? ' ck__mode-btn--on' : ''}`}
                        onClick={switchToNewAddress}
                      >
                        Nhập địa chỉ mới
                      </button>
                    </div>
                  )}

                  {addressMode === 'saved' ? (
                    <div className="ck__addr-list">
                      {savedAddresses.map((address) => (
                        <label
                          key={address.id}
                          className={`ck__addr${selectedAddressId === address.id ? ' ck__addr--on' : ''}`}
                        >
                          <input
                            className="ck__addr-radio"
                            type="radio"
                            name="saved-address"
                            checked={selectedAddressId === address.id}
                            onChange={() => selectAddress(address)}
                          />
                          <span className="ck__addr-main">
                            <span className="ck__addr-name">
                              {address.receiverName}
                              <span className="ck__addr-phone">{address.receiverPhone}</span>
                              {address.defaultAddress && <Badge tone="info">Mặc định</Badge>}
                            </span>
                            <span className="ck__addr-text">{address.fullAddress}</span>
                          </span>
                        </label>
                      ))}
                    </div>
                  ) : (
                    <div className="ck__row2">
                      <Input
                        label="Họ và tên người nhận"
                        required
                        value={receiverName}
                        onChange={(event) => setReceiverName(event.target.value)}
                        onBlur={() => setErrors((prev) => ({ ...prev, receiverName: validateField('receiverName', receiverName) }))}
                        error={errors.receiverName}
                        placeholder="Nguyễn Văn A"
                      />
                      <Input
                        label="Số điện thoại"
                        required
                        inputMode="tel"
                        value={receiverPhone}
                        onChange={(event) => setReceiverPhone(event.target.value)}
                        onBlur={() => setErrors((prev) => ({ ...prev, receiverPhone: validateField('receiverPhone', receiverPhone) }))}
                        error={errors.receiverPhone}
                        placeholder="0901234567"
                      />
                    </div>
                  )}

                  {addressMode === 'new' && (
                    <Input
                      label="Địa chỉ nhận hàng"
                      required
                      value={shippingAddress}
                      onChange={(event) => setShippingAddress(event.target.value)}
                      onBlur={() => setErrors((prev) => ({ ...prev, shippingAddress: validateField('shippingAddress', shippingAddress) }))}
                      error={errors.shippingAddress}
                      hint="Số nhà, đường, phường/quận, thành phố — giúp tính phí giao chính xác."
                      placeholder="123 Lê Lợi, Quận 1, TP.HCM"
                    />
                  )}

                  <div className="ck__block">
                    <Textarea
                      label="Ghi chú cho quán"
                      value={note}
                      maxLength={300}
                      onChange={(event) => setNote(event.target.value)}
                      placeholder="Ví dụ: không hành, cắt đôi, gọi trước khi giao..."
                    />
                  </div>
                </>
              )}
            </div>
          </section>

          <section className="card">
            <div className="card__head">
              <h2 className="card__title">
                <Receipt size={19} />
                Phương thức thanh toán
              </h2>
            </div>
            <div className="card__body">
              <div className="ck__pays">
                <label className={`ck__pay${paymentMethod === 'COD' ? ' ck__pay--on' : ''}`}>
                  <input
                    type="radio"
                    name="payment-method"
                    checked={paymentMethod === 'COD'}
                    onChange={() => setPaymentMethod('COD')}
                  />
                  <span className="ck__pay-icon">
                    <Banknote size={18} />
                  </span>
                  <span className="ck__pay-text">
                    <span className="ck__pay-title">Tiền mặt khi nhận hàng</span>
                    <span className="ck__pay-desc">Thanh toán cho tài xế sau khi nhận bánh</span>
                  </span>
                </label>

                <label className={`ck__pay${paymentMethod === 'BANK_TRANSFER' ? ' ck__pay--on' : ''}`}>
                  <input
                    type="radio"
                    name="payment-method"
                    checked={paymentMethod === 'BANK_TRANSFER'}
                    onChange={() => setPaymentMethod('BANK_TRANSFER')}
                  />
                  <span className="ck__pay-icon">
                    <QrCode size={18} />
                  </span>
                  <span className="ck__pay-text">
                    <span className="ck__pay-title">Chuyển khoản VietQR</span>
                    <span className="ck__pay-desc">Quét mã QR, đơn được xác nhận tự động</span>
                  </span>
                </label>
              </div>
            </div>
          </section>
        </div>

        <aside className="card">
          <div className="card__head">
            <h2 className="card__title">
              <Receipt size={19} />
              Đơn hàng của bạn
            </h2>
            <span className="menu__section-sub">{totalQuantity} món</span>
          </div>
          <div className="card__body">
            <div className="ck__items">
              {items.map((item) => (
                <div className="ck__item" key={item.id}>
                  <span className="ck__item-media">
                    {item.productImageUrl ? (
                      <img className="ck__item-img" src={item.productImageUrl} alt={item.productName} />
                    ) : (
                      <span className="pcard__placeholder" aria-hidden="true">
                        <Sandwich size={18} />
                      </span>
                    )}
                  </span>
                  <div>
                    <p className="ck__item-name">{item.productName}</p>
                    <p className="ck__item-meta">
                      {item.quantity} × {formatCurrency(item.unitPrice)}
                    </p>
                  </div>
                  <span className="ck__item-price">{formatCurrency(item.subtotal)}</span>
                </div>
              ))}
            </div>

            <div className="summary__divider" />

            <div className="summary__row">
              <span>Tạm tính</span>
              <span>{formatCurrency(subtotal)}</span>
            </div>
            <div className={`summary__row${fee?.freeship ? ' summary__row--free' : ''}`}>
              <span>
                <Truck size={14} /> Phí giao hàng
              </span>
              <span>
                {isLoadingFee ? 'Đang tính…' : fee?.freeship ? 'Miễn phí' : formatCurrency(shippingFee)}
              </span>
            </div>
            {fee?.description && <p className="summary__row summary__row--note">{fee.description}</p>}
            {discount > 0 && (
              <div className="summary__row summary__row--free">
                <span>Giảm giá ({appliedPromotion?.code})</span>
                <span>-{formatCurrency(discount)}</span>
              </div>
            )}

            <div className="summary__divider" />

            <div className="summary__total">
              <span className="summary__total-label">Tổng thanh toán</span>
              <span className="summary__total-price">{formatCurrency(total)}</span>
            </div>

            <div className="ck__block">
              <div className="ck__promo">
                <Input
                  label="Mã giảm giá"
                  icon={<Ticket size={16} />}
                  placeholder="VD: BANHMYKING10"
                  value={promoInput}
                  disabled={appliedPromotion !== null}
                  onChange={(event) => setPromoInput(event.target.value)}
                  // Ô này nằm trong <form onSubmit> — không chặn thì Enter sẽ gửi luôn đơn hàng
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      event.preventDefault();
                      void applyPromo();
                    }
                  }}
                />
                {appliedPromotion ? (
                  <Button variant="secondary" onClick={clearPromo}>
                    Bỏ mã
                  </Button>
                ) : (
                  <Button
                    variant="secondary"
                    loading={isApplyingPromo}
                    disabled={!promoInput.trim()}
                    onClick={() => void applyPromo()}
                  >
                    Áp dụng
                  </Button>
                )}
              </div>
            </div>
          </div>
          <div className="card__foot">
            <Button type="submit" block size="lg" loading={isSubmitting}>
              {`Xác nhận đặt hàng — ${formatCurrency(total)}`}
            </Button>

            <div className="ck__trust">
              <span className="ck__trust-item">
                <ShieldCheck size={14} /> Bánh nướng theo đơn
              </span>
              <span className="ck__trust-item">
                <Timer size={14} /> Giao trong 30 phút
              </span>
              <span className="ck__trust-item">
                <Truck size={14} /> Miễn phí từ {formatCurrency(fee?.freeshipThreshold ?? 200000)}
              </span>
            </div>
          </div>
        </aside>
      </form>
    </>
  );
};
