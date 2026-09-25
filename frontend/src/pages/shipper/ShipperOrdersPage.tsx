import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Bike,
  CheckCircle2,
  History,
  MapPin,
  Package,
  PackageCheck,
  Phone,
  Search,
  StickyNote,
  TriangleAlert,
  Wallet,
  XCircle,
} from 'lucide-react';
import {
  Badge,
  Button,
  EmptyState,
  Input,
  Modal,
  PageHeader,
  Skeleton,
  StatusBadge,
  Tabs,
  Textarea,
  useToast,
} from '../../components/ui';
import { shipperOrderApi, type OrderStatusHistoryItem } from '../../api/shipperOrderApi';
import { isFinalStatus, type OrderResponse } from '../../types/order';
import { PAYMENT_METHOD_LABEL } from '../../utils/payment';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { broadcastOrderChange, orderSyncChannel, playNotificationSound } from '../../utils/orderSyncChannel';
import '../../styles/components/shipper-orders.css';

type TabType = 'DELIVERING' | 'READY_FOR_PICKUP' | 'ALL' | 'DELIVERED' | 'FAILED';

const POLL_MS = 3000;

const FAILURE_REASONS = [
  'Khách không nhấc máy sau 3 lần gọi',
  'Sai địa chỉ / không tìm thấy nhà khách',
  'Khách từ chối nhận hàng',
  'Khách hẹn giao lại vào thời gian khác',
  'Không thể liên lạc được với khách hàng',
];

const REJECT_REASONS = [
  'Xe gặp sự cố hỏng hóc giữa đường',
  'Khoảng cách giao hàng quá xa khu vực',
  'Đang chở nhiều đơn cồng kềnh, quá tải',
  'Đã hết ca làm việc / có việc bận đột xuất',
  'Thời tiết xấu / mưa ngập không thể di chuyển',
];

interface NewOrderNotice {
  orderCode: string;
  receiverName: string;
  shippingAddress: string;
}

