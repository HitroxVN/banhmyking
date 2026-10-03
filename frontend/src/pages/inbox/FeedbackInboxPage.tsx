import { useEffect, useState } from 'react';
import { Eye, MessageSquare } from 'lucide-react';
import { feedbackApi } from '../../api/feedbackApi';
import { orderApi } from '../../api/orderApi';
import { useAuth } from '../../context/useAuth';
import {
  Badge,
  Button,
  ChipGroup,
  EmptyState,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  Textarea,
  useToast,
} from '../../components/ui';
import { StoreScopeSelect } from '../../components/store/StoreScopeSelect';
import { FEEDBACK_STATUS_LABEL, FEEDBACK_STATUS_TONE, FEEDBACK_TYPE_LABEL } from '../../utils/contentLabels';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { notifyInboxChanged } from '../../utils/inboxEvents';
import { ORDER_STATUS_LABEL } from '../../utils/orderStatus';
import type { Feedback, FeedbackStatus, FeedbackType } from '../../types/content';
import type { OrderResponse } from '../../types/order';
import '../../styles/components/table.css';
import '../../styles/components/content.css';

const PAGE_SIZE = 20;
const TYPES: FeedbackType[] = ['SUGGESTION', 'COMPLAINT', 'PARTNERSHIP', 'OTHER'];
const STATUSES: FeedbackStatus[] = ['NEW', 'IN_PROGRESS', 'RESOLVED'];

type StatusFilter = FeedbackStatus | 'ALL';

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  ...STATUSES.map((status) => ({ value: status, label: FEEDBACK_STATUS_LABEL[status] })),
];

interface OrderPeek {
  code: string;
  order: OrderResponse | null;
  error: string | null;
}

