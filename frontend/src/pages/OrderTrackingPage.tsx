import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Check, Info, Receipt, SearchX, Star, Truck, XCircle } from 'lucide-react';
import { orderApi } from '../api/orderApi';
import { Badge, Button, EmptyState, Spinner, StatusBadge, useConfirm, useToast } from '../components/ui';
import { ReviewFormModal } from '../components/review/ReviewFormModal';
import { formatCurrency, formatDateTime } from '../utils/formatters';
import type { OrderItemResponse, OrderResponse, OrderStatus, OrderStatusHistoryItem, PaymentStatus } from '../types/order';
import '../styles/components/order.css';
import '../styles/components/tracking.css';

/** 6 bước đi của một đơn thành công (CANCELLED/FAILED hiển thị riêng bằng banner) */
const STEPS: OrderStatus[] = ['PENDING', 'CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP', 'DELIVERING', 'DELIVERED'];

const STEP_LABELS: Partial<Record<OrderStatus, string>> = {
  PENDING: 'Đặt hàng',
  CONFIRMED: 'Đã xác nhận',
  PREPARING: 'Đang làm',
  READY_FOR_PICKUP: 'Chờ lấy',
  DELIVERING: 'Đang giao',
  DELIVERED: 'Đã giao',
};

const TERMINAL: OrderStatus[] = ['DELIVERED', 'CANCELLED', 'FAILED'];
const CUSTOMER_CANCELABLE: OrderStatus[] = ['PENDING', 'CONFIRMED'];

const PAYMENT_METHOD_LABEL: Record<string, string> = {
  COD: 'Tiền mặt khi nhận hàng',
  BANK_TRANSFER: 'Chuyển khoản VietQR',
  E_WALLET: 'Ví điện tử',
};

const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  PENDING: 'Chờ thanh toán',
  PAID: 'Đã thanh toán',
  FAILED: 'Thanh toán lỗi',
  REFUNDED: 'Đã hoàn tiền',
};

const PAYMENT_STATUS_TONE: Record<PaymentStatus, 'warning' | 'success' | 'danger' | 'neutral'> = {
  PENDING: 'warning',
  PAID: 'success',
  FAILED: 'danger',
  REFUNDED: 'neutral',
};

const ROLE_LABEL: Record<string, string> = {
  CUSTOMER: 'Khách hàng',
  STAFF: 'Nhân viên',
  SHIPPER: 'Tài xế',
  ADMIN: 'Quản trị viên',
};