export const ShipperOrdersPage = () => {
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [activeTab, setActiveTab] = useState<TabType>('DELIVERING');
  const [searchQuery, setSearchQuery] = useState('');

  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [expandedOrders, setExpandedOrders] = useState<Record<string, boolean>>({});
  const [busyOrderCode, setBusyOrderCode] = useState<string | null>(null);
  const [newNotice, setNewNotice] = useState<NewOrderNotice | null>(null);

  const [deliveringOrder, setDeliveringOrder] = useState<OrderResponse | null>(null);
  const [failingOrder, setFailingOrder] = useState<OrderResponse | null>(null);
  const [rejectingOrder, setRejectingOrder] = useState<OrderResponse | null>(null);
  const [historyOrder, setHistoryOrder] = useState<OrderResponse | null>(null);

  // Chỉ báo đơn mới cho những đơn xuất hiện SAU lần tải đầu tiên
  const isInitialFetchDoneRef = useRef(false);
  const knownReadyCodesRef = useRef<Set<string>>(new Set());

  const toast = useToast();

  const fetchOrders = useCallback(async (isSilent = false) => {
    if (!isSilent) setIsLoading(true);
    try {
      const data = await shipperOrderApi.getAssignedOrders(undefined, 0, 100);
      const fetched = data.content ?? [];
      setOrders(fetched);
      setErrorMsg(null);

      const readyOrders = fetched.filter((order) => order.status === 'READY_FOR_PICKUP');
      const readyCodes = new Set(readyOrders.map((order) => order.orderCode));

      if (isInitialFetchDoneRef.current) {
        const newlyAssigned = readyOrders.filter((order) => !knownReadyCodesRef.current.has(order.orderCode));

        if (newlyAssigned.length > 0) {
          const newest = newlyAssigned[0];
          playNotificationSound();
          toast.success(`Đơn ${newest.orderCode} vừa được phân công cho bạn`);
          setNewNotice({
            orderCode: newest.orderCode,
            receiverName: newest.receiverName,
            shippingAddress: newest.shippingAddress,
          });
          // Không có đơn nào đang giao thì nhảy thẳng sang tab chờ lấy bánh
          if (!fetched.some((order) => order.status === 'DELIVERING')) {
            setActiveTab('READY_FOR_PICKUP');
          }
        }
      } else {
        isInitialFetchDoneRef.current = true;
        if (!fetched.some((order) => order.status === 'DELIVERING') && readyOrders.length > 0) {
          setActiveTab('READY_FOR_PICKUP');
        }
      }

      knownReadyCodesRef.current = readyCodes;
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Không tải được danh sách đơn hàng.');
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [toast]);

  useEffect(() => {
    void fetchOrders();

    const intervalId = setInterval(() => void fetchOrders(true), POLL_MS);

    const handleSync = () => void fetchOrders(true);
    orderSyncChannel?.addEventListener('message', handleSync);

    const handleVisibility = () => {
      if (document.visibilityState === 'visible') void fetchOrders(true);
    };
    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('focus', handleSync);

    return () => {
      clearInterval(intervalId);
      orderSyncChannel?.removeEventListener('message', handleSync);
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('focus', handleSync);
    };
  }, [fetchOrders]);

  const reload = () => {
    setIsRefreshing(true);
    void fetchOrders(true);
  };

  const replaceOrder = (updated: OrderResponse) => {
    setOrders((prev) => prev.map((order) => (order.orderCode === updated.orderCode ? updated : order)));
  };

  const handleStartDelivering = async (order: OrderResponse) => {
    setBusyOrderCode(order.orderCode);
    try {
      const updated = await shipperOrderApi.startDelivering(order.orderCode);
      broadcastOrderChange('ORDER_ACCEPTED', { orderCode: order.orderCode });
      replaceOrder(updated);
      setNewNotice(null);
      toast.success(`Đã nhận đơn ${order.orderCode}, bắt đầu đi giao`);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Không thể bắt đầu đi giao.');
    } finally {
      setBusyOrderCode(null);
    }
  };

  const handleConfirmDelivery = async (note: string) => {
    if (!deliveringOrder) return;

    const updated = await shipperOrderApi.confirmDelivery(deliveringOrder.orderCode, note);
    broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode: deliveringOrder.orderCode });
    replaceOrder(updated);
    setDeliveringOrder(null);
    toast.success(`Đã giao thành công đơn ${updated.orderCode}`);
  };

  const handleFailDelivery = async (reason: string) => {
    if (!failingOrder) return;

    const updated = await shipperOrderApi.failDelivery(failingOrder.orderCode, reason);
    broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode: failingOrder.orderCode });
    replaceOrder(updated);
    setFailingOrder(null);
    toast.success(`Đã ghi nhận giao thất bại đơn ${updated.orderCode}`);
  };

  const handleRejectOrder = async (reason: string) => {
    if (!rejectingOrder) return;

    await shipperOrderApi.rejectOrder(rejectingOrder.orderCode, reason);
    broadcastOrderChange('ORDER_REJECTED', { orderCode: rejectingOrder.orderCode });
    // Đơn đã trả về quán nên gỡ khỏi danh sách của tài xế
    setOrders((prev) => prev.filter((order) => order.orderCode !== rejectingOrder.orderCode));
    setNewNotice(null);
    setRejectingOrder(null);
    toast.success(`Đã từ chối đơn ${rejectingOrder.orderCode}, đơn trả về quán`);
  };

  const stats = useMemo(
    () => ({
      delivering: orders.filter((order) => order.status === 'DELIVERING').length,
      ready: orders.filter((order) => order.status === 'READY_FOR_PICKUP').length,
      delivered: orders.filter((order) => order.status === 'DELIVERED').length,
      failed: orders.filter((order) => order.status === 'FAILED').length,
      codCollected: orders
        .filter((order) => order.status === 'DELIVERED' && order.paymentMethod === 'COD')
        .reduce((sum, order) => sum + Number(order.total || 0), 0),
    }),
    [orders]
  );

  const filteredOrders = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();

    return orders.filter((order) => {
      if (activeTab !== 'ALL' && order.status !== activeTab) return false;
      if (!query) return true;
      return (
        order.orderCode.toLowerCase().includes(query) ||
        (order.receiverName?.toLowerCase().includes(query) ?? false) ||
        (order.receiverPhone?.toLowerCase().includes(query) ?? false) ||
        (order.shippingAddress?.toLowerCase().includes(query) ?? false)
      );
    });
  }, [orders, activeTab, searchQuery]);

  return (
    <>
      <PageHeader
        title="Đơn hàng của tôi"
        subtitle="Theo dõi đơn được phân công, gọi khách, dẫn đường và cập nhật tiến trình giao"
        onRefresh={reload}
        isRefreshing={isRefreshing}
      />

      {newNotice && (
        <div className="alert-banner alert-success shipper__notice" role="alert">
          <TriangleAlert size={18} />
          <div>
            <strong>Đơn {newNotice.orderCode} vừa được phân công cho bạn.</strong> Giao tới{' '}
            {newNotice.shippingAddress} (khách {newNotice.receiverName}).
          </div>
          <Button
            size="sm"
            variant="primary"
            onClick={() => {
              setActiveTab('READY_FOR_PICKUP');
              setNewNotice(null);
            }}
          >
            Xem ngay
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setNewNotice(null)}>
            Đóng
          </Button>
        </div>
      )}

      <section className="shipper__stats">
        <KpiCard icon={<Bike size={18} />} value={String(stats.delivering)} label="Đang giao trên đường" />
        <KpiCard icon={<PackageCheck size={18} />} value={String(stats.ready)} label="Chờ lấy tại quán" />
        <KpiCard icon={<CheckCircle2 size={18} />} value={String(stats.delivered)} label="Giao thành công" />
        <KpiCard icon={<Wallet size={18} />} value={formatCurrency(stats.codCollected)} label="Tiền COD đã thu" />
      </section>

      <section className="card">
        <div className="shipper__controls">
          <Tabs
            value={activeTab}
            onChange={setActiveTab}
            tabs={[
              { key: 'DELIVERING', label: 'Đang giao', count: stats.delivering },
              { key: 'READY_FOR_PICKUP', label: 'Chờ lấy bánh', count: stats.ready },
              { key: 'ALL', label: 'Tất cả đơn', count: orders.length },
              { key: 'DELIVERED', label: 'Đã giao', count: stats.delivered },
              { key: 'FAILED', label: 'Giao thất bại', count: stats.failed },
            ]}
          />

          <div className="shipper__search">
            <Input
              type="search"
              icon={<Search size={16} />}
              placeholder="Tìm mã đơn, SĐT, tên khách..."
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
            />
          </div>
        </div>
      </section>

      {isLoading && orders.length === 0 ? (
        <section className="card">
          <div className="card__body">
            <Skeleton variant="card" count={3} />
          </div>
        </section>
      ) : errorMsg ? (
        <section className="card">
          <div className="card__body">
            <EmptyState
              icon={<XCircle size={30} />}
              title="Không tải được dữ liệu"
              description={errorMsg}
              action={<Button onClick={() => void fetchOrders(false)}>Thử lại</Button>}
            />
          </div>
        </section>
      ) : filteredOrders.length === 0 ? (
        <section className="card">
          <div className="card__body">
            <EmptyState
              icon={<Package size={30} />}
              title="Chưa có đơn nào ở mục này"
              description="Đơn được quán phân công sẽ tự hiện tại đây theo thời gian thực."
              action={<Button onClick={reload}>Tải lại dữ liệu</Button>}
            />
          </div>
        </section>
      ) : (
        <div className="shipper__grid">
          {filteredOrders.map((order) => (
            <OrderCard
              key={order.id}
              order={order}
              expanded={!!expandedOrders[order.orderCode]}
              busy={busyOrderCode === order.orderCode}
              onToggleExpand={() =>
                setExpandedOrders((prev) => ({ ...prev, [order.orderCode]: !prev[order.orderCode] }))
              }
              onHistory={() => setHistoryOrder(order)}
              onAccept={() => void handleStartDelivering(order)}
              onReject={() => setRejectingOrder(order)}
              onDeliver={() => setDeliveringOrder(order)}
              onFail={() => setFailingOrder(order)}
            />
          ))}
        </div>
      )}

      {deliveringOrder && (
        <DeliveryConfirmModal order={deliveringOrder} onClose={() => setDeliveringOrder(null)} onConfirm={handleConfirmDelivery} />
      )}

      {failingOrder && (
        <ReasonModal
          title={`Báo giao thất bại — ${failingOrder.orderCode}`}
          intro="Đơn sẽ chuyển sang trạng thái Giao thất bại và báo ngay về cho quán."
          label="Lý do giao thất bại"
          presets={FAILURE_REASONS}
          placeholder="Ví dụ: đã gọi 3 lần nhưng khách thuê bao, bảo vệ không cho gửi..."
          confirmText="Xác nhận thất bại"
          onClose={() => setFailingOrder(null)}
          onConfirm={handleFailDelivery}
        />
      )}

      {rejectingOrder && (
        <ReasonModal
          title={`Từ chối nhận đơn — ${rejectingOrder.orderCode}`}
          intro="Đơn sẽ được gỡ khỏi danh sách của bạn và trả về quán để điều phối tài xế khác."
          label="Lý do từ chối"
          presets={REJECT_REASONS}
          placeholder="Ví dụ: xe thủng xăm đang sửa, kẹt mưa bão, hết ca làm..."
          confirmText="Xác nhận từ chối"
          onClose={() => setRejectingOrder(null)}
          onConfirm={handleRejectOrder}
        />
      )}

      {historyOrder && <HistoryModal order={historyOrder} onClose={() => setHistoryOrder(null)} />}
    </>
  );
};