/** Hộp phản hồi — ADMIN mọi cơ sở (kể cả chung toàn chuỗi), MANAGER cơ sở mình (spec D §5). */
export const FeedbackInboxPage = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const toast = useToast();
  const [type, setType] = useState<FeedbackType | null>(null);
  const [storeId, setStoreId] = useState<number | null>(null);
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<Feedback[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [selected, setSelected] = useState<Feedback | null>(null);
  const [draftStatus, setDraftStatus] = useState<FeedbackStatus>('NEW');
  const [draftNote, setDraftNote] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [orderPeek, setOrderPeek] = useState<OrderPeek | null>(null);

  // Tải trong effect bằng promise (không gọi setState đồng bộ); reload() tăng reloadKey để tải lại sau khi lưu/xoá
  const requestKey = `${type}|${storeId}|${status}|${page}|${reloadKey}`;
  const isLoading = loadedKey === null;
  // Đang tải lại do đổi bộ lọc/trang/lưu: giữ dòng cũ nhưng làm mờ
  const isFetching = loadedKey !== requestKey;

  useEffect(() => {
    let alive = true;
    feedbackApi
      .adminList({
        type: type ?? undefined,
        storeId: isAdmin ? storeId ?? undefined : undefined,
        status: status === 'ALL' ? undefined : status,
        page: page - 1,
        size: PAGE_SIZE,
      })
      .then((data) => {
        if (!alive) return;
        // Trang hiện tại vừa hết dòng (xử lý mục cuối) thì lùi một trang rồi tải lại
        if (data.content.length === 0 && page > 1) {
          setPage(Math.max(1, Math.min(page - 1, data.totalPages)));
          return;
        }
        setItems(data.content);
        setTotalPages(data.totalPages);
        // Mục đang xem không còn trong danh sách đã lọc thì đóng ngăn chi tiết
        setSelected((prev) => (prev && data.content.some((item) => item.id === prev.id) ? prev : null));
        setLoadError(null);
        setLoadedKey(requestKey);
      })
      .catch((err) => {
        if (!alive) return;
        const message = err instanceof Error ? err.message : 'Không tải được phản hồi';
        toast.error(message);
        setLoadError(message);
        setLoadedKey(requestKey);
      });
    return () => {
      alive = false;
    };
  }, [type, storeId, status, page, isAdmin, reloadKey, requestKey, toast]);

  const reload = () => setReloadKey((key) => key + 1);

  const select = (feedback: Feedback) => {
    setSelected(feedback);
    setDraftStatus(feedback.status);
    setDraftNote(feedback.resolutionNote ?? '');
    setOrderPeek(null);
  };

  const peekOrder = async (code: string) => {
    try {
      setOrderPeek({ code, order: await orderApi.getOrderByCode(code), error: null });
    } catch (err) {
      setOrderPeek({ code, order: null, error: err instanceof Error ? err.message : 'Không tải được đơn hàng' });
    }
  };

  const handleSave = async () => {
    if (!selected) return;
    setIsSaving(true);
    try {
      const updated = await feedbackApi.adminUpdate(selected.id, { status: draftStatus, resolutionNote: draftNote });
      toast.success('Đã cập nhật phản hồi');
      select(updated);
      notifyInboxChanged();
      reload();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Cập nhật phản hồi thất bại');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Phản hồi"
        subtitle={isAdmin ? 'Phản hồi của khách ở mọi cơ sở và phản hồi chung toàn chuỗi.' : 'Phản hồi của khách về cơ sở của bạn.'}
      />

      <div className="inbox-filters">
        <Select
          label="Loại"
          value={type ?? ''}
          onChange={(event) => {
            setType(event.target.value ? (event.target.value as FeedbackType) : null);
            setPage(1);
          }}
        >
          <option value="">Tất cả loại</option>
          {TYPES.map((value) => (
            <option key={value} value={value}>
              {FEEDBACK_TYPE_LABEL[value]}
            </option>
          ))}
        </Select>
        {isAdmin && (
          <StoreScopeSelect
            value={storeId}
            onChange={(id) => {
              setStoreId(id);
              setPage(1);
            }}
          />
        )}
        <ChipGroup<StatusFilter>
          options={STATUS_FILTERS}
          value={status}
          onChange={(value) => {
            setStatus(value);
            setPage(1);
          }}
          ariaLabel="Lọc trạng thái phản hồi"
        />
      </div>

      {isLoading ? (
        <Skeleton variant="row" count={4} />
      ) : loadError ? (
        <EmptyState
          icon={<MessageSquare size={30} />}
          title="Không tải được phản hồi"
          description={loadError}
          action={<Button onClick={reload}>Thử lại</Button>}
        />
      ) : items.length === 0 ? (
        <EmptyState icon={<MessageSquare size={30} />} title="Chưa có phản hồi" description="Phản hồi khách gửi từ trang Liên hệ sẽ hiện ở đây." />
      ) : (
        <div className={`inbox${selected ? '' : ' inbox--single'}`}>
          <section className="card" aria-busy={isFetching} style={isFetching ? { opacity: 0.55, transition: 'opacity .15s' } : undefined}>
            <div className="table-wrap">
              <table className="ui-table">
                <thead>
                  <tr>
                    <th>Tiêu đề</th>
                    <th>Loại</th>
                    <th>Cơ sở</th>
                    <th>Ngày gửi</th>
                    <th>Trạng thái</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((feedback) => (
                    <tr
                      key={feedback.id}
                      className={`inbox__row${selected?.id === feedback.id ? ' inbox__row--active' : ''}`}
                      tabIndex={0}
                      onClick={() => select(feedback)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') select(feedback);
                      }}
                    >
                      <td>
                        <span className="ui-table__primary">{feedback.subject}</span>
                        <span className="ui-table__meta">
                          {feedback.fullName}
                          {feedback.orderCode ? ` · đơn ${feedback.orderCode}` : ''}
                        </span>
                      </td>
                      <td>{FEEDBACK_TYPE_LABEL[feedback.type]}</td>
                      <td>{feedback.storeName ?? 'Toàn chuỗi'}</td>
                      <td>{formatDateTime(feedback.createdAt)}</td>
                      <td>
                        <Badge tone={FEEDBACK_STATUS_TONE[feedback.status]}>{FEEDBACK_STATUS_LABEL[feedback.status]}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </section>

          {selected && (
            <aside className="card inbox__detail">
              <div className="card__body cf-form">
                <h3 className="card__title">{selected.subject}</h3>
                <dl>
                  <dt>Loại</dt>
                  <dd>{FEEDBACK_TYPE_LABEL[selected.type]}</dd>
                  <dt>Người gửi</dt>
                  <dd>
                    {selected.fullName}
                    {selected.userId ? ' (có tài khoản)' : ''}
                  </dd>
                  <dt>Điện thoại</dt>
                  <dd>{selected.phone ? <a href={`tel:${selected.phone}`}>{selected.phone}</a> : '—'}</dd>
                  <dt>Email</dt>
                  <dd>{selected.email ? <a href={`mailto:${selected.email}`}>{selected.email}</a> : '—'}</dd>
                  <dt>Cơ sở</dt>
                  <dd>{selected.storeName ?? 'Chung toàn chuỗi'}</dd>
                  <dt>Đơn hàng</dt>
                  <dd>
                    {selected.orderCode ? (
                      <>
                        {selected.orderCode}{' '}
                        <Button size="sm" variant="ghost" icon={<Eye size={14} />} onClick={() => void peekOrder(selected.orderCode as string)}>
                          Xem đơn
                        </Button>
                      </>
                    ) : (
                      '—'
                    )}
                  </dd>
                  <dt>Ngày gửi</dt>
                  <dd>{formatDateTime(selected.createdAt)}</dd>
                  <dt>Người xử lý</dt>
                  <dd>
                    {selected.handledByName
                      ? `${selected.handledByName} · ${formatDateTime(selected.handledAt)}`
                      : 'Chưa xử lý'}
                  </dd>
                </dl>

                {orderPeek && orderPeek.code === selected.orderCode && (
                  <div className="order-peek">
                    {orderPeek.order ? (
                      <>
                        <strong>
                          {orderPeek.order.orderCode} · {ORDER_STATUS_LABEL[orderPeek.order.status]}
                        </strong>
                        <span>
                          {orderPeek.order.storeName ?? '—'} · {formatDateTime(orderPeek.order.createdAt)} ·{' '}
                          {formatCurrency(orderPeek.order.total)}
                        </span>
                        <span>
                          {orderPeek.order.items.map((item) => `${item.productName} ×${item.quantity}`).join(', ')}
                        </span>
                        <span>
                          Giao tới: {orderPeek.order.receiverName} · {orderPeek.order.receiverPhone}
                        </span>
                      </>
                    ) : (
                      <span>{orderPeek.error}</span>
                    )}
                  </div>
                )}

                <div className="inbox__message">{selected.content}</div>
                <Select label="Trạng thái" value={draftStatus} onChange={(event) => setDraftStatus(event.target.value as FeedbackStatus)}>
                  {STATUSES.map((value) => (
                    <option key={value} value={value}>
                      {FEEDBACK_STATUS_LABEL[value]}
                    </option>
                  ))}
                </Select>
                <Textarea label="Ghi chú xử lý" rows={4} maxLength={2000} value={draftNote} onChange={(event) => setDraftNote(event.target.value)} />
                <div className="inbox__actions">
                  <Button loading={isSaving} onClick={() => void handleSave()}>
                    Lưu
                  </Button>
                  <Button variant="ghost" onClick={() => setSelected(null)}>
                    Đóng
                  </Button>
                </div>
              </div>
            </aside>
          )}
        </div>
      )}
    </>
  );
};
