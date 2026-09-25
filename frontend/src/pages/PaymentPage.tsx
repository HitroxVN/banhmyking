import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Banknote, CheckCircle2, Copy, Home, QrCode, Receipt, RefreshCw } from 'lucide-react';
import { orderApi } from '../api/orderApi';
import { paymentApi } from '../api/paymentApi';
import { Badge, Button, EmptyState, Spinner, StatusBadge, useToast } from '../components/ui';
import { formatCurrency, formatDateTime } from '../utils/formatters';
import { VIETQR_CONFIG, generateVietQrUrl } from '../config/vietqr';
import type { OrderResponse } from '../types/order';
import type { PaymentResponse, ProcessPaymentRequest } from '../types/payment';
import '../styles/components/order.css';
import '../styles/components/payment.css';

/** Dòng "nhãn — giá trị" có nút sao chép, dùng cho thông tin chuyển khoản */
const CopyRow = ({ label, value }: { label: string; value: string }) => {
  const toast = useToast();
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      toast.success('Đã sao chép');
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error('Trình duyệt không cho phép sao chép');
    }
  };

  return (
    <div className="pay__copy">
      <span className="pay__copy-text">
        <span className="pay__copy-label">{label}</span>
        <span className="pay__copy-value">{value}</span>
      </span>
      <Button
        variant="secondary"
        size="sm"
        icon={<Copy size={15} />}
        onClick={handleCopy}
        aria-label={`Sao chép ${label}`}
      >
        {copied ? 'Đã chép' : 'Sao chép'}
      </Button>
    </div>
  );
};

