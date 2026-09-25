import { useCallback, useEffect, useState } from 'react';
import { Pencil, Plus, Ticket, Trash2, XCircle } from 'lucide-react';
import {
  Badge,
  Button,
  EmptyState,
  Input,
  Modal,
  PageHeader,
  Select,
  Skeleton,
  Textarea,
  useConfirm,
  useToast,
} from '../../components/ui';
import { promotionApi } from '../../api/promotionApi';
import type { DiscountType, PromotionPayload, PromotionResponse } from '../../types/promotion';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import '../../styles/components/admin-promotions.css';

const DISCOUNT_TYPE_LABEL: Record<DiscountType, string> = {
  PERCENTAGE: 'Giảm theo %',
  FIXED_AMOUNT: 'Giảm số tiền',
  FREE_SHIP: 'Miễn phí giao hàng',
};

type StateTone = 'success' | 'warning' | 'danger' | 'neutral';

/** Trạng thái suy ra từ cờ active + thời gian + lượt dùng (backend kiểm tra lại khi khách áp mã). */
const getState = (promo: PromotionResponse): { label: string; tone: StateTone } => {
  const now = Date.now();
  if (!promo.active) return { label: 'Đã tắt', tone: 'neutral' };
  if (promo.startsAt && new Date(promo.startsAt).getTime() > now) return { label: 'Chưa tới ngày', tone: 'warning' };
  if (promo.endsAt && new Date(promo.endsAt).getTime() < now) return { label: 'Hết hạn', tone: 'danger' };
  if (promo.maxUsage != null && promo.maxUsage > 0 && (promo.usedCount ?? 0) >= promo.maxUsage) {
    return { label: 'Hết lượt', tone: 'danger' };
  }
  return { label: 'Đang chạy', tone: 'success' };
};

const describeValue = (promo: PromotionResponse): string => {
  if (promo.discountType === 'PERCENTAGE') {
    return promo.maxDiscountAmount ? `${promo.value}% · tối đa ${formatCurrency(promo.maxDiscountAmount)}` : `${promo.value}%`;
  }
  if (promo.discountType === 'FREE_SHIP') {
    return `Tối đa ${formatCurrency(promo.value)}`;
  }
  return formatCurrency(promo.value);
};

/** `<input type="datetime-local">` chỉ nhận `YYYY-MM-DDTHH:mm` theo giờ địa phương */
const toInputValue = (iso?: string) => (iso ? iso.slice(0, 16) : '');
const toLocalInput = (date: Date) => new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);

interface PromotionFormModalProps {
  /** null = thêm mới */
  promotion: PromotionResponse | null;
  onClose: () => void;
  onSaved: () => void;
}

