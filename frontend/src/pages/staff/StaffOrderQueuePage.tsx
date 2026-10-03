import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import {
  Bike,
  ChefHat,
  ClipboardList,
  Clock,
  ArrowRightLeft,
  MapPin,
  PackageCheck,
  Phone,
  RotateCcw,
  Search,
  StickyNote,
  XCircle,
} from 'lucide-react';
import {
  Badge,
  Button,
  EmptyState,
  Input,
  PageHeader,
  Skeleton,
  StatusBadge,
  Tabs,
  useToast,
} from '../../components/ui';
import { AssignShipperModal } from '../../components/order/AssignShipperModal';
import { CancelOrderModal } from '../../components/order/CancelOrderModal';
import { TransferStoreModal } from '../../components/order/TransferStoreModal';
import { RefundOrderModal } from '../../components/order/RefundOrderModal';
import { EmptyPlate } from '../../components/illustrations/FoodDoodles';
import { staffOrderApi } from '../../api/staffOrderApi';
import { useAuth } from '../../context/useAuth';
import { useStoreScope } from '../../context/useStoreScope';
import type { OrderResponse, OrderStatus } from '../../types/order';
import { isFinalStatus } from '../../types/order';
import { ORDER_STATUS_LABEL } from '../../utils/orderStatus';
import { PAYMENT_METHOD_LABEL } from '../../utils/payment';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { orderComponentsText } from '../../utils/pricing';
import { useLiveRefresh } from '../../hooks/useLiveRefresh';
import { broadcastOrderChange } from '../../utils/orderSyncChannel';
import '../../styles/components/staff-queue.css';

type TabFilter = 'ALL' | 'NEW' | 'PREPARING' | 'READY' | 'DELIVERING' | 'HISTORY';

/** Đơn chờ quá lâu thì tô đỏ để bếp ưu tiên xử lý */
const URGENT_AFTER_MINUTES = 15;

const TAB_STATUSES: Record<Exclude<TabFilter, 'ALL' | 'HISTORY'>, OrderStatus[]> = {
  NEW: ['PENDING', 'CONFIRMED'],
  PREPARING: ['PREPARING'],
  READY: ['READY_FOR_PICKUP'],
  DELIVERING: ['DELIVERING'],
};

