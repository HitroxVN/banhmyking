import { useState } from 'react';
import { TriangleAlert } from 'lucide-react';
import { staffOrderApi } from '../../api/staffOrderApi';
import type { OrderResponse } from '../../types/order';
import { Button, Input, Modal, Textarea } from '../ui';
import { formatCurrency } from '../../utils/formatters';
import { broadcastOrderChange } from '../../utils/orderSyncChannel';
import '../../styles/components/order-ops.css';

export interface RefundOrderModalProps {
  order: OrderResponse;
  onClose: () => void;
  onSuccess: (updatedOrder: OrderResponse, reason: string) => void;
}

/** Hoàn tiền cho đơn đã thu — Staff và Admin dùng chung. */
export const RefundOrderModal = ({ order, onClose, onSuccess }: RefundOrderModalProps) => {
  const [reason, setReason] = useState('');
  const [amount, setAmount] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleSubmit = async () => {
    const trimmed = reason.trim();
    if (!trimmed) {
      setErrorMsg('Vui lòng nhập lý do hoàn tiền.');
      return;
    }

    const parsed = amount.trim() ? Number(amount) : undefined;
    if (parsed !== undefined && (!Number.isFinite(parsed) || parsed <= 0)) {
      setErrorMsg('Số tiền hoàn phải là số lớn hơn 0.');
      return;
    }

    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      const updated = await staffOrderApi.refundOrder(order.orderCode, trimmed, parsed);
      broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode: order.orderCode });
      onSuccess(updated, trimmed);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Hoàn tiền thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="md"
      title={`Hoàn tiền đơn ${order.orderCode}`}
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
            Xác nhận hoàn tiền
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
          Hoàn tiền cho đơn <strong>{order.orderCode}</strong> của{' '}
          <strong>{order.receiverName}</strong>. Số tiền đã thu:{' '}
          {formatCurrency(order.total)}.
        </span>
      </div>

      <div className="ops-field">
        <Input
          label="Số tiền hoàn"
          type="number"
          min="0"
          placeholder={String(order.total)}
          hint={`Bỏ trống để hoàn toàn bộ ${formatCurrency(order.total)}`}
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
        />
      </div>

      <div className="ops-field">
        <Textarea
          label="Lý do hoàn tiền"
          required
          rows={3}
          placeholder="Ví dụ: Khách chuyển khoản nhầm, đơn không giao được..."
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      </div>
    </Modal>
  );
};
