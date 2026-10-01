import { useEffect, useRef, useState } from 'react';
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
import { describePromotionValue } from '../utils/promotion';
import type { AddressResponse } from '../types/address';
import type { CreateOrderRequest, PaymentMethod } from '../types/order';
import type { PromotionResponse, PublicPromotionResponse } from '../types/promotion';
import type { DeliveryQuote, StoreQuoteOption } from '../types/store';
import { AddressLocationFields } from '../components/address/AddressLocationFields';
import { EMPTY_LOCATION, type AddressLocation } from '../components/address/addressLocation';
import '../styles/components/order.css';
import '../styles/components/checkout.css';
import '../styles/components/stores.css';

interface FormErrors {
  receiverName?: string;
  receiverPhone?: string;
  shippingAddress?: string;
}

/** Địa chỉ trong sổ → dạng dùng cho form/phí ship (địa chỉ cũ chưa ghim thì toạ độ null) */
const toLocation = (address: AddressResponse): AddressLocation => ({
  street: address.street ?? '',
  ward: address.ward ?? '',
  province: address.province ?? '',
  fullAddress: address.fullAddress,
  latitude: address.latitude ?? null,
  longitude: address.longitude ?? null,
});

const SAVED_UNPINNED_MESSAGE =
  'Địa chỉ này chưa được ghim trên bản đồ — vui lòng cập nhật địa chỉ trong Hồ sơ trước khi đặt hàng.';

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
  /** Địa chỉ giao đang chọn (từ sổ địa chỉ hoặc nhập mới) — toạ độ dùng để server tính phí ship */
  const [location, setLocation] = useState<AddressLocation>(EMPTY_LOCATION);
  const shippingAddress = location.fullAddress;
  const [note, setNote] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('COD');

  const [errors, setErrors] = useState<FormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);
  /** Đã thử đặt hàng với địa chỉ mới mà chưa ghim vị trí — mất đi ngay khi khách ghim */
  const [pinAttempted, setPinAttempted] = useState(false);
  const hasPin = location.latitude != null && location.longitude != null;
  const pinMissing = pinAttempted && !hasPin;
  /** Giữ nguyên khoá giữa các lần bấm lại cùng một lượt đặt để backend không tạo đơn trùng. */
  const idempotencyKeyRef = useRef<string>('');

  const [quote, setQuote] = useState<DeliveryQuote | null>(null);
  const [isLoadingFee, setIsLoadingFee] = useState(false);
  /** Lỗi tính phí (vd ngoài bán kính giao) — hiện cho khách thay vì nuốt im lặng */
  const [feeError, setFeeError] = useState<string | null>(null);
  /** Cơ sở khách chọn; null = theo đề xuất của server */
  const [chosenStoreId, setChosenStoreId] = useState<number | null>(null);
  const [showStores, setShowStores] = useState(false);

  const [promoInput, setPromoInput] = useState('');
  const [appliedPromotion, setAppliedPromotion] = useState<PromotionResponse | null>(null);
  const [isApplyingPromo, setIsApplyingPromo] = useState(false);
  const [availablePromotions, setAvailablePromotions] = useState<PublicPromotionResponse[]>([]);

  // Danh sách mã chỉ là GỢI Ý: lỗi mạng thì bỏ qua im lặng, khách vẫn gõ tay được.
  // Không để nó chặn hay làm hỏng luồng đặt hàng.
  useEffect(() => {
    let alive = true;

    promotionApi
      .getPublicPromotions()
      .then((promotions) => {
        if (alive) setAvailablePromotions(promotions);
      })
      .catch(() => {
        if (alive) setAvailablePromotions([]);
      });

    return () => {
      alive = false;
    };
  }, []);

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
        setLocation(toLocation(preferred));
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

  // Phí ship xem trước — cùng tham số (địa chỉ + toạ độ ghim) mà POST /orders sẽ dùng,
  // server tự tính khoảng cách từ quán nên số xem trước khớp số chốt đơn.
  const { latitude, longitude } = location;
  useEffect(() => {
    const address = shippingAddress.trim();
    if (subtotal <= 0 || address.length < 5) {
      setQuote(null);
      setFeeError(null);
      return;
    }

    let cancelled = false;
    setIsLoadingFee(true);

    const timer = window.setTimeout(() => {
      deliveryApi
        .getQuote({ shippingAddress: address, latitude, longitude })
        .then((result) => {
          if (cancelled) return;
          setQuote(result);
          setFeeError(null);
        })
        .catch((err) => {
          if (cancelled) return;
          setQuote(null);
          setFeeError(err instanceof Error ? err.message : 'Không tính được phí giao hàng');
        })
        .finally(() => {
          if (!cancelled) setIsLoadingFee(false);
        });
    }, 400);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [shippingAddress, subtotal, latitude, longitude]);

  const selectedOption: StoreQuoteOption | null = (() => {
    if (!quote) return null;
    const byChoice = quote.options.find((o) => o.storeId === chosenStoreId && o.eligible);
    if (byChoice) return byChoice;
    return quote.options.find((o) => o.storeId === quote.recommendedStoreId) ?? null;
  })();
  const noStoreAvailable = quote != null && !quote.options.some((o) => o.eligible);
  const needsManualChoice =
    quote != null && quote.recommendedStoreId == null && !noStoreAvailable && selectedOption == null;

  /**
   * R12: địa chỉ cũ trong sổ chưa ghim toạ độ — server không đề xuất được cơ sở và từ chối nếu khách
   * tự chọn cơ sở, nên phải cập nhật địa chỉ ở trang Hồ sơ trước.
   */
  const savedAddressUnpinned = addressMode === 'saved' && selectedAddressId != null && !hasPin;

  const shippingFee = selectedOption?.shippingFee ?? 0;
  // Số tiền giảm do backend tính (cùng công thức với lúc tạo đơn) — FE không tự tính lại
  const discount = appliedPromotion?.discountApplied ?? 0;
  const total = Math.max(0, subtotal + shippingFee - discount);
  const items = cart?.items ?? [];
  const isEmpty = !isCartLoading && items.length === 0;

  const selectAddress = (address: AddressResponse) => {
    setSelectedAddressId(address.id);
    setReceiverName(address.receiverName);
    setReceiverPhone(address.receiverPhone);
    setLocation(toLocation(address));
    setChosenStoreId(null);
    setErrors({});
  };

  const switchToNewAddress = () => {
    setAddressMode('new');
    setSelectedAddressId(null);
    setReceiverName(user?.fullName ?? '');
    setReceiverPhone(user?.phone ?? '');
    setLocation(EMPTY_LOCATION);
    setChosenStoreId(null);
    setErrors({});
    setPinAttempted(false);
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    // Chặn bấm đúp ngay từ tầng handler: state isSubmitting có thể chưa kịp flush giữa 2 lần click.
    if (isSubmitting) return;
    setApiError(null);

    const nextErrors: FormErrors = {
      receiverName: validateField('receiverName', receiverName),
      receiverPhone: validateField('receiverPhone', receiverPhone),
      shippingAddress: validateField('shippingAddress', shippingAddress),
    };
    setErrors(nextErrors);
    if (Object.values(nextErrors).some(Boolean)) return;

    // Địa chỉ mới bắt buộc ghim vị trí (server vẫn kiểm tra lại — đây chỉ là chặn sớm cho UX)
    if (addressMode === 'new' && !hasPin) {
      setPinAttempted(true);
      return;
    }
    if (savedAddressUnpinned) {
      setApiError(SAVED_UNPINNED_MESSAGE);
      return;
    }

    if (isEmpty) {
      setApiError('Giỏ hàng của bạn đang trống. Vui lòng thêm món trước khi đặt hàng.');
      return;
    }

    if (!selectedOption) {
      setApiError(
        noStoreAvailable
          ? 'Hiện chưa có cơ sở nào phục vụ được đơn này.'
          : 'Vui lòng chọn cơ sở phục vụ đơn hàng.'
      );
      return;
    }

    setIsSubmitting(true);
    try {
      if (!idempotencyKeyRef.current) {
        idempotencyKeyRef.current =
          typeof crypto !== 'undefined' && 'randomUUID' in crypto
            ? crypto.randomUUID()
            : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
      }
      const payload: CreateOrderRequest = {
        idempotencyKey: idempotencyKeyRef.current,
        // Chỉ gửi cơ sở đã chọn khi có toạ độ — địa chỉ chưa ghim mà kèm storeId sẽ bị server từ chối (R12)
        storeId: hasPin ? selectedOption.storeId : undefined,
        addressId: addressMode === 'saved' && selectedAddressId ? selectedAddressId : undefined,
        receiverName: receiverName.trim(),
        receiverPhone: receiverPhone.trim(),
        shippingAddress: shippingAddress.trim(),
        // Địa chỉ trong sổ: server tự lấy toạ độ đã lưu theo addressId
        latitude: addressMode === 'new' ? location.latitude : undefined,
        longitude: addressMode === 'new' ? location.longitude : undefined,
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
  // `rawCode` cho phép bấm thẳng vào một mã trong danh sách gợi ý mà không phải chờ state kịp cập nhật
  const applyPromo = async (rawCode?: string) => {
    const code = (rawCode ?? promoInput).trim();
    if (!code || isApplyingPromo) return;

    setIsApplyingPromo(true);
    try {
      const promotion = await promotionApi.validate({
        code,
        orderAmount: subtotal,
        shippingFee: selectedOption?.shippingFee,
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
          action={<Button onClick={() => navigate('/menu')}>Xem thực đơn</Button>}
        />
      </>
    );
  }

  return (
    <>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/menu">Thực đơn</Link> / <Link to="/cart">Giỏ hàng</Link> / Thanh toán
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
                          setPinAttempted(false);
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
                      {savedAddressUnpinned && (
                        <p className="ui-field__msg ui-field__msg--error" role="alert">
                          Địa chỉ này chưa được ghim trên bản đồ — vui lòng{' '}
                          <Link to="/profile">cập nhật địa chỉ trong Hồ sơ</Link> trước khi đặt hàng.
                        </p>
                      )}
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
                    <div className="ck__block">
                      <AddressLocationFields
                        value={location}
                        onChange={(next) => {
                          setLocation(next);
                          if (errors.shippingAddress) {
                            setErrors((prev) => ({
                              ...prev,
                              shippingAddress: validateField('shippingAddress', next.fullAddress),
                            }));
                          }
                        }}
                        fullAddressLabel="Địa chỉ nhận hàng"
                        fullAddressError={errors.shippingAddress}
                      />
                      {pinMissing && (
                        <p className="ui-field__msg ui-field__msg--error" role="alert">
                          Vui lòng ghim vị trí giao hàng trên bản đồ
                        </p>
                      )}
                    </div>
                  )}

                  {quote && (
                    <div className="ck__block">
                      <div className="ck-store">
                        {selectedOption ? (
                          <div className="ck-store__row">
                            <span>
                              <span className="ck-store__name">Giao từ: {selectedOption.storeName}</span>
                              <span className="ck-store__meta">
                                {' · '}
                                {selectedOption.distanceKm != null
                                  ? `${selectedOption.distanceKm.toFixed(1)} km · `
                                  : ''}
                                {selectedOption.freeship ? 'Miễn phí ship' : formatCurrency(selectedOption.shippingFee)}
                              </span>
                            </span>
                            <Button type="button" size="sm" variant="ghost" onClick={() => setShowStores((v) => !v)}>
                              {showStores ? 'Đóng' : 'Đổi cơ sở'}
                            </Button>
                          </div>
                        ) : noStoreAvailable ? (
                          <p className="ck-store__warn">
                            Hiện chưa có cơ sở nào phục vụ được địa chỉ và giỏ hàng này — xem lý do bên dưới.
                          </p>
                        ) : (
                          <p className="ck-store__meta">
                            Chọn cơ sở phục vụ đơn hàng (ghim vị trí để hệ thống tự chọn cơ sở gần nhất).
                          </p>
                        )}
                        {(showStores || noStoreAvailable || needsManualChoice) && (
                          <ul className="ck-store__options" role="radiogroup" aria-label="Chọn cơ sở">
                            {quote.options.map((option) => (
                              <li key={option.storeId}>
                                <label
                                  className={`ck-store__option${option.eligible ? '' : ' ck-store__option--off'}`}
                                >
                                  <input
                                    type="radio"
                                    name="store"
                                    disabled={!option.eligible}
                                    checked={selectedOption?.storeId === option.storeId}
                                    onChange={() => {
                                      setChosenStoreId(option.storeId);
                                      setShowStores(false);
                                    }}
                                  />
                                  <span>
                                    <strong>{option.storeName}</strong> — {option.storeAddress}
                                    {option.distanceKm != null && ` · ${option.distanceKm.toFixed(1)} km`}
                                    {option.eligible &&
                                      ` · ${option.freeship ? 'Miễn phí ship' : formatCurrency(option.shippingFee)}`}
                                    {option.reasonMessages.map((message) => (
                                      <span key={message} className="ck-store__reason">
                                        {message}
                                      </span>
                                    ))}
                                  </span>
                                </label>
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                    </div>
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
            <div className={`summary__row${selectedOption?.freeship ? ' summary__row--free' : ''}`}>
              <span>
                <Truck size={14} /> Phí giao hàng
              </span>
              <span>
                {isLoadingFee ? 'Đang tính…' : selectedOption?.freeship ? 'Miễn phí' : formatCurrency(shippingFee)}
              </span>
            </div>
            {selectedOption?.feeDescription && (
              <p className="summary__row summary__row--note">{selectedOption.feeDescription}</p>
            )}
            {feeError && !isLoadingFee && (
              <p className="summary__row summary__row--note summary__row--error" role="alert">
                {feeError}
              </p>
            )}
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

              {/*
                Mã đang dùng được — để khách chọn thay vì phải biết trước mã.
                `type="button"` là bắt buộc: khối này nằm trong <form>, thiếu type thì bấm
                vào mã sẽ submit luôn đơn hàng thay vì áp mã.
                Mã chưa đủ đơn tối thiểu vẫn bấm được, chỉ hiện rõ còn thiếu điều kiện gì.
              */}
              {!appliedPromotion && availablePromotions.length > 0 && (
                <div className="ck__promo-picks">
                  <span className="ck__promo-picks-label">Mã đang có</span>
                  <div className="ck__promo-picks-list">
                    {availablePromotions.map((promo) => {
                      const minOrder = promo.minOrderAmount ?? 0;
                      const isBelowMin = subtotal < minOrder;
                      return (
                        <button
                          key={promo.code}
                          type="button"
                          className={`ck__promo-pick${isBelowMin ? ' ck__promo-pick--short' : ''}`}
                          disabled={isApplyingPromo}
                          onClick={() => void applyPromo(promo.code)}
                          title={promo.description}
                        >
                          <span className="ck__promo-pick-code">{promo.code}</span>
                          <span className="ck__promo-pick-value">{describePromotionValue(promo)}</span>
                          {isBelowMin && (
                            <span className="ck__promo-pick-min">Đơn từ {formatCurrency(minOrder)}</span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          </div>
          <div className="card__foot">
            <Button type="submit" block size="lg" loading={isSubmitting} disabled={noStoreAvailable}>
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
                <Truck size={14} /> Miễn phí từ {formatCurrency(200000)}
              </span>
            </div>
          </div>
        </aside>
      </form>
    </>
  );
};