export const PaymentPage = () => {
  const { orderCode } = useParams<{ orderCode: string }>();
  const navigate = useNavigate();
  const location = useLocation();

  const initialOrder = (location.state as { order?: OrderResponse } | undefined)?.order;

  const [order, setOrder] = useState<OrderResponse | null>(initialOrder ?? null);
  const [payment, setPayment] = useState<PaymentResponse | null>(null);
  const [isLoading, setIsLoading] = useState(!initialOrder);
  const [error, setError] = useState<string | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [isCodConfirmed, setIsCodConfirmed] = useState(false);
  const [paymentError, setPaymentError] = useState<string | null>(null);

  // Tải chi tiết đơn hàng & trạng thái thanh toán
  useEffect(() => {
    if (!orderCode) return;

    let alive = true;

    const load = async () => {
      try {
        const latestOrder = await orderApi.getOrderByCode(orderCode);
        if (!alive) return;
        setOrder(latestOrder);

        try {
          const latestPayment = await paymentApi.getPaymentByOrderCode(orderCode);
          if (alive) setPayment(latestPayment);
        } catch {
          // Bản ghi payment có thể chưa được khởi tạo — không phải lỗi chặn trang
        }
      } catch (err) {
        if (alive) {
          setError(err instanceof Error ? err.message : 'Không tìm thấy thông tin đơn hàng với mã này.');
        }
      } finally {
        if (alive) setIsLoading(false);
      }
    };

    load();
    return () => {
      alive = false;
    };
  }, [orderCode]);

  // SePay webhook tự xác nhận → poll tới khi đơn được trả tiền
  useEffect(() => {
    if (!orderCode || order?.paymentMethod !== 'BANK_TRANSFER') return;
    if (payment?.status === 'PAID' || order?.paymentStatus === 'PAID' || order?.status === 'CONFIRMED') return;

    const intervalId = window.setInterval(async () => {
      try {
        const latestPayment = await paymentApi.getPaymentByOrderCode(orderCode);
        if (latestPayment?.status === 'PAID') {
          setPayment(latestPayment);
          setOrder(await orderApi.getOrderByCode(orderCode));
        }
      } catch {
        // Polling ngầm — bỏ qua lỗi mạng tạm thời
      }
    }, 2500);

    return () => window.clearInterval(intervalId);
  }, [orderCode, order?.paymentMethod, order?.paymentStatus, order?.status, payment?.status]);

  const handleProcessPayment = async () => {
    if (!orderCode || !order) return;

    setIsProcessing(true);
    setPaymentError(null);

    try {
      const payload: ProcessPaymentRequest = {
        method: order.paymentMethod,
        transactionRef: `TXN-${Date.now().toString().slice(-8)}`,
        simulateFailure: false,
      };

      const updatedPayment = await paymentApi.processPayment(orderCode, payload);
      setPayment(updatedPayment);

      if (order.paymentMethod === 'COD') setIsCodConfirmed(true);

      setOrder(await orderApi.getOrderByCode(orderCode));
    } catch (err) {
      setPaymentError(
        err instanceof Error ? err.message : 'Giao dịch không thành công. Vui lòng thử lại sau.'
      );
    } finally {
      setIsProcessing(false);
    }
  };

  const isPaid = payment?.status === 'PAID' || order?.paymentStatus === 'PAID';
  const isCodOrder = order?.paymentMethod === 'COD';
  const showReceipt = isPaid || (isCodOrder && isCodConfirmed);

  if (isLoading) {
    return (
      <div className="page-state">
        <Spinner size={34} />
      </div>
    );
  }

  if (error || !order) {
    return (
      <EmptyState
        icon={<Receipt size={30} />}
        title="Không tìm thấy đơn hàng"
        description={error ?? 'Mã đơn hàng không tồn tại hoặc bạn không có quyền xem đơn này.'}
        action={<Button onClick={() => navigate('/')}>Về trang chủ</Button>}
      />
    );
  }

  return (
    <>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">Đơn {order.orderCode}</p>
          <h1 className="page-bar__title">{showReceipt ? 'Đơn hàng đã được ghi nhận' : 'Thanh toán đơn hàng'}</h1>
        </div>
        <StatusBadge status={order.status} />
      </div>

      {showReceipt ? (
        <section className="card">
          <div className="card__head">
            <div className="pay__receipt-head">
              <span className="pay__receipt-icon">
                <CheckCircle2 size={24} />
              </span>
              <div>
                <h2 className="pay__receipt-title">
                  {isCodOrder ? 'Đặt hàng thành công' : 'Thanh toán thành công'}
                </h2>
                <p className="pay__receipt-sub">
                  {isCodOrder
                    ? 'Bạn thanh toán tiền mặt cho tài xế khi nhận hàng.'
                    : 'Chúng tôi đã nhận được thanh toán của bạn.'}
                </p>
              </div>
            </div>
          </div>

          <div className="card__body">
            <div className="pay__meta">
              <span>
                <span className="pay__meta-label">Mã đơn hàng</span>
                <span className="pay__meta-value">{order.orderCode}</span>
              </span>
              <span>
                <span className="pay__meta-label">Thời gian đặt</span>
                <span className="pay__meta-value">{formatDateTime(order.createdAt)}</span>
              </span>
              <span>
                <span className="pay__meta-label">Người nhận</span>
                <span className="pay__meta-value">
                  {order.receiverName} · {order.receiverPhone}
                </span>
              </span>
              <span>
                <span className="pay__meta-label">Địa chỉ giao</span>
                <span className="pay__meta-value">{order.shippingAddress}</span>
              </span>
              <span>
                <span className="pay__meta-label">Phương thức</span>
                <span className="pay__meta-value">
                  {isCodOrder ? 'Tiền mặt khi nhận hàng' : 'Chuyển khoản VietQR'}
                </span>
              </span>
              <span>
                <span className="pay__meta-label">Trạng thái thanh toán</span>
                <span className="pay__meta-value">
                  <Badge tone={isPaid ? 'success' : 'warning'}>
                    {isPaid ? 'Đã thanh toán' : 'Chờ thanh toán'}
                  </Badge>
                </span>
              </span>
            </div>

            {order.note && (
              <p className="summary__row summary__row--note">Ghi chú: {order.note}</p>
            )}

            <div className="pay__items">
              {order.items.map((item) => (
                <div className="pay__item" key={item.id}>
                  <div>
                    <p className="pay__item-name">{item.productName}</p>
                    {item.options.length > 0 && (
                      <p className="pay__item-opts">
                        {item.options.map((option) => option.optionName).join(' · ')}
                      </p>
                    )}
                  </div>
                  <span className="pay__item-qty">
                    {item.quantity} × {formatCurrency(item.unitPrice)}
                  </span>
                  <span className="pay__item-total">{formatCurrency(item.lineTotal)}</span>
                </div>
              ))}
            </div>

            <div className="summary__divider" />
            <div className="summary__row">
              <span>Tạm tính</span>
              <span>{formatCurrency(order.subtotal)}</span>
            </div>
            <div className="summary__row">
              <span>Phí giao hàng</span>
              <span>{order.shippingFee > 0 ? formatCurrency(order.shippingFee) : 'Miễn phí'}</span>
            </div>
            {order.discountAmount > 0 && (
              <div className="summary__row summary__row--free">
                <span>Giảm giá</span>
                <span>-{formatCurrency(order.discountAmount)}</span>
              </div>
            )}
            <div className="summary__divider" />
            <div className="summary__total">
              <span className="summary__total-label">Tổng cộng</span>
              <span className="summary__total-price">{formatCurrency(order.total)}</span>
            </div>
          </div>

          <div className="card__foot">
            <div className="pay__actions">
              <Button icon={<Receipt size={17} />} onClick={() => navigate(`/orders/${order.orderCode}`)}>
                Theo dõi đơn hàng
              </Button>
              <Button variant="secondary" icon={<Home size={17} />} onClick={() => navigate('/')}>
                Về trang chủ
              </Button>
            </div>
          </div>
        </section>
      ) : (
        <div className="pay__grid">
          <section className="card">
            <div className="card__head">
              <h2 className="card__title">
                {isCodOrder ? <Banknote size={19} /> : <QrCode size={19} />}
                {isCodOrder ? 'Thanh toán khi nhận hàng' : 'Quét mã VietQR'}
              </h2>
              {!isCodOrder && <Badge tone="warning">Chờ thanh toán</Badge>}
            </div>

            <div className="card__body pay__qr-card">
              {isCodOrder ? (
                <>
                  <Banknote size={44} />
                  <p className="pay__receipt-sub">
                    Đơn hàng sẽ được giao tới bạn. Vui lòng chuẩn bị{' '}
                    <strong>{formatCurrency(order.total)}</strong> và thanh toán cho tài xế khi nhận hàng.
                  </p>
                  <Button size="lg" loading={isProcessing} onClick={handleProcessPayment}>
                    Xác nhận đặt hàng
                  </Button>
                </>
              ) : (
                <>
                  <div className="pay__qr">
                    <img src={generateVietQrUrl(order.total, order.orderCode)} alt={`Mã VietQR đơn ${order.orderCode}`} />
                  </div>
                  <span className="pay__amount">{formatCurrency(order.total)}</span>
                  <span className="pay__waiting">
                    <RefreshCw size={15} />
                    Đang chờ chuyển khoản — trang tự cập nhật khi nhận được tiền
                  </span>
                </>
              )}
            </div>
          </section>

          <section className="card">
            <div className="card__head">
              <h2 className="card__title">
                <Receipt size={19} />
                Thông tin chuyển khoản
              </h2>
            </div>
            <div className="card__body">
              {!isCodOrder && (
                <>
                  <div className="pay__copy-list">
                    <CopyRow label="Ngân hàng" value={VIETQR_CONFIG.bankId} />
                    <CopyRow label="Số tài khoản" value={VIETQR_CONFIG.accountNo} />
                    <CopyRow label="Chủ tài khoản" value={VIETQR_CONFIG.accountName} />
                    <CopyRow label="Số tiền" value={formatCurrency(order.total)} />
                    <CopyRow label="Nội dung chuyển khoản" value={order.orderCode} />
                  </div>
                  <ol className="pay__steps">
                    <li>Mở app ngân hàng và quét mã QR bên cạnh.</li>
                    <li>Giữ nguyên nội dung chuyển khoản là mã đơn {order.orderCode}.</li>
                    <li>Đơn sẽ tự chuyển sang trạng thái đã thanh toán sau vài giây.</li>
                  </ol>
                </>
              )}

              {isCodOrder && (
                <p className="pay__receipt-sub">
                  Bạn không cần thanh toán trước. Nhấn “Xác nhận đặt hàng” để quán bắt đầu chuẩn bị món.
                </p>
              )}

              {paymentError && (
                <div className="alert-banner alert-error page-alert">
                  <div>{paymentError}</div>
                </div>
              )}

              <div className="summary__divider" />

              {order.items.map((item) => (
                <div className="summary__row" key={item.id}>
                  <span>
                    {item.quantity} × {item.productName}
                  </span>
                  <span>{formatCurrency(item.lineTotal)}</span>
                </div>
              ))}

              <div className="summary__row">
                <span>Phí giao hàng</span>
                <span>{order.shippingFee > 0 ? formatCurrency(order.shippingFee) : 'Miễn phí'}</span>
              </div>

              <div className="summary__divider" />

              <div className="summary__total">
                <span className="summary__total-label">Tổng thanh toán</span>
                <span className="summary__total-price">{formatCurrency(order.total)}</span>
              </div>
            </div>
          </section>
        </div>
      )}
    </>
  );
};