const KpiCard = ({ icon, value, label }: { icon: React.ReactNode; value: string; label: string }) => (
  <div className="card shipper__kpi">
    <span className="shipper__kpi-icon" aria-hidden="true">
      {icon}
    </span>
    <span>
      <strong>{value}</strong>
      <em>{label}</em>
    </span>
  </div>
);

interface OrderCardProps {
  order: OrderResponse;
  expanded: boolean;
  busy: boolean;
  onToggleExpand: () => void;
  onHistory: () => void;
  onAccept: () => void;
  onReject: () => void;
  onDeliver: () => void;
  onFail: () => void;
}

const OrderCard = ({
  order,
  expanded,
  busy,
  onToggleExpand,
  onHistory,
  onAccept,
  onReject,
  onDeliver,
  onFail,
}: OrderCardProps) => {
  const isReady = order.status === 'READY_FOR_PICKUP';
  const isDelivering = order.status === 'DELIVERING';
  const isCod = order.paymentMethod === 'COD';
  const isPaid = order.paymentStatus === 'PAID';

  return (
    <article className={`card shipper__card${isDelivering ? ' shipper__card--active' : ''}`}>
      <header className="shipper__head">
        <div className="shipper__ident">
          <span className="shipper__code">#{order.orderCode}</span>
          <StatusBadge status={order.status} />
        </div>
        <div className="shipper__head-right">
          <span className="shipper__time">{formatDateTime(order.createdAt)}</span>
          <Button size="sm" variant="ghost" icon={<History size={15} />} onClick={onHistory}>
            Lịch sử
          </Button>
        </div>
      </header>

      <div className="shipper__body">
        <div className="shipper__contact">
          <p className="shipper__recv">
            {order.receiverName}
            <span>{order.receiverPhone}</span>
          </p>
          <div className="shipper__contact-actions">
            <a className="shipper__tel" href={`tel:${order.receiverPhone}`}>
              <Phone size={15} /> Gọi ngay
            </a>
            <a
              className="shipper__map"
              href={`https://maps.google.com/?q=${encodeURIComponent(order.shippingAddress)}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              <MapPin size={15} /> Chỉ đường
            </a>
          </div>
        </div>

        <p className="shipper__address">
          <MapPin size={15} />
          <span>{order.shippingAddress}</span>
        </p>

        {order.note && (
          <p className="shipper__note">
            <StickyNote size={15} />
            <span>Ghi chú: “{order.note}”</span>
          </p>
        )}

        <div className={`shipper__money${isCod && !isPaid ? ' shipper__money--due' : ''}`}>
          <span className="shipper__money-label">
            {isCod && !isPaid ? 'Số tiền cần thu (COD)' : isPaid ? 'Đã thanh toán trực tuyến' : 'Thanh toán'}
          </span>
          <strong>{isCod && !isPaid ? formatCurrency(order.total) : formatCurrency(0)}</strong>
          <em>
            {isCod && !isPaid
              ? `Thu đủ tiền mặt trước khi bàn giao · ${PAYMENT_METHOD_LABEL[order.paymentMethod]}`
              : 'Khách đã trả trước, không thu thêm.'}
          </em>
        </div>

        <button type="button" className="shipper__items-toggle" aria-expanded={expanded} onClick={onToggleExpand}>
          {expanded ? 'Thu gọn chi tiết món' : `Xem ${order.items.length} món & topping`}
        </button>

        {expanded && (
          <ul className="shipper__items">
            {order.items.map((item) => (
              <li key={item.id}>
                <span className="shipper__item-qty">{item.quantity}×</span>
                <span className="shipper__item-body">
                  <strong>{item.productName}</strong>
                  {item.options && item.options.length > 0 && (
                    <em>{item.options.map((option) => `+ ${option.optionName}`).join(' · ')}</em>
                  )}
                </span>
                <span className="shipper__item-price">{formatCurrency(item.lineTotal)}</span>
              </li>
            ))}
          </ul>
        )}

        {order.status === 'DELIVERED' && (
          <p className="shipper__done">
            <CheckCircle2 size={16} />
            Đã giao lúc {formatDateTime(order.deliveredAt || order.updatedAt)}
          </p>
        )}

        {order.status === 'FAILED' && (
          <p className="shipper__failed">
            <TriangleAlert size={16} />
            {order.cancelReason || 'Không ghi rõ lý do thất bại.'}
          </p>
        )}
      </div>

      {!isFinalStatus(order.status) && (
        <footer className="shipper__foot">
          {isReady && (
            <>
              <Button variant="ghost" disabled={busy} onClick={onReject}>
                Từ chối đơn
              </Button>
              <Button variant="primary" icon={<Bike size={17} />} loading={busy} onClick={onAccept}>
                Nhận đơn & bắt đầu giao
              </Button>
            </>
          )}

          {isDelivering && (
            <>
              <Button variant="ghost" onClick={onFail}>
                Báo giao thất bại
              </Button>
              <Button variant="success" icon={<CheckCircle2 size={17} />} onClick={onDeliver}>
                Xác nhận đã giao
              </Button>
            </>
          )}
        </footer>
      )}
    </article>
  );
};

/** Xác nhận đã giao — nhắc tài xế thu đủ tiền COD trước khi hoàn tất. */
const DeliveryConfirmModal = ({
  order,
  onClose,
  onConfirm,
}: {
  order: OrderResponse;
  onClose: () => void;
  onConfirm: (note: string) => Promise<void>;
}) => {
  const [note, setNote] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const needsCash = order.paymentMethod === 'COD' && order.paymentStatus !== 'PAID';

  const handleConfirm = async () => {
    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      await onConfirm(note.trim());
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Xác nhận giao hàng thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      title={`Xác nhận đã giao đơn ${order.orderCode}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button variant="success" loading={isSubmitting} onClick={() => void handleConfirm()}>
            Hoàn tất đơn hàng
          </Button>
        </>
      }
    >
      <div className="shipper__modal">
        {errorMsg && (
          <div className="alert-banner alert-error" role="alert">
            <XCircle size={17} />
            <div>{errorMsg}</div>
          </div>
        )}

        <dl className="shipper__summary">
          <div>
            <dt>Khách nhận</dt>
            <dd>
              {order.receiverName} · {order.receiverPhone}
            </dd>
          </div>
          <div>
            <dt>Địa chỉ</dt>
            <dd>{order.shippingAddress}</dd>
          </div>
        </dl>

        <div className={`shipper__cash${needsCash ? ' shipper__cash--due' : ''}`}>
          <span>{needsCash ? 'Thu tiền mặt (COD)' : 'Đã thanh toán trước'}</span>
          <strong>{needsCash ? formatCurrency(order.total) : '0 đ'}</strong>
          <em>
            {needsCash
              ? 'Kiểm tra kỹ trước khi bấm hoàn tất đơn hàng.'
              : 'Không thu thêm bất kỳ khoản nào từ khách.'}
          </em>
        </div>

        <Textarea
          label="Ghi chú giao hàng (không bắt buộc)"
          rows={2}
          placeholder="Ví dụ: đã gửi cho bảo vệ tòa nhà..."
          value={note}
          onChange={(event) => setNote(event.target.value)}
        />
      </div>
    </Modal>
  );
};

