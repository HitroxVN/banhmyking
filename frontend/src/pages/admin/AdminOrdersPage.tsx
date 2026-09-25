import { useCallback, useEffect, useState } from 'react';
import { Bike, Package, XCircle } from 'lucide-react';
import {
  Badge,
  Button,
  EmptyState,
  Input,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
  useToast,
} from '../../components/ui';
import { AssignShipperModal } from '../../components/order/AssignShipperModal';
import { CancelOrderModal } from '../../components/order/CancelOrderModal';
import { staffOrderApi } from '../../api/staffOrderApi';
import type { OrderResponse, OrderStatus } from '../../types/order';
import { ORDER_NEXT_STATUSES, isFinalStatus } from '../../types/order';
import { ORDER_STATUS_LABEL, ORDER_STATUS_OPTIONS } from '../../utils/orderStatus';
import { PAYMENT_METHOD_LABEL, PAYMENT_STATUS_LABEL, PAYMENT_STATUS_TONE } from '../../utils/payment';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { broadcastOrderChange } from '../../utils/orderSyncChannel';
import '../../styles/components/admin-orders.css';

const PAGE_SIZE = 10;

export const AdminOrdersPage = () => {
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [status, setStatus] = useState<OrderStatus | 'ALL'>('ALL');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [busyOrderCode, setBusyOrderCode] = useState<string | null>(null);

  const [assigningOrder, setAssigningOrder] = useState<OrderResponse | null>(null);
  const [cancellingOrder, setCancellingOrder] = useState<OrderResponse | null>(null);

  const toast = useToast();

  const load = useCallback(
    async (manual = false) => {
      if (manual) setIsRefreshing(true);
      setErrorMsg(null);
      try {
        const data = await staffOrderApi.getOrders({
          status: status === 'ALL' ? undefined : status,
          fromDate: fromDate || undefined,
          toDate: toDate || undefined,
          page: page - 1,
          size: PAGE_SIZE,
        });
        setOrders(data.content ?? []);
        setTotalPages(data.totalPages || 1);
        setTotalElements(data.totalElements ?? 0);
      } catch (err: unknown) {
        setErrorMsg(err instanceof Error ? err.message : 'Không tải được danh sách đơn hàng.');
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [status, fromDate, toDate, page]
  );

  useEffect(() => {
    setIsLoading(true);
    void load();
  }, [load]);

  const reload = () => {
    setIsLoading(true);
    void load(true);
  };

  const handleStatusChange = async (order: OrderResponse, next: OrderStatus) => {
    setBusyOrderCode(order.orderCode);
    try {
      await staffOrderApi.updateOrderStatus(
        order.orderCode,
        next,
        `Quản trị viên chuyển trạng thái sang ${ORDER_STATUS_LABEL[next]}`
      );
      broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode: order.orderCode });
      toast.success(`Đơn ${order.orderCode} → ${ORDER_STATUS_LABEL[next]}`);
      await load();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Cập nhật trạng thái thất bại.');
    } finally {
      setBusyOrderCode(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Quản lý đơn hàng"
        subtitle={`${totalElements} đơn hàng trong hệ thống`}
        onRefresh={reload}
        isRefreshing={isRefreshing}
      />

      {errorMsg && (
        <div className="alert-banner alert-error page-alert" role="alert">
          <XCircle size={18} />
          <div>{errorMsg}</div>
        </div>
      )}

      <section className="card">
        <div className="aorders__filters">
          <Select
            label="Trạng thái"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as OrderStatus | 'ALL');
              setPage(1);
            }}
          >
            <option value="ALL">Tất cả trạng thái</option>
            {ORDER_STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>

          <Input
            label="Từ ngày"
            type="date"
            value={fromDate}
            onChange={(event) => {
              setFromDate(event.target.value);
              setPage(1);
            }}
          />

          <Input
            label="Đến ngày"
            type="date"
            value={toDate}
            onChange={(event) => {
              setToDate(event.target.value);
              setPage(1);
            }}
          />

          {(status !== 'ALL' || fromDate || toDate) && (
            <Button
              variant="ghost"
              onClick={() => {
                setStatus('ALL');
                setFromDate('');
                setToDate('');
                setPage(1);
              }}
            >
              Xoá lọc
            </Button>
          )}
        </div>
      </section>

      <section className="card">
        {isLoading ? (
          <div className="card__body">
            <Skeleton variant="row" count={6} />
          </div>
        ) : orders.length === 0 ? (
          <div className="card__body">
            <EmptyState
              icon={<Package size={30} />}
              title="Không có đơn hàng nào"
              description="Thử đổi bộ lọc trạng thái hoặc khoảng ngày để xem thêm."
            />
          </div>
        ) : (
          <div className="table-wrap">
            <table className="ui-table">
              <thead>
                <tr>
                  <th>Mã đơn</th>
                  <th>Khách hàng</th>
                  <th>Món</th>
                  <th>Tổng tiền</th>
                  <th>Thanh toán</th>
                  <th>Trạng thái</th>
                  <th>Tài xế</th>
                  <th aria-label="Hành động" />
                </tr>
              </thead>
              <tbody>
                {orders.map((order) => {
                  const nextStatuses = ORDER_NEXT_STATUSES[order.status] ?? [];
                  const canCancel = nextStatuses.includes('CANCELLED');
                  const busy = busyOrderCode === order.orderCode;

                  return (
                    <tr key={order.orderCode}>
                      <td>
                        <span className="ui-table__primary">{order.orderCode}</span>
                        <span className="ui-table__meta">{formatDateTime(order.createdAt)}</span>
                      </td>
                      <td>
                        {order.receiverName}
                        <span className="ui-table__meta">{order.receiverPhone}</span>
                      </td>
                      <td className="ui-table__clip">
                        {order.items.length} món
                        {order.items[0] && (
                          <span className="ui-table__meta">{order.items[0].productName}</span>
                        )}
                      </td>
                      <td className="ui-table__amount">{formatCurrency(order.total)}</td>
                      <td>
                        <span className="aorders__payer">
                          <span>{PAYMENT_METHOD_LABEL[order.paymentMethod] ?? order.paymentMethod}</span>
                          <Badge tone={order.paymentStatus ? PAYMENT_STATUS_TONE[order.paymentStatus] : 'neutral'}>
                            {order.paymentStatus
                              ? PAYMENT_STATUS_LABEL[order.paymentStatus]
                              : 'Chưa thanh toán'}
                          </Badge>
                        </span>
                      </td>
                      <td>
                        <StatusBadge status={order.status} />
                      </td>
                      <td>
                        {order.shipperName || <span className="ui-table__meta">Chưa gán</span>}
                      </td>
                      <td>
                        <div className="ui-table__actions">
                          {!isFinalStatus(order.status) && (
                            <Select
                              className="aorders__status-select"
                              aria-label={`Chuyển trạng thái đơn ${order.orderCode}`}
                              value=""
                              disabled={busy}
                              onChange={(event) => {
                                if (event.target.value) {
                                  void handleStatusChange(order, event.target.value as OrderStatus);
                                }
                              }}
                            >
                              <option value="">Chuyển trạng thái…</option>
                              {nextStatuses.map((next) => (
                                <option key={next} value={next}>
                                  {ORDER_STATUS_LABEL[next]}
                                </option>
                              ))}
                            </Select>
                          )}

                          {!isFinalStatus(order.status) && (
                            <Button
                              size="sm"
                              variant="secondary"
                              icon={<Bike size={15} />}
                              onClick={() => setAssigningOrder(order)}
                            >
                              {order.shipperId ? 'Đổi tài xế' : 'Gán tài xế'}
                            </Button>
                          )}

                          {canCancel && (
                            <Button
                              size="sm"
                              variant="ghost"
                              disabled={busy}
                              onClick={() => setCancellingOrder(order)}
                            >
                              Huỷ
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {!isLoading && orders.length > 0 && totalPages > 1 && (
          <div className="aorders__pager">
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </div>
        )}
      </section>

      {assigningOrder && (
        <AssignShipperModal
          order={assigningOrder}
          onClose={() => setAssigningOrder(null)}
          onSuccess={(updated) => {
            broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode: updated.orderCode });
            setAssigningOrder(null);
            toast.success(`Đã gán tài xế cho đơn ${updated.orderCode}`);
            void load();
          }}
        />
      )}

      {cancellingOrder && (
        <CancelOrderModal
          order={cancellingOrder}
          onClose={() => setCancellingOrder(null)}
          onSuccess={(updated, reason) => {
            setCancellingOrder(null);
            toast.success(`Đã huỷ đơn ${updated.orderCode} — lý do: ${reason}`);
            void load();
          }}
        />
      )}
    </>
  );
};