const PromotionFormModal = ({ promotion, onClose, onSaved }: PromotionFormModalProps) => {
  const [code, setCode] = useState(promotion?.code ?? '');
  const [description, setDescription] = useState(promotion?.description ?? '');
  const [discountType, setDiscountType] = useState<DiscountType>(promotion?.discountType ?? 'PERCENTAGE');
  const [value, setValue] = useState(String(promotion?.value ?? ''));
  const [maxDiscountAmount, setMaxDiscountAmount] = useState(String(promotion?.maxDiscountAmount ?? ''));
  const [minOrderAmount, setMinOrderAmount] = useState(String(promotion?.minOrderAmount ?? 0));
  const [maxUsage, setMaxUsage] = useState(String(promotion?.maxUsage ?? 50));
  // Mặc định sẵn 1 khoảng hợp lệ để không bị chặn ở bước "chọn thời gian"
  const [startsAt, setStartsAt] = useState(promotion?.startsAt ? toInputValue(promotion.startsAt) : toLocalInput(new Date()));
  const [endsAt, setEndsAt] = useState(
    promotion?.endsAt ? toInputValue(promotion.endsAt) : toLocalInput(new Date(Date.now() + 30 * 86400000))
  );
  const [active, setActive] = useState(promotion?.active ?? true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const toast = useToast();
  const isPercentage = discountType === 'PERCENTAGE';

  const validate = (): string | null => {
    if (!code.trim()) return 'Vui lòng nhập mã khuyến mãi.';
    if (!description.trim()) return 'Vui lòng nhập mô tả — khách sẽ thấy dòng này khi áp mã.';
    if (!(Number(value) > 0)) return 'Giá trị giảm phải lớn hơn 0.';
    if (isPercentage && Number(value) > 100) return 'Giảm theo % tối đa là 100.';
    if (isPercentage && !(Number(maxDiscountAmount) > 0)) return 'Mã giảm theo % phải có số tiền giảm tối đa.';
    if (!startsAt || !endsAt) return 'Vui lòng chọn thời gian bắt đầu và kết thúc.';
    if (new Date(endsAt).getTime() <= new Date(startsAt).getTime()) return 'Thời gian kết thúc phải sau thời gian bắt đầu.';
    if (!(Number(maxUsage) >= 1)) return 'Số lượt sử dụng tối đa phải từ 1 trở lên.';
    return null;
  };

  const handleSubmit = async () => {
    const invalid = validate();
    setErrorMsg(invalid);
    if (invalid) return;

    const payload: PromotionPayload = {
      code: code.trim().toUpperCase(),
      description: description.trim(),
      discountType,
      value: Number(value),
      maxDiscountAmount: maxDiscountAmount ? Number(maxDiscountAmount) : undefined,
      minOrderAmount: Number(minOrderAmount) || 0,
      startsAt,
      endsAt,
      maxUsage: Number(maxUsage),
      active,
    };

    setIsSubmitting(true);
    try {
      if (promotion) {
        await promotionApi.updatePromotion(promotion.id, payload);
        toast.success(`Đã cập nhật mã ${payload.code}`);
      } else {
        await promotionApi.createPromotion(payload);
        toast.success(`Đã tạo mã ${payload.code}`);
      }
      onSaved();
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Lưu mã giảm giá thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="md"
      title={promotion ? `Sửa mã — ${promotion.code}` : 'Thêm mã giảm giá'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button variant="primary" loading={isSubmitting} onClick={handleSubmit}>
            {promotion ? 'Lưu thay đổi' : 'Tạo mã'}
          </Button>
        </>
      }
    >
      {errorMsg && (
        <div className="alert-banner alert-error" role="alert">
          <XCircle size={17} />
          <div>{errorMsg}</div>
        </div>
      )}

      <div className="apromo__form">
        <div className="apromo__row">
          <Input
            label="Mã khuyến mãi"
            required
            placeholder="Ví dụ: BANHMYKING10"
            hint="Khách nhập mã này ở bước thanh toán (không phân biệt hoa/thường)"
            value={code}
            onChange={(event) => setCode(event.target.value.toUpperCase())}
          />

          <Select
            label="Loại giảm giá"
            value={discountType}
            onChange={(event) => setDiscountType(event.target.value as DiscountType)}
          >
            <option value="PERCENTAGE">Giảm theo %</option>
            <option value="FIXED_AMOUNT">Giảm số tiền</option>
            <option value="FREE_SHIP">Miễn phí giao hàng</option>
          </Select>
        </div>

        <Textarea
          label="Mô tả"
          required
          rows={2}
          placeholder="Ví dụ: Giảm 10% tối đa 20.000đ cho đơn từ 50.000đ"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />

        <div className="apromo__row">
          <Input
            label={isPercentage ? 'Giá trị giảm (%)' : 'Giá trị giảm (đ)'}
            required
            type="number"
            min={0}
            hint={
              isPercentage
                ? 'Theo % đơn hàng, bị chặn bởi "giảm tối đa"'
                : discountType === 'FREE_SHIP'
                  ? 'Số tiền giảm bị chặn ở đúng phí giao hàng của đơn'
                  : 'Số tiền giảm cố định'
            }
            value={value}
            onChange={(event) => setValue(event.target.value)}
          />

          {isPercentage && (
            <Input
              label="Giảm tối đa (đ)"
              required
              type="number"
              min={0}
              hint="Trần số tiền giảm cho 1 đơn"
              value={maxDiscountAmount}
              onChange={(event) => setMaxDiscountAmount(event.target.value)}
            />
          )}
        </div>

        <div className="apromo__row">
          <Input
            label="Đơn tối thiểu (đ)"
            type="number"
            min={0}
            hint="0 = không yêu cầu"
            value={minOrderAmount}
            onChange={(event) => setMinOrderAmount(event.target.value)}
          />

          <Input
            label="Lượt sử dụng tối đa"
            required
            type="number"
            min={1}
            hint="Tổng số lượt cho tất cả khách"
            value={maxUsage}
            onChange={(event) => setMaxUsage(event.target.value)}
          />
        </div>

        <div className="apromo__row">
          <Input
            label="Bắt đầu"
            required
            type="datetime-local"
            value={startsAt}
            onChange={(event) => setStartsAt(event.target.value)}
          />

          <Input
            label="Kết thúc"
            required
            type="datetime-local"
            value={endsAt}
            onChange={(event) => setEndsAt(event.target.value)}
          />
        </div>

        <div className="apromo__row">
          <Select
            label="Trạng thái"
            hint="Mã đã tắt vẫn giữ nguyên lượt đã dùng"
            value={active ? 'ON' : 'OFF'}
            onChange={(event) => setActive(event.target.value === 'ON')}
          >
            <option value="ON">Đang bật — khách áp được</option>
            <option value="OFF">Đã tắt — tạm ẩn mã</option>
          </Select>
        </div>
      </div>
    </Modal>
  );
};

export const AdminPromotionsPage = () => {
  const [promotions, setPromotions] = useState<PromotionResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editingPromotion, setEditingPromotion] = useState<PromotionResponse | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const confirm = useConfirm();
  const toast = useToast();

  const load = useCallback(async () => {
    setIsLoading(true);
    setErrorMsg(null);
    try {
      setPromotions(await promotionApi.getAllPromotions());
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Không tải được danh sách mã giảm giá.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleDelete = async (promotion: PromotionResponse) => {
    const ok = await confirm({
      title: 'Xoá mã giảm giá',
      message: `Xoá mã "${promotion.code}"? Khách đang nhập mã này sẽ không áp được nữa.`,
      confirmText: 'Xoá mã',
      danger: true,
    });
    if (!ok) return;

    setDeletingId(promotion.id);
    try {
      await promotionApi.deletePromotion(promotion.id);
      toast.success(`Đã xoá mã ${promotion.code}`);
      await load();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Xoá mã giảm giá thất bại.');
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Mã giảm giá"
        subtitle={`${promotions.length} mã trong hệ thống · số tiền giảm do backend tính khi khách áp mã`}
        actions={
          <Button
            variant="primary"
            icon={<Plus size={17} />}
            onClick={() => {
              setEditingPromotion(null);
              setFormOpen(true);
            }}
          >
            Thêm mã
          </Button>
        }
      />

      {errorMsg && (
        <div className="alert-banner alert-error page-alert" role="alert">
          <XCircle size={18} />
          <div>{errorMsg}</div>
        </div>
      )}

      <section className="card">
        {isLoading ? (
          <div className="card__body">
            <Skeleton variant="row" count={5} />
          </div>
        ) : promotions.length === 0 ? (
          <div className="card__body">
            <EmptyState
              icon={<Ticket size={30} />}
              title="Chưa có mã giảm giá nào"
              description="Tạo mã đầu tiên để khách áp được ở bước thanh toán."
            />
          </div>
        ) : (
          <div className="table-wrap">
            <table className="ui-table">
              <thead>
                <tr>
                  <th>Mã</th>
                  <th>Giảm</th>
                  <th>Đơn tối thiểu</th>
                  <th>Hiệu lực</th>
                  <th>Lượt dùng</th>
                  <th>Trạng thái</th>
                  <th aria-label="Hành động" />
                </tr>
              </thead>
              <tbody>
                {promotions.map((promotion) => {
                  const state = getState(promotion);
                  return (
                    <tr key={promotion.id}>
                      <td>
                        <span className="ui-table__primary">{promotion.code}</span>
                        <span className="ui-table__meta ui-table__clip">{promotion.description}</span>
                      </td>
                      <td>
                        <span className="apromo__value">{describeValue(promotion)}</span>
                        <span className="ui-table__meta">{DISCOUNT_TYPE_LABEL[promotion.discountType]}</span>
                      </td>
                      <td className="ui-table__amount">
                        {promotion.minOrderAmount ? formatCurrency(promotion.minOrderAmount) : 'Không yêu cầu'}
                      </td>
                      <td>
                        <span className="apromo__dates">
                          {formatDateTime(promotion.startsAt)} → {formatDateTime(promotion.endsAt)}
                        </span>
                        <span className="ui-table__meta">Thời gian áp dụng</span>
                      </td>
                      <td className="ui-table__amount">
                        {promotion.usedCount ?? 0}/{promotion.maxUsage ?? '∞'}
                      </td>
                      <td>
                        <Badge tone={state.tone}>{state.label}</Badge>
                      </td>
                      <td>
                        <div className="ui-table__actions">
                          <Button
                            size="sm"
                            variant="secondary"
                            icon={<Pencil size={15} />}
                            onClick={() => {
                              setEditingPromotion(promotion);
                              setFormOpen(true);
                            }}
                          >
                            Sửa
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            icon={<Trash2 size={15} />}
                            loading={deletingId === promotion.id}
                            onClick={() => void handleDelete(promotion)}
                          >
                            Xoá
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {formOpen && (
        <PromotionFormModal
          promotion={editingPromotion}
          onClose={() => setFormOpen(false)}
          onSaved={() => {
            setFormOpen(false);
            void load();
          }}
        />
      )}
    </>
  );
};