/** Modal lý do dùng chung cho "giao thất bại" và "từ chối nhận đơn". */
const ReasonModal = ({
  title,
  intro,
  label,
  presets,
  placeholder,
  confirmText,
  onClose,
  onConfirm,
}: {
  title: string;
  intro: string;
  label: string;
  presets: string[];
  placeholder: string;
  confirmText: string;
  onClose: () => void;
  onConfirm: (reason: string) => Promise<void>;
}) => {
  const [reason, setReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleConfirm = async () => {
    const trimmed = reason.trim();
    if (!trimmed) {
      setErrorMsg('Vui lòng chọn hoặc nhập lý do.');
      return;
    }

    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      await onConfirm(trimmed);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Không gửi được lý do.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      title={title}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Quay lại
          </Button>
          <Button
            variant="danger"
            loading={isSubmitting}
            disabled={!reason.trim()}
            onClick={() => void handleConfirm()}
          >
            {confirmText}
          </Button>
        </>
      }
    >
      <div className="shipper__modal">
        {errorMsg && (
          <div className="alert-banner alert-error" role="alert">
            <XCircle size={17} />
            <div>{errorMsg}</div>
          </div>
        )}

        <p className="shipper__intro">{intro}</p>

        <div className="ui-field">
          <span className="ui-field__label">Chọn nhanh lý do</span>
          <div className="shipper__presets">
            {presets.map((preset) => (
              <button
                key={preset}
                type="button"
                className={`ui-chip${reason === preset ? ' ui-chip--active' : ''}`}
                onClick={() => setReason(preset)}
              >
                {preset}
              </button>
            ))}
          </div>
        </div>

        <Textarea
          label={label}
          required
          rows={3}
          placeholder={placeholder}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      </div>
    </Modal>
  );
};

