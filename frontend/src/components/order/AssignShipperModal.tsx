import { useEffect, useState } from 'react';
import { Bike, Lock, TriangleAlert } from 'lucide-react';
import { staffOrderApi } from '../../api/staffOrderApi';
import type { OrderResponse } from '../../types/order';
import type { ShipperAvailability } from '../../types/staff';
import { Badge, Button, Modal, Spinner, Textarea } from '../ui';
import { formatCurrency } from '../../utils/formatters';
import { broadcastOrderChange } from '../../utils/orderSyncChannel';
import '../../styles/components/order-ops.css';

export interface AssignShipperModalProps {
  order: OrderResponse;
  onClose: () => void;
  onSuccess: (updatedOrder: OrderResponse) => void;
}

/** Chọn tài xế đang rảnh để gán đơn — tài xế đang có đơn sẽ bị khoá nút. */
export const AssignShipperModal = ({ order, onClose, onSuccess }: AssignShipperModalProps) => {
  const [shippers, setShippers] = useState<ShipperAvailability[]>([]);
  const [selectedShipperId, setSelectedShipperId] = useState<number | null>(null);
  const [note, setNote] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    staffOrderApi
      .getAvailableShippers()
      .then((data) => {
        if (cancelled) return;
        setShippers(data);
        setSelectedShipperId(data.find((s) => s.available && s.activeOrdersCount === 0)?.id ?? null);
      })
      .catch((err: unknown) => {
        if (!cancelled) setErrorMsg(err instanceof Error ? err.message : 'Không thể tải danh sách tài xế.');
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const isFree = (s: ShipperAvailability) => s.available && s.activeOrdersCount === 0;
  const allBusy = shippers.length > 0 && shippers.every((s) => !isFree(s));

  const handleAssign = async () => {
    const selected = shippers.find((s) => s.id === selectedShipperId);
    if (!selected) {
      setErrorMsg('Vui lòng chọn một tài xế đang rảnh để gán đơn hàng.');
      return;
    }
    if (!isFree(selected)) {
      setErrorMsg(
        `Tài xế ${selected.fullName} đang có ${selected.activeOrdersCount} đơn chưa giao xong. Vui lòng chọn tài xế khác.`
      );
      return;
    }

    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      const updated = await staffOrderApi.assignShipper(
        order.orderCode,
        selected.id,
        note.trim() || undefined
      );
      broadcastOrderChange('ORDER_ASSIGNED', { orderCode: order.orderCode, shipperId: selected.id });
      onSuccess(updated);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Gán tài xế thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="md"
      title={`Gán tài xế — ${order.orderCode}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button
            variant="primary"
            icon={<Bike size={17} />}
            loading={isSubmitting}
            disabled={!selectedShipperId || allBusy}
            onClick={handleAssign}
          >
            Gán đơn cho tài xế
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

      <div className="ops-sum">
        <div className="ops-sum__row">
          <span className="ops-sum__label">Khách nhận</span>
          <span className="ops-sum__value">
            {order.receiverName} · {order.receiverPhone}
          </span>
        </div>
        <div className="ops-sum__row">
          <span className="ops-sum__label">Giao tới</span>
          <span className="ops-sum__value">{order.shippingAddress}</span>
        </div>
        <div className="ops-sum__row">
          <span className="ops-sum__label">Tổng tiền</span>
          <span className="ops-sum__value ops-sum__total">{formatCurrency(order.total)}</span>
        </div>
      </div>

      {allBusy && (
        <div className="ops-warn" role="alert">
          <Lock size={17} />
          <span>
            <strong>Tất cả tài xế đang có đơn.</strong>
            <p>Vui lòng đợi một tài xế hoàn tất đơn hiện tại rồi phân công lại.</p>
          </span>
        </div>
      )}

      <div className="ops-field">
        <span className="ops-group-title">Chọn tài xế</span>

        {isLoading ? (
          <div className="ops-state">
            <Spinner size={22} />
          </div>
        ) : shippers.length === 0 ? (
          <p className="ops-empty">Chưa có tài khoản tài xế nào trong hệ thống.</p>
        ) : (
          <div className="ops-shippers">
            {shippers.map((shipper) => {
              const free = isFree(shipper);
              return (
                <button
                  key={shipper.id}
                  type="button"
                  className={`ops-shipper${selectedShipperId === shipper.id ? ' ops-shipper--on' : ''}`}
                  disabled={!free}
                  aria-pressed={selectedShipperId === shipper.id}
                  onClick={() => {
                    setSelectedShipperId(shipper.id);
                    setErrorMsg(null);
                  }}
                >
                  <span className="ops-shipper__avatar">
                    <Bike size={17} />
                  </span>
                  <span>
                    <span className="ops-shipper__name">{shipper.fullName}</span>
                    <span className="ops-shipper__meta">{shipper.phone}</span>
                  </span>
                  <span className="ops-shipper__badge">
                    {free ? (
                      <Badge tone="success">Đang rảnh</Badge>
                    ) : (
                      <Badge tone="warning">{shipper.activeOrdersCount} đơn chưa xong</Badge>
                    )}
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      <div className="ops-field">
        <Textarea
          label="Ghi chú điều phối"
          hint="Không bắt buộc — tài xế sẽ nhìn thấy ghi chú này"
          rows={3}
          placeholder="Ví dụ: Giao trước 11h30, khách ở tầng 3..."
          value={note}
          onChange={(event) => setNote(event.target.value)}
        />
      </div>
    </Modal>
  );
};