export const StaffOrderQueuePage = () => {
  const { user } = useAuth();
  const { storeId } = useStoreScope();
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [activeTab, setActiveTab] = useState<TabFilter>('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [busyOrderCode, setBusyOrderCode] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());

  const [assigningOrder, setAssigningOrder] = useState<OrderResponse | null>(null);
  const [cancellingOrder, setCancellingOrder] = useState<OrderResponse | null>(null);
  const [refundingOrder, setRefundingOrder] = useState<OrderResponse | null>(null);
  const [transferringOrder, setTransferringOrder] = useState<OrderResponse | null>(null);
  const canTransfer = user?.role === 'MANAGER' || user?.role === 'ADMIN';

  const toast = useToast();

  // `manual` = người dùng bấm Làm mới; polling nền chạy im lặng, không nhấp nháy spinner
  const fetchOrders = useCallback(async (manual = false) => {
    if (manual) setIsRefreshing(true);
    try {
      const data = await staffOrderApi.getOrderQueue(undefined, 0, 50, storeId ?? undefined);
      setOrders(data.content ?? []);
      setNow(Date.now());
    } catch (err: unknown) {
      if (manual) setErrorMsg(err instanceof Error ? err.message : 'Không tải được hàng đợi đơn hàng.');
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [storeId]);

  // ADMIN chưa chọn cơ sở: không tải và không polling (tránh truy vấn toàn chuỗi vô ích)
  const needsStore = user?.role === 'ADMIN' && storeId == null;

  useEffect(() => {
    if (needsStore) return;
    void fetchOrders();
  }, [fetchOrders, needsStore]);

  // Realtime: chỉ tải lại khi đơn thuộc cơ sở đang xem đổi (admin xem từng cơ sở một)
  useLiveRefresh(() => fetchOrders(), {
    enabled: !needsStore,
    // previousStoreId: đơn vừa chuyển đi khỏi cơ sở này thì hàng đợi cũng phải tải lại
    matchOrder: (signal) =>
      storeId == null || signal.storeId === storeId || signal.previousStoreId === storeId,
  });

  const reload = () => {
    setErrorMsg(null);
    setIsLoading(true);
    void fetchOrders(true);
  };

  const replaceOrder = (updated: OrderResponse) => {
    setOrders((prev) => prev.map((order) => (order.orderCode === updated.orderCode ? updated : order)));
    setNow(Date.now());
  };

  const handleStartPreparing = async (order: OrderResponse) => {
    setBusyOrderCode(order.orderCode);
    try {
      const updated = await staffOrderApi.startPreparingOrder(order);
      broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode: order.orderCode });
      replaceOrder(updated);
      toast.success(`Đơn ${order.orderCode} đã vào bếp`);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Không thể chuyển đơn sang Đang chế biến.');
    } finally {
      setBusyOrderCode(null);
    }
  };

  const handleMarkReady = async (order: OrderResponse) => {
    setBusyOrderCode(order.orderCode);
    try {
      const updated = await staffOrderApi.markReadyForPickup(order.orderCode);
      broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode: order.orderCode });
      replaceOrder(updated);
      toast.success(`Đơn ${order.orderCode} đã xong, chờ tài xế nhận`);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Không thể chuyển đơn sang Chờ lấy hàng.');
    } finally {
      setBusyOrderCode(null);
    }
  };

  const metrics = useMemo(
    () => ({
      pending: orders.filter((order) => order.status === 'PENDING' || order.status === 'CONFIRMED').length,
      preparing: orders.filter((order) => order.status === 'PREPARING').length,
      ready: orders.filter((order) => order.status === 'READY_FOR_PICKUP').length,
      delivering: orders.filter((order) => order.status === 'DELIVERING').length,
      completed: orders.filter((order) => order.status === 'DELIVERED').length,
    }),
    [orders]
  );

  const filteredOrders = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();

    return orders.filter((order) => {
      if (activeTab === 'HISTORY') {
        if (order.status !== 'DELIVERED' && order.status !== 'CANCELLED') return false;
      } else if (activeTab !== 'ALL' && !TAB_STATUSES[activeTab].includes(order.status)) {
        return false;
      }

      if (!query) return true;
      return (
        order.orderCode.toLowerCase().includes(query) ||
        order.receiverName.toLowerCase().includes(query) ||
        order.receiverPhone.includes(query) ||
        order.shippingAddress.toLowerCase().includes(query)
      );
    });
  }, [orders, activeTab, searchQuery]);

  const elapsedMinutes = (createdAt: string) => {
    const diff = now - new Date(createdAt).getTime();
    return Number.isNaN(diff) ? 0 : Math.max(0, Math.floor(diff / 60000));
  };

  if (needsStore) {
    return (
      <EmptyState
        icon={<ClipboardList size={30} />}
        title="Chưa chọn cơ sở"
        description="Chọn cơ sở ở thanh bên để xem hàng đợi"
      />
    );
  }

  return (
    <>
      <PageHeader
        title="Hàng đợi đơn hàng"
        subtitle="Tiếp nhận đơn realtime, điều phối làm bánh và bàn giao cho tài xế"
        onRefresh={reload}
        isRefreshing={isRefreshing}
      />

      {errorMsg && (
        <div className="alert-banner alert-error page-alert" role="alert">
          <XCircle size={18} />
          <div>{errorMsg}</div>
        </div>
      )}

      <section className="squeue__stats">
        <StatCard icon={<ClipboardList size={18} />} value={metrics.pending} label="Đơn mới cần làm" />
        <StatCard icon={<ChefHat size={18} />} value={metrics.preparing} label="Bếp đang làm" />
        <StatCard icon={<PackageCheck size={18} />} value={metrics.ready} label="Chờ tài xế nhận" />
        <StatCard icon={<Bike size={18} />} value={metrics.delivering} label="Đang giao" />
        <StatCard icon={<Clock size={18} />} value={metrics.completed} label="Đã giao hôm nay" />
      </section>

      <section className="card">
        <div className="squeue__controls">
          <Tabs
            value={activeTab}
            onChange={setActiveTab}
            tabs={[
              { key: 'ALL', label: 'Tất cả', count: orders.length },
              { key: 'NEW', label: 'Cần làm ngay', count: metrics.pending },
              { key: 'PREPARING', label: 'Đang chế biến', count: metrics.preparing },
              { key: 'READY', label: 'Chờ tài xế', count: metrics.ready },
              { key: 'DELIVERING', label: 'Đang giao', count: metrics.delivering },
              { key: 'HISTORY', label: 'Lịch sử / huỷ' },
            ]}
          />

          <div className="squeue__search">
            <Input
              type="search"
              icon={<Search size={16} />}
              placeholder="Tìm mã đơn, tên khách, SĐT..."
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
      ) : filteredOrders.length === 0 ? (
        <section className="card">
          <div className="card__body squeue__empty">
            <EmptyState
              icon={<EmptyPlate size={120} />}
              title="Không có đơn nào trong danh sách"
              description="Bếp đang rảnh tay — đơn mới sẽ tự động hiện tại đây theo thời gian thực."
            />
          </div>
        </section>
      ) : (
        <div className="squeue__board">
          <div className="squeue__rail" aria-hidden="true" />
          <div className="squeue__list">
            {filteredOrders.map((order) => {
              const minutes = elapsedMinutes(order.createdAt);
              const urgent = minutes >= URGENT_AFTER_MINUTES && !isFinalStatus(order.status);
              const busy = busyOrderCode === order.orderCode;
              const waiting = !isFinalStatus(order.status);

              return (
                <article
                  key={order.id}
                  data-status={order.status}
                  className={`card squeue__card${urgent ? ' squeue__card--urgent' : ''}`}
                >
                  <header className="squeue__head">
                    <div className="squeue__ident">
                      <span className="squeue__code">{order.orderCode}</span>
                      <StatusBadge status={order.status} />
                      {waiting && (
                        <span className={`squeue__timer${urgent ? ' squeue__timer--urgent' : ''}`}>
                          <Clock size={14} />
                          Chờ {minutes} phút
                        </span>
                      )}
                    </div>

                    <div className="squeue__meta">
                      <span>{formatDateTime(order.createdAt)}</span>
                      <Badge tone={order.paymentStatus === 'PAID' ? 'success' : 'warning'}>
                        {order.paymentStatus === 'PAID' ? 'Đã thanh toán' : 'Thu khi giao'} ·{' '}
                        {PAYMENT_METHOD_LABEL[order.paymentMethod] ?? order.paymentMethod}
                      </Badge>
                    </div>
                  </header>

                  <div className="squeue__body">
                    <div className="squeue__info">
                      <p className="squeue__row">
                        <Phone size={15} />
                        <span>
                          <strong>{order.receiverName}</strong> · {order.receiverPhone}
                        </span>
                      </p>
                      <p className="squeue__row">
                        <MapPin size={15} />
                        <span>{order.shippingAddress}</span>
                      </p>
                      {order.note && (
                        <p className="squeue__row squeue__row--note">
                          <StickyNote size={15} />
                          <span>“{order.note}”</span>
                        </p>
                      )}
                      {order.shipperName && (
                        <p className="squeue__row">
                          <Bike size={15} />
                          <span>
                            Tài xế: <strong>{order.shipperName}</strong>
                            {order.shipperPhone ? ` · ${order.shipperPhone}` : ''}
                          </span>
                        </p>
                      )}
                      {order.cancelReason && (
                        <p className="squeue__row squeue__row--cancel">
                          <XCircle size={15} />
                          <span>Lý do huỷ: {order.cancelReason}</span>
                        </p>
                      )}
                    </div>

                    <div className="squeue__items">
                      {order.items.map((item) => (
                        <div key={item.id} className="squeue__item">
                          <span className="squeue__item-qty">{item.quantity}×</span>
                          <span className="squeue__item-body">
                            <strong>{item.productName}</strong>
                            {orderComponentsText(item) && <em>{orderComponentsText(item)}</em>}
                            {item.options && item.options.length > 0 && (
                              <em>
                                {item.options
                                  .map((option) => `+ ${option.optionName}`)
                                  .join(' · ')}
                              </em>
                            )}
                          </span>
                          <span className="squeue__item-price">{formatCurrency(item.lineTotal)}</span>
                        </div>
                      ))}

                      <dl className="squeue__cost">
                        <div>
                          <dt>Tạm tính</dt>
                          <dd>{formatCurrency(order.subtotal)}</dd>
                        </div>
                        <div>
                          <dt>Phí giao</dt>
                          <dd>{formatCurrency(order.shippingFee)}</dd>
                        </div>
                        {order.discountAmount > 0 && (
                          <div>
                            <dt>Giảm giá{order.promotionCode ? ` (${order.promotionCode})` : ''}</dt>
                            <dd>-{formatCurrency(order.discountAmount)}</dd>
                          </div>
                        )}
                        <div className="squeue__cost-total">
                          <dt>Tổng cộng</dt>
                          <dd>{formatCurrency(order.total)}</dd>
                        </div>
                      </dl>
                    </div>
                  </div>

                  <footer className="squeue__foot">
                    <p className="squeue__prompt">{promptFor(order)}</p>

                    <div className="squeue__actions">
                      {(order.status === 'PENDING' || order.status === 'CONFIRMED') && (
                        <Button
                          variant="primary"
                          icon={<ChefHat size={17} />}
                          loading={busy}
                          onClick={() => void handleStartPreparing(order)}
                        >
                          Nhận đơn & bắt đầu làm
                        </Button>
                      )}

                      {order.status === 'PREPARING' && (
                        <Button
                          variant="success"
                          icon={<PackageCheck size={17} />}
                          loading={busy}
                          onClick={() => void handleMarkReady(order)}
                        >
                          Bánh đã làm xong
                        </Button>
                      )}

                      {order.status === 'READY_FOR_PICKUP' && (
                        <Button
                          variant="primary"
                          icon={<Bike size={17} />}
                          onClick={() => setAssigningOrder(order)}
                        >
                          {order.shipperName ? 'Đổi tài xế khác' : 'Gán tài xế đang rảnh'}
                        </Button>
                      )}

                      {canTransfer && order.status === 'PENDING' && (
                        <Button
                          variant="ghost"
                          icon={<ArrowRightLeft size={17} />}
                          disabled={busy}
                          onClick={() => setTransferringOrder(order)}
                        >
                          Chuyển cơ sở
                        </Button>
                      )}

                      {order.paymentStatus === 'PAID' && (
                        <Button
                          variant="ghost"
                          icon={<RotateCcw size={17} />}
                          onClick={() => setRefundingOrder(order)}
                        >
                          Hoàn tiền
                        </Button>
                      )}

                      {!isFinalStatus(order.status) && (
                        <Button
                          variant="ghost"
                          disabled={busy}
                          onClick={() => setCancellingOrder(order)}
                        >
                          Huỷ đơn
                        </Button>
                      )}
                    </div>
                  </footer>
                </article>
              );
            })}
          </div>
        </div>
      )}

      {assigningOrder && (
        <AssignShipperModal
          order={assigningOrder}
          onClose={() => setAssigningOrder(null)}
          onSuccess={(updated) => {
            replaceOrder(updated);
            setAssigningOrder(null);
            toast.success(`Đã gán đơn ${updated.orderCode} cho tài xế`);
          }}
        />
      )}

      {cancellingOrder && (
        <CancelOrderModal
          order={cancellingOrder}
          onClose={() => setCancellingOrder(null)}
          onSuccess={(updated, reason) => {
            replaceOrder(updated);
            setCancellingOrder(null);
            toast.success(`Đã huỷ đơn ${updated.orderCode} — lý do: ${reason}`);
          }}
        />
      )}

      {transferringOrder && (
        <TransferStoreModal
          order={transferringOrder}
          onClose={() => setTransferringOrder(null)}
          onSuccess={(updated) => {
            setTransferringOrder(null);
            toast.success(`Đã chuyển đơn ${updated.orderCode} sang cơ sở khác`);
            void fetchOrders();
          }}
        />
      )}

      {refundingOrder && (
        <RefundOrderModal
          order={refundingOrder}
          onClose={() => setRefundingOrder(null)}
          onSuccess={(updated, reason) => {
            replaceOrder(updated);
            setRefundingOrder(null);
            toast.success(`Đã hoàn tiền đơn ${updated.orderCode} — lý do: ${reason}`);
          }}
        />
      )}
    </>
  );
};

