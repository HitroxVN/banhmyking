import React, { useState, useEffect } from 'react';
import { staffOrderApi } from '../../api/staffOrderApi';
import type { OrderResponse } from '../../types/order';
import type { ShipperAvailability } from '../../types/staff';
import { formatCurrency } from '../../utils/formatters';
import { broadcastOrderChange } from '../../utils/orderSyncChannel';

interface AssignShipperModalProps {
  order: OrderResponse;
  onClose: () => void;
  onSuccess: (updatedOrder: OrderResponse) => void;
}

export const AssignShipperModal: React.FC<AssignShipperModalProps> = ({
  order,
  onClose,
  onSuccess,
}) => {
  const [shippers, setShippers] = useState<ShipperAvailability[]>([]);
  const [selectedShipperId, setSelectedShipperId] = useState<number | null>(null);
  const [note, setNote] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;
    staffOrderApi
      .getAvailableShippers()
      .then((data) => {
        if (isMounted) {
          setShippers(data);
          // Chỉ tự động chọn shipper đang rảnh (available: true và 0 đơn)
          const freeShipper = data.find((s) => s.available && s.activeOrdersCount === 0);
          if (freeShipper) {
            setSelectedShipperId(freeShipper.id);
          } else {
            setSelectedShipperId(null);
          }
          setIsLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (isMounted) {
          const error = err as Error;
          setErrorMsg(error.message || 'Không thể tải danh sách tài xế.');
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const handleAssignToShipper = async (shipperId: number) => {
    const selectedShipper = shippers.find((s) => s.id === shipperId);
    if (!selectedShipper || !selectedShipper.available || selectedShipper.activeOrdersCount > 0) {
      setErrorMsg(
        `Tài xế ${selectedShipper?.fullName || ''} hiện đang có đơn (${selectedShipper?.activeOrdersCount || 1} đơn chưa hoàn tất). Nút gán đơn cho shipper này đã bị đóng băng!`
      );
      return;
    }

    setIsSubmitting(true);
    setSelectedShipperId(shipperId);
    setErrorMsg(null);
    try {
      const updated = await staffOrderApi.assignShipper(
        order.orderCode,
        shipperId,
        note.trim() ? note.trim() : undefined
      );
      broadcastOrderChange('ORDER_ASSIGNED', {
        orderCode: order.orderCode,
        shipperId: shipperId,
      });
      onSuccess(updated);
    } catch (err: unknown) {
      const error = err as Error;
      setErrorMsg(error.message || 'Gán shipper thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedShipperId) {
      setErrorMsg('Vui lòng chọn một tài xế đang rảnh để gán đơn hàng.');
      return;
    }
    await handleAssignToShipper(selectedShipperId);
  };

  return (
    <div className="modal-backdrop">
      <div className="modal-card modal-assign-shipper">
        <div className="modal-header">
          <div className="modal-header-title-wrap">
            <span className="modal-header-icon">🛵</span>
            <h3 className="modal-title">Điều Phối & Gán Shipper Giao Hàng</h3>
          </div>
          <button type="button" className="modal-close-btn" onClick={onClose}>
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            {errorMsg && (
              <div className="alert-banner alert-error" style={{ marginBottom: '1rem' }}>
                ⚠️ {errorMsg}
              </div>
            )}

            {/* Thông tin đơn hàng tóm tắt */}
            <div className="assign-order-summary">
              <div className="order-summary-row">
                <span className="summary-label">Mã đơn hàng:</span>
                <span className="summary-code">{order.orderCode}</span>
              </div>
              <div className="order-summary-row">
                <span className="summary-label">Khách nhận:</span>
                <span className="summary-value">
                  {order.receiverName} - {order.receiverPhone}
                </span>
              </div>
              <div className="order-summary-row">
                <span className="summary-label">Địa chỉ giao:</span>
                <span className="summary-value" title={order.shippingAddress}>
                  {order.shippingAddress}
                </span>
              </div>
              <div className="order-summary-row">
                <span className="summary-label">Tổng tiền:</span>
                <span className="summary-total">{formatCurrency(order.total)}</span>
              </div>
            </div>

            {/* Cảnh báo đóng băng nếu tất cả tài xế đều đang bận */}
            {!isLoading && shippers.length > 0 && shippers.every((s) => !s.available || s.activeOrdersCount > 0) && (
              <div className="alert-warning-frozen" role="alert" style={{ marginTop: '1rem' }}>
                🔒 <strong>TẤT CẢ SHIPPER ĐỀU ĐANG CÓ ĐƠN HÀNG!</strong>
                <p style={{ margin: '0.25rem 0 0' }}>
                  Hệ thống đã tự động đóng băng toàn bộ nút gán đơn để tránh quá tải cho tài xế. Vui lòng đợi tài xế hoàn tất đơn hiện tại hoặc phân công sau.
                </p>
              </div>
            )}

            <div className="form-group" style={{ marginTop: '1.25rem' }}>
              <label className="form-label">
                Danh sách Tài xế (Shipper) <span className="req">*</span>
              </label>

              {isLoading ? (
                <div className="shipper-loading-box">
                  <div className="spinner-mini"></div>
                  <span>Đang kiểm tra trạng thái các tài xế...</span>
                </div>
              ) : shippers.length === 0 ? (
                <div className="shipper-empty-box">
                  ⚠️ Chưa có tài xế nào trực thuộc hệ thống. Vui lòng liên hệ Admin để tạo tài khoản Shipper.
                </div>
              ) : (
                <div className="shipper-select-list">
                  {shippers.map((s) => {
                    const isSelected = selectedShipperId === s.id;
                    const isFree = s.available && s.activeOrdersCount === 0;
                    return (
                      <label
                        key={s.id}
                        className={`shipper-item-card ${isSelected ? 'selected' : ''} ${
                          !isFree ? 'disabled busy-frozen' : ''
                        }`}
                        onClick={(e) => {
                          if (!isFree) {
                            e.preventDefault();
                            setErrorMsg(
                              `Tài xế ${s.fullName} đang có ${s.activeOrdersCount} đơn hàng chưa giao xong. Nút gán đơn cho tài xế này đã bị đóng băng!`
                            );
                          } else {
                            setSelectedShipperId(s.id);
                            setErrorMsg(null);
                          }
                        }}
                      >
                        <input
                          type="radio"
                          name="shipperRadio"
                          value={s.id}
                          checked={isSelected}
                          disabled={!isFree}
                          onChange={() => {
                            if (isFree) {
                              setSelectedShipperId(s.id);
                              setErrorMsg(null);
                            }
                          }}
                        />
                        <div className="shipper-avatar">
                          {s.fullName ? s.fullName.charAt(0).toUpperCase() : 'S'}
                        </div>
                        <div className="shipper-details">
                          <div className="shipper-top-row">
                            <span className="shipper-name">{s.fullName}</span>
                            {isFree ? (
                              <span className="shipper-status-chip free">
                                🟢 Đang rảnh (0 đơn)
                              </span>
                            ) : (
                              <span className="shipper-status-chip busy-frozen-badge">
                                🔒 Đang bận ({s.activeOrdersCount} đơn) - Đóng băng
                              </span>
                            )}
                          </div>
                          <span className="shipper-subtext">
                            📞 {s.phone || 'Chưa cập nhật SĐT'} | ✉️ {s.email}
                          </span>
                          {!isFree && (
                            <div className="shipper-busy-explanation">
                              ⛔ Đang xử lý {s.activeOrdersCount} đơn hàng. Nút gán đơn đã bị đóng băng!
                            </div>
                          )}
                        </div>

                        {/* Nút gán đơn trực tiếp cho shipper này */}
                        <div className="shipper-action-wrap">
                          {isFree ? (
                            <button
                              type="button"
                              id={`btn-assign-shipper-${s.id}`}
                              className="btn-assign-shipper-row active"
                              disabled={isSubmitting}
                              onClick={(e) => {
                                e.stopPropagation();
                                e.preventDefault();
                                handleAssignToShipper(s.id);
                              }}
                              title={`Gán đơn ${order.orderCode} ngay cho shipper ${s.fullName}`}
                            >
                              🛵 Gán đơn
                            </button>
                          ) : (
                            <button
                              type="button"
                              id={`btn-assign-shipper-${s.id}-frozen`}
                              className="btn-assign-shipper-row frozen"
                              disabled={true}
                              onClick={(e) => {
                                e.stopPropagation();
                                e.preventDefault();
                              }}
                              title={`Tài xế ${s.fullName} đang có ${s.activeOrdersCount} đơn hàng chưa giao xong. Nút gán đơn cho shipper này đã bị đóng băng!`}
                            >
                              🔒 Đã đóng băng (Đang có đơn)
                            </button>
                          )}
                        </div>
                      </label>
                    );
                  })}
                </div>
              )}
            </div>

            <div className="form-group" style={{ marginTop: '1rem' }}>
              <label className="form-label">Ghi chú điều phối (Tuỳ chọn)</label>
              <input
                type="text"
                placeholder="Ví dụ: Bánh nóng giòn, giao ngay trong 15 phút..."
                value={note}
                onChange={(e) => setNote(e.target.value)}
                className="form-input"
              />
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn-outline" onClick={onClose} disabled={isSubmitting}>
              Huỷ bỏ
            </button>
            {(() => {
              const selectedShipper = shippers.find((s) => s.id === selectedShipperId);
              const canAssign = selectedShipper
                ? selectedShipper.available && selectedShipper.activeOrdersCount === 0
                : false;
              const allBusy =
                shippers.length > 0 &&
                shippers.every((s) => !s.available || s.activeOrdersCount > 0);

              return (
                <button
                  type="submit"
                  id="btn-submit-assign-shipper"
                  className={`btn-primary ${!canAssign ? 'btn-frozen' : ''}`}
                  disabled={
                    isSubmitting ||
                    shippers.length === 0 ||
                    !selectedShipperId ||
                    !canAssign
                  }
                  title={!canAssign ? 'Vui lòng chọn tài xế đang rảnh' : 'Xác nhận gán đơn cho tài xế'}
                >
                  {isSubmitting ? (
                    'Đang bàn giao...'
                  ) : allBusy ? (
                    '🔒 Nút gán bị đóng băng (Hết tài xế rảnh)'
                  ) : !selectedShipperId ? (
                    '👉 Hãy chọn tài xế đang rảnh'
                  ) : !canAssign || !selectedShipper ? (
                    '🔒 Nút gán bị đóng băng (Tài xế đang có đơn)'
                  ) : (
                    `🛵 Xác nhận gán cho ${selectedShipper.fullName}`
                  )}
                </button>
              );
            })()}
          </div>
        </form>
      </div>
    </div>
  );
};
