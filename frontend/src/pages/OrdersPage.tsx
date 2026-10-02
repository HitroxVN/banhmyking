import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PackageSearch, Receipt, SearchX } from 'lucide-react';
import { orderApi } from '../api/orderApi';
import { Button, ChipGroup, EmptyState, Pagination, Skeleton, StatusBadge } from '../components/ui';
import { formatCurrency, formatDateTime } from '../utils/formatters';
import { usePolling } from '../hooks/usePolling';
import type { OrderResponse, OrderStatus } from '../types/order';
import { PriceTag } from '../components/product/PriceTag';
import '../styles/components/pricing.css';
import '../styles/components/orders.css';

const PAGE_SIZE = 10;
/** Danh sách nhiều đơn — làm mới thưa hơn trang chi tiết */
const LIST_POLL_MS = 15000;

type FilterKey = 'ALL' | 'PENDING' | 'KITCHEN' | 'DELIVERING' | 'DELIVERED' | 'CLOSED';

const FILTERS: { key: FilterKey; label: string }[] = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ xác nhận' },
  { key: 'KITCHEN', label: 'Đang chuẩn bị' },
  { key: 'DELIVERING', label: 'Đang giao' },
  { key: 'DELIVERED', label: 'Đã giao' },
  { key: 'CLOSED', label: 'Đã huỷ' },
];

const STATUS_OF_FILTER: Record<FilterKey, OrderStatus[] | null> = {
  ALL: null,
  PENDING: ['PENDING'],
  KITCHEN: ['CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP'],
  DELIVERING: ['DELIVERING'],
  DELIVERED: ['DELIVERED'],
  CLOSED: ['CANCELLED', 'FAILED'],
};

export const OrdersPage = () => {
  const navigate = useNavigate();
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [filter, setFilter] = useState<FilterKey>('ALL');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);

  // Lọc + phân trang do server làm. Trước đây FE nạp 50 đơn rồi tự lọc, nên khách quá 50 đơn
  // là mất đơn cũ và tổng số trang đếm sai.
  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    orderApi
      .getUserOrders(page - 1, PAGE_SIZE, STATUS_OF_FILTER[filter] ?? undefined)
      .then((result) => {
        if (cancelled) return;
        setOrders(result.content ?? []);
        setTotalPages(Math.max(1, result.totalPages ?? 1));

        // Đang ở trang vượt quá số trang thật (đơn bị huỷ/xoá ở lần tải trước) → lùi về trang cuối
        if ((result.totalPages ?? 0) > 0 && page > result.totalPages) {
          setPage(result.totalPages);
        }
      })
      .catch(() => {
        if (!cancelled) setError('Không tải được danh sách đơn hàng. Vui lòng thử lại.');
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [reloadKey, filter, page]);

  // Tự làm mới khi staff/shipper đổi trạng thái. Kết quả của bộ lọc/trang cũ (người dùng vừa bấm đổi
  // giữa chừng) bị bỏ qua để không ghi đè danh sách đang xem.
  const queryKey = `${filter}|${page}`;
  const queryKeyRef = useRef(queryKey);
  useEffect(() => {
    queryKeyRef.current = queryKey;
  }, [queryKey]);
  usePolling(
    async () => {
      const requestedKey = queryKey;
      try {
        const result = await orderApi.getUserOrders(page - 1, PAGE_SIZE, STATUS_OF_FILTER[filter] ?? undefined);
        if (queryKeyRef.current !== requestedKey) return;
        setOrders(result.content ?? []);
        setTotalPages(Math.max(1, result.totalPages ?? 1));
      } catch {
        // Lỗi tải ngầm: giữ danh sách đang hiện, lượt sau thử lại
      }
    },
    { intervalMs: LIST_POLL_MS, enabled: !isLoading && !error }
  );

  const changeFilter = (next: FilterKey) => {
    setFilter(next);
    setPage(1);
  };

  return (
    <div>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">Thực đơn / Đơn hàng</p>
          <h1 className="page-bar__title">Đơn hàng của tôi</h1>
        </div>
        <Button variant="secondary" icon={<Receipt size={17} />} onClick={() => navigate('/menu')}>
          Đặt món mới
        </Button>
      </div>

      <ChipGroup
        ariaLabel="Lọc theo trạng thái đơn"
        value={filter}
        onChange={changeFilter}
        options={FILTERS.map((item) => ({ value: item.key, label: item.label }))}
      />

      {isLoading && (
        <div className="orders__list">
          <Skeleton variant="row" count={4} />
        </div>
      )}

      {!isLoading && error && (
        <EmptyState
          icon={<SearchX size={30} />}
          title="Chưa tải được đơn hàng"
          description={error}
          action={<Button onClick={() => setReloadKey((key) => key + 1)}>Thử lại</Button>}
        />
      )}

      {!isLoading && !error && orders.length === 0 && (
        <EmptyState
          icon={<PackageSearch size={30} />}
          title={filter === 'ALL' ? 'Bạn chưa có đơn hàng nào' : 'Không có đơn ở trạng thái này'}
          description={
            filter === 'ALL'
              ? 'Chọn vài món trong thực đơn, đơn đầu tiên của bạn sẽ xuất hiện ở đây.'
              : 'Thử chọn trạng thái khác để xem các đơn còn lại.'
          }
          action={
            filter === 'ALL' ? (
              <Button onClick={() => navigate('/menu')}>Xem thực đơn</Button>
            ) : (
              <Button variant="secondary" onClick={() => changeFilter('ALL')}>
                Xem tất cả
              </Button>
            )
          }
        />
      )}

      {!isLoading && !error && orders.length > 0 && (
        <>
          <div className="orders__list">
            {orders.map((order) => (
              <article className="ord-card" key={order.id}>
                <div className="ord-card__main">
                  <div className="ord-card__top">
                    <span className="ord-card__code">{order.orderCode}</span>
                    <StatusBadge status={order.status} />
                  </div>
                  <p className="ord-card__meta">
                    {formatDateTime(order.createdAt)} · {order.items.length} món
                  </p>
                  <p className="ord-card__items">
                    {order.items
                      .map((item) => {
                        const parts = (item.components ?? []).map((c) => `${c.quantity}× ${c.productName}`);
                        return `${item.quantity}× ${item.productName}${parts.length > 0 ? ` (${parts.join(', ')})` : ''}`;
                      })
                      .join(' · ')}
                  </p>
                </div>

                <div className="ord-card__side">
                  <PriceTag className="ord-card__total" price={order.total} />
                  {(order.savingsAmount ?? 0) > 0 && order.status !== 'CANCELLED' && order.status !== 'FAILED' && (
                    <span className="ord-card__saving">Tiết kiệm {formatCurrency(order.savingsAmount)}</span>
                  )}
                  <Button size="sm" variant="secondary" onClick={() => navigate(`/orders/${order.orderCode}`)}>
                    Chi tiết
                  </Button>
                </div>
              </article>
            ))}
          </div>

          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
};