export const OrderTrackingPage = () => {
  const { orderCode = '' } = useParams();
  const navigate = useNavigate();
  const confirm = useConfirm();
  const toast = useToast();

  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [history, setHistory] = useState<OrderStatusHistoryItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isCancelling, setIsCancelling] = useState(false);
  /** Món đang mở form đánh giá — null = đóng modal */
  const [reviewItem, setReviewItem] = useState<OrderItemResponse | null>(null);

  const load = useCallback(
    async (silent = false) => {
      if (!orderCode) return;
      if (!silent) setIsLoading(true);

      try {
        const [orderData, historyData] = await Promise.all([
          orderApi.getOrderByCode(orderCode),
          // Lịch sử là thông tin phụ — lỗi ở đây không nên chặn cả trang
          orderApi.getOrderHistory(orderCode).catch(() => [] as OrderStatusHistoryItem[]),
        ]);
        setOrder(orderData);
        setHistory(historyData);
        setError(null);
      } catch (err) {
        if (!silent) setError(err instanceof Error ? err.message : 'Không tải được đơn hàng.');
      } finally {
        if (!silent) setIsLoading(false);
      }
    },
    [orderCode]
  );

  useEffect(() => {
    load();
  }, [load]);

  // Đơn chưa kết thúc thì tự làm mới mỗi 10s, bỏ qua khi tab đang ẩn
  const isFinished = order != null && TERMINAL.includes(order.status);
  useEffect(() => {
    if (!order || isFinished) return;

    const timer = window.setInterval(() => {
      if (document.visibilityState !== 'visible') return;
      load(true);
    }, 10000);

    return () => window.clearInterval(timer);
  }, [order, isFinished, load]);

  const handleCancel = async () => {
    if (!order) return;

    const accepted = await confirm({
      title: 'Huỷ đơn hàng',
      message: `Bạn chắc chắn muốn huỷ đơn ${order.orderCode}? Hành động này không thể hoàn tác.`,
      confirmText: 'Huỷ đơn',
      danger: true,
    });
    if (!accepted) return;

    setIsCancelling(true);
    try {
      await orderApi.cancelOrder(order.orderCode, 'Khách hàng huỷ đơn');
      toast.success('Đã huỷ đơn hàng');
      await load(true);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Huỷ đơn thất bại');
    } finally {
      setIsCancelling(false);
    }
  };

  if (isLoading) {
    return (
      <div className="page-state">
        <Spinner size={28} />
      </div>
    );
  }

  if (error || !order) {
    return (
      <EmptyState
        icon={<SearchX size={30} />}
        title="Không tìm thấy đơn hàng"
        description={error ?? 'Đơn hàng không tồn tại hoặc không thuộc tài khoản của bạn.'}
        action={<Button onClick={() => navigate('/orders')}>Về danh sách đơn</Button>}
      />
    );
  }

  const isStopped = order.status === 'CANCELLED' || order.status === 'FAILED';
  const reached = isStopped
    ? history.reduce((max, item) => Math.max(max, STEPS.indexOf(item.toStatus)), 0)
    : STEPS.indexOf(order.status);

  return (
    <div>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/orders">Đơn hàng của tôi</Link> / Chi tiết
          </p>
          <h1 className="page-bar__title">Theo dõi đơn hàng</h1>
        </div>
        <div className="track__head">
          {CUSTOMER_CANCELABLE.includes(order.status) && (
            <Button variant="secondary" icon={<XCircle size={17} />} loading={isCancelling} onClick={handleCancel}>
              Huỷ đơn
            </Button>
          )}
          <Button icon={<Receipt size={17} />} onClick={() => navigate('/')}>
            Đặt món mới
          </Button>
        </div>
      </div>

      {isStopped && (
        <div className="alert-banner alert-error page-alert" role="alert">
          <XCircle size={18} />
          <div>
            <strong>{order.status === 'CANCELLED' ? 'Đơn đã bị huỷ' : 'Giao hàng thất bại'}</strong>
            {order.cancelReason ? ` — Lý do: ${order.cancelReason}` : ''}
          </div>
        </div>
      )}

      <section className="card">
        <div className="card__body">
          <div className="track__head">
            <div>
              <p className="track__code">{order.orderCode}</p>
              <p className="track__time">Đặt lúc {formatDateTime(order.createdAt)}</p>
            </div>
            <StatusBadge status={order.status} />
          </div>

          <ol className="track__steps">
            {STEPS.map((status, index) => {
              const state =
                index === reached && isStopped
                  ? 'stopped'
                  : index <= reached
                    ? index === reached && !isFinished
                      ? 'current'
                      : 'done'
                    : 'todo';

              return (
                <li key={status} className={`track__step track__step--${state}`}>
                  <span className="track__dot" aria-hidden="true">
                    {state === 'done' ? (
                      <Check size={16} />
                    ) : state === 'stopped' ? (
                      <XCircle size={16} />
                    ) : state === 'current' ? (
                      <Truck size={15} />
                    ) : (
                      index + 1
                    )}
                  </span>
                  <span className="track__label">{STEP_LABELS[status]}</span>
                </li>
              );
            })}
          </ol>

          {!isFinished && !isStopped && (
            <p className="track__note">
              <Info size={16} />
              Trạng thái tự cập nhật mỗi 10 giây — bạn có thể để trang này mở trong lúc chờ.
            </p>
          )}
        </div>
      </section>

      <div className="order-grid track__grid">
        <div>
          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Món đã đặt ({order.items.length})</h2>
            </div>
            <div className="card__body">
              {order.items.map((item) => (
                <div className="cart-row" key={item.id}>
                  <div className="cart-row__media">
                    {item.productImageUrl ? (
                      <img className="cart-row__img" src={item.productImageUrl} alt={item.productName} />
                    ) : (
                      <span className="pcard__placeholder" aria-hidden="true">
                        <Receipt size={26} />
                      </span>
                    )}
                  </div>
                  <div>
                    <p className="cart-row__name">{item.productName}</p>
                    {item.options.length > 0 && (
                      <p className="cart-row__opts">
                        {item.options.map((option) => (
                          <span className="cart-row__opt" key={option.id}>
                            {option.optionName}
                          </span>
                        ))}
                      </p>
                    )}
                    <p className="cart-row__unit">
                      {formatCurrency(item.unitPrice)} × {item.quantity}
                    </p>
                  </div>
                  <div className="cart-row__side">
                    <span className="cart-row__total">{formatCurrency(item.lineTotal)}</span>
                    {/* Backend chỉ nhận đánh giá khi đơn đã giao */}
                    {order.status === 'DELIVERED' && (
                      <Button
                        size="sm"
                        variant="secondary"
                        icon={<Star size={15} />}
                        onClick={() => setReviewItem(item)}
                      >
                        Đánh giá
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </section>

          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Lịch sử trạng thái</h2>
            </div>
            <div className="card__body">
              {history.length === 0 ? (
                <p className="tl__empty">Chưa có ghi nhận chuyển trạng thái nào.</p>
              ) : (
                <ul className="tl">
                  {[...history].reverse().map((item) => (
                    <li className="tl__item" key={item.id}>
                      <span className="tl__dot" aria-hidden="true" />
                      <div className="tl__head">
                        <StatusBadge status={item.toStatus} />
                        <span className="tl__time">{formatDateTime(item.createdAt)}</span>
                      </div>
                      {item.note && <p className="tl__note">{item.note}</p>}
                      <p className="tl__by">
                        {item.changedByName ? `Thực hiện bởi ${item.changedByName}` : 'Hệ thống ghi nhận'}
                        {item.changedByRole ? ` · ${ROLE_LABEL[item.changedByRole] ?? item.changedByRole}` : ''}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </section>
        </div>

        <aside>
          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Thanh toán</h2>
              {order.paymentStatus && (
                <Badge tone={PAYMENT_STATUS_TONE[order.paymentStatus]}>
                  {PAYMENT_STATUS_LABEL[order.paymentStatus]}
                </Badge>
              )}
            </div>
            <div className="card__body">
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
                  <span>Giảm giá{order.promotionCode ? ` (${order.promotionCode})` : ''}</span>
                  <span>-{formatCurrency(order.discountAmount)}</span>
                </div>
              )}

              <div className="summary__divider" />

              <div className="summary__total">
                <span className="summary__total-label">Tổng cộng</span>
                <span className="summary__total-price">{formatCurrency(order.total)}</span>
              </div>

              <div className="summary__divider" />

              <div className="summary__row">
                <span>Hình thức</span>
                <span>{PAYMENT_METHOD_LABEL[order.paymentMethod] ?? order.paymentMethod}</span>
              </div>
              <div className="summary__row">
                <span>Người nhận</span>
                <span>
                  {order.receiverName} · {order.receiverPhone}
                </span>
              </div>
              <div className="summary__row">
                <span>Giao tới</span>
                <span>{order.shippingAddress}</span>
              </div>
              {order.note && (
                <div className="summary__row summary__row--note">
                  <span>Ghi chú: {order.note}</span>
                </div>
              )}
              {order.shipperName && (
                <div className="summary__row">
                  <span>Tài xế</span>
                  <span>
                    {order.shipperName}
                    {order.shipperPhone ? ` · ${order.shipperPhone}` : ''}
                  </span>
                </div>
              )}
              {order.deliveredAt && (
                <div className="summary__row">
                  <span>Giao lúc</span>
                  <span>{formatDateTime(order.deliveredAt)}</span>
                </div>
              )}
            </div>
          </section>
        </aside>
      </div>

      <ReviewFormModal item={reviewItem} onClose={() => setReviewItem(null)} />
    </div>
  );
};
