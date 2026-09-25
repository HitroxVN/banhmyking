import { useState } from 'react';
import { TriangleAlert } from 'lucide-react';
import { staffOrderApi } from '../../api/staffOrderApi';
import type { OrderResponse } from '../../types/order';
import { Button, Modal, Textarea } from '../ui';
import { formatCurrency } from '../../utils/formatters';
import { broadcastOrderChange } from '../../utils/orderSyncChannel';
import '../../styles/components/order-ops.css';

export interface CancelOrderModalProps {
  order: OrderResponse;
  onClose: () => void;
  onSuccess: (updatedOrder: OrderResponse, reason: string) => void;
}

/** Huỷ đơn kèm lý do bắt buộc — Staff và Admin dùng chung. */
export const CancelOrderModal = ({ order, onClose, onSuccess }: CancelOrderModalProps) => {
  const [reason, setReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSubmit = async () => {
    const trimmed = reason.trim();
    if (!trimmed) {
      setErrorMsg('Vui lòng nhập lý do huỷ đơn hàng.');
      return;
    }

    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      const updated = await staffOrderApi.cancelOrder(order.orderCode, trimmed);
      broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode: order.orderCode });
      onSuccess(updated, trimmed);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Huỷ đơn hàng thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="md"
      title={`Huỷ đơn ${order.orderCode}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button
            variant="danger"
            loading={isSubmitting}
            disabled={!reason.trim()}
            onClick={handleSubmit}
          >
            Xác nhận huỷ đơn
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

      <div className="ops-danger-note">
        <TriangleAlert size={17} />
        <span>
          Huỷ đơn <strong>{order.orderCode}</strong> của <strong>{order.receiverName}</strong> (
          {order.receiverPhone}) trị giá {formatCurrency(order.total)}. Hành động này không thể hoàn
          tác.
        </span>
      </div>

      <div className="ops-field">
        <Textarea
          label="Lý do huỷ"
          required
          rows={3}
          placeholder="Ví dụ: Khách gọi yêu cầu huỷ, hết nguyên liệu..."
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      </div>
    </Modal>
  );
};
