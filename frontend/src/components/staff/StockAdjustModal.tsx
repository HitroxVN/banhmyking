import { useEffect, useState } from 'react';
import { XCircle } from 'lucide-react';
import { Button, Input, Modal, Select, Spinner, useToast } from '../ui';
import { storeInventoryApi } from '../../api/storeInventoryApi';
import type { StockMovement, StockMovementReason } from '../../types/staff';
import type { StoreStockItem } from '../../types/store';
import { formatDateTime } from '../../utils/formatters';
import '../../styles/components/staff-menu.css';

const MOVEMENT_LABEL: Record<StockMovementReason, string> = {
  IMPORT: 'Nhập kho',
  ORDER: 'Trừ theo đơn',
  RESTORE: 'Hoàn khi huỷ đơn',
  ADJUST: 'Điều chỉnh',
};

interface StockAdjustModalProps {
  storeId: number;
  item: StoreStockItem;
  onClose: () => void;
  onAdjusted: (updated: StoreStockItem) => void;
}

/** Nhập/điều chỉnh tồn kho một món tại cơ sở, kèm sổ kho gần đây để đối chiếu. */
const StockAdjustModal = ({ storeId, item, onClose, onAdjusted }: StockAdjustModalProps) => {
  const [mode, setMode] = useState<'in' | 'out'>('in');
  const [qty, setQty] = useState(1);
  const [note, setNote] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [movements, setMovements] = useState<StockMovement[]>([]);
  const [isLoadingLog, setIsLoadingLog] = useState(true);
  const toast = useToast();

  useEffect(() => {
    let cancelled = false;
    storeInventoryApi
      .movements(storeId, item.productId)
      .then((list) => {
        if (!cancelled) setMovements(list);
      })
      .catch(() => {
        // Sổ kho hỏng thì vẫn cho nhập hàng, chỉ thiếu phần đối chiếu
      })
      .finally(() => {
        if (!cancelled) setIsLoadingLog(false);
      });
    return () => {
      cancelled = true;
    };
  }, [storeId, item.productId]);

  const current = item.stockQuantity ?? null;
  const delta = mode === 'in' ? qty : -qty;
  const after = (current ?? 0) + delta;

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      const updated = await storeInventoryApi.adjustStock(storeId, item.productId, delta, note.trim() || undefined);
      onAdjusted(updated);
      toast.success(`${updated.productName}: tồn kho còn ${updated.stockQuantity}`);
      onClose();
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Cập nhật tồn kho thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      title={`Nhập kho — ${item.productName}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button variant="primary" loading={isSubmitting} disabled={qty < 1} onClick={() => void handleSubmit()}>
            Xác nhận
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

      <div className="smenu__form">
        <p className="stockadj__now">
          {current === null ? 'Món này chưa quản tồn — lần nhập này sẽ bắt đầu quản.' : `Tồn hiện tại: ${current}`}
        </p>

        <Select
          label="Loại thay đổi"
          value={mode}
          onChange={(event) => setMode(event.target.value as 'in' | 'out')}
        >
          <option value="in">Nhập thêm</option>
          {/* Chưa quản tồn thì chưa có gì để giảm — backend cũng chặn */}
          {current !== null && <option value="out">Giảm bớt</option>}
        </Select>

        <Input
          label="Số lượng"
          type="number"
          required
          min={1}
          value={qty}
          onChange={(event) => setQty(Number(event.target.value))}
        />

        <Input
          label="Ghi chú"
          placeholder="Ví dụ: nhập buổi sáng, hao hụt kiểm kê..."
          value={note}
          onChange={(event) => setNote(event.target.value)}
        />

        <p className="stockadj__after">
          Tồn sau khi lưu: <strong>{after}</strong>
        </p>

        <div className="stockadj__log">
          <span className="stockadj__log-title">Sổ kho gần đây</span>
          {isLoadingLog ? (
            <Spinner size={18} />
          ) : movements.length === 0 ? (
            <p className="stockadj__log-empty">Chưa có thay đổi nào.</p>
          ) : (
            <ul className="stockadj__log-list">
              {movements.map((movement) => (
                <li key={movement.id} className="stockadj__log-item">
                  <span className={`stockadj__delta${movement.changeQty < 0 ? ' stockadj__delta--out' : ''}`}>
                    {movement.changeQty > 0 ? `+${movement.changeQty}` : movement.changeQty}
                  </span>
                  <span className="stockadj__log-main">
                    {MOVEMENT_LABEL[movement.reason]}
                    {movement.orderCode ? ` · ${movement.orderCode}` : ''}
                    {movement.note ? ` · ${movement.note}` : ''}
                  </span>
                  <time className="stockadj__log-time">{formatDateTime(movement.createdAt)}</time>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </Modal>
  );
};

export { StockAdjustModal };