const HistoryModal = ({ order, onClose }: { order: OrderResponse; onClose: () => void }) => {
  const [history, setHistory] = useState<OrderStatusHistoryItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    shipperOrderApi
      .getOrderHistory(order.orderCode)
      .then((data) => {
        if (!cancelled) setHistory(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setErrorMsg(err instanceof Error ? err.message : 'Không tải được lịch sử đơn hàng.');
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [order.orderCode]);

  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      title={`Lịch sử đơn ${order.orderCode}`}
      footer={
        <Button variant="secondary" onClick={onClose}>
          Đóng
        </Button>
      }
    >
      <div className="shipper__modal">
        {errorMsg && (
          <div className="alert-banner alert-error" role="alert">
            <XCircle size={17} />
            <div>{errorMsg}</div>
          </div>
        )}

        {isLoading ? (
          <Skeleton variant="row" count={3} />
        ) : history.length === 0 ? (
          <p className="shipper__intro">Chưa có lịch sử trạng thái cho đơn này.</p>
        ) : (
          <ol className="shipper__timeline">
            {history.map((item) => (
              <li key={item.id}>
                <span className="shipper__timeline-dot" aria-hidden="true" />
                <div>
                  <div className="shipper__timeline-top">
                    <Badge tone="neutral">{item.toStatus}</Badge>
                    <span>{formatDateTime(item.createdAt)}</span>
                  </div>
                  {item.changedByName && (
                    <p>
                      Thực hiện bởi <strong>{item.changedByName}</strong>
                      {item.changedByRole ? ` (${item.changedByRole})` : ''}
                    </p>
                  )}
                  {item.note && <p className="shipper__timeline-note">{item.note}</p>}
                </div>
              </li>
            ))}
          </ol>
        )}
      </div>
    </Modal>
  );
};
