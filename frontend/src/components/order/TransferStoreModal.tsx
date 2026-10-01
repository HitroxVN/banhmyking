import { useEffect, useState } from 'react';
import { ArrowRightLeft, TriangleAlert } from 'lucide-react';
import { staffOrderApi } from '../../api/staffOrderApi';
import { storeApi } from '../../api/storeApi';
import type { OrderResponse } from '../../types/order';
import type { PublicStore } from '../../types/store';
import { Button, Modal, Select, Textarea } from '../ui';
import { broadcastOrderChange } from '../../utils/orderSyncChannel';
import '../../styles/components/order-ops.css';

export interface TransferStoreModalProps {
  order: OrderResponse;
  onClose: () => void;
  onSuccess: (updatedOrder: OrderResponse) => void;
}

/** Chuyển đơn PENDING sang cơ sở khác kèm lý do bắt buộc — MANAGER / ADMIN. */
export const TransferStoreModal = ({ order, onClose, onSuccess }: TransferStoreModalProps) => {
  const [stores, setStores] = useState<PublicStore[]>([]);
  const [storesLoaded, setStoresLoaded] = useState(false);
  const [targetId, setTargetId] = useState('');
  const [reason, setReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    storeApi
      .listPublic()
      .then((data) => {
        if (!alive) return;
        setStores(data.filter((s) => s.id !== order.storeId));
        setStoresLoaded(true);
      })
      .catch((err: unknown) => {
        if (alive) setErrorMsg(err instanceof Error ? err.message : 'Không tải được danh sách cơ sở.');
      });
    return () => {
      alive = false;
    };
  }, [order.storeId]);

  const noOtherStore = storesLoaded && stores.length === 0;

  const handleSubmit = async () => {
    const trimmed = reason.trim();
    if (!targetId) {
      setErrorMsg('Vui lòng chọn cơ sở nhận đơn.');
      return;
    }
    if (!trimmed) {
      setErrorMsg('Vui lòng nhập lý do chuyển cơ sở.');
      return;
    }

    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      const updated = await staffOrderApi.transferStore(order.orderCode, Number(targetId), trimmed);
      broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode: order.orderCode });
      onSuccess(updated);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Chuyển cơ sở thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="md"
      title={`Chuyển cơ sở — ${order.orderCode}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button
            variant="primary"
            icon={<ArrowRightLeft size={17} />}
            loading={isSubmitting}
            disabled={noOtherStore || !targetId || !reason.trim()}
            onClick={handleSubmit}
          >
            Xác nhận chuyển
          </Button>
        </>
      }
    >
      {errorMsg && (
        <div className="alert-banner alert-error" role="alert">
          <TriangleAlert size={17} />
          <div>{errorMsg}</div>
        </div>
      )}

      <div className="ops-field">
        <Select
          label="Cơ sở nhận đơn"
          required
          value={targetId}
          disabled={noOtherStore}
          hint={noOtherStore ? 'Không có cơ sở khác để chuyển' : undefined}
          onChange={(event) => setTargetId(event.target.value)}
        >
          <option value="">— Chọn cơ sở —</option>
          {stores.map((store) => (
            // Cơ sở tạm ngưng nhận đơn chắc chắn bị backend từ chối — khoá sẵn để khỏi thử vô ích.
            <option key={store.id} value={store.id} disabled={!store.acceptingOrders}>
              {store.acceptingOrders ? store.name : `${store.name} (tạm ngưng nhận đơn)`}
            </option>
          ))}
        </Select>
      </div>

      <div className="ops-field">
        <Textarea
          label="Lý do chuyển"
          required
          rows={3}
          placeholder="Ví dụ: Cơ sở hiện tại hết nguyên liệu, khách ở gần cơ sở khác..."
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      </div>
    </Modal>
  );
};