const StatCard = ({ icon, value, label }: { icon: ReactNode; value: number; label: string }) => (
  <div className="card squeue__stat">
    <span className="squeue__stat-icon" aria-hidden="true">
      {icon}
    </span>
    <span>
      <strong>{value}</strong>
      <em>{label}</em>
    </span>
  </div>
);

const promptFor = (order: OrderResponse) => {
  switch (order.status) {
    case 'PENDING':
      return 'Đơn mới từ khách — bấm nhận đơn để bắt đầu làm bánh.';
    case 'CONFIRMED':
      return 'Đơn đã xác nhận — chuyển vào hàng đợi bếp khi bắt đầu làm.';
    case 'PREPARING':
      return 'Bếp đang làm bánh, bấm "Bánh đã làm xong" khi hoàn tất.';
    case 'READY_FOR_PICKUP':
      return order.shipperName
        ? `Đã gán cho tài xế ${order.shipperName}, đang chờ tài xế tới lấy.`
        : 'Bánh đã sẵn sàng — hãy gán cho một tài xế đang rảnh.';
    case 'DELIVERING':
      return `Tài xế ${order.shipperName ?? ''} đang giao đơn này.`;
    case 'DELIVERED':
      return `Đã giao thành công — ${ORDER_STATUS_LABEL[order.status]}.`;
    default:
      return '';
  }
};
