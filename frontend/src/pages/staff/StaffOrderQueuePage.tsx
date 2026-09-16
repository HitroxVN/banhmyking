import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { StaffLayout } from '../../components/staff/StaffLayout';
import { staffOrderApi } from '../../api/staffOrderApi';
import { AssignShipperModal } from '../../components/staff/AssignShipperModal';
import type { OrderResponse, OrderStatus } from '../../types/order';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { orderSyncChannel, broadcastOrderChange } from '../../utils/orderSyncChannel';

type TabFilter = 'ALL' | 'NEW' | 'PREPARING' | 'READY' | 'DELIVERING' | 'HISTORY';

export const StaffOrderQueuePage: React.FC = () => {
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [activeTab, setActiveTab] = useState<TabFilter>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [alert, setAlert] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Thao tác với đơn hàng
  const [actionLoadingOrderCode, setActionLoadingOrderCode] = useState<string | null>(null);
  const [assigningOrder, setAssigningOrder] = useState<OrderResponse | null>(null);
  const [cancellingOrder, setCancellingOrder] = useState<OrderResponse | null>(null);
  const [cancelReason, setCancelReason] = useState<string>('');
  const [currentTime, setCurrentTime] = useState<number>(() => Date.now());

  // Tải dữ liệu hàng đợi đơn hàng
  const fetchOrders = useCallback(async (isManual = false) => {
    if (isManual) {
      setIsRefreshing(true);
    }
    try {
      // Lấy toàn bộ đơn hàng (size 100) để phục vụ sắp xếp và thống kê metrics trực tiếp
      const pageData = await staffOrderApi.getOrderQueue(undefined, 0, 100);
      setOrders(pageData.content);
      setCurrentTime(Date.now());
    } catch (err: unknown) {
      const error = err as Error;
      if (isManual) {
        setAlert({ type: 'error', message: error.message || 'Không thể tải hàng đợi đơn hàng.' });
      }
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  // Polling tự động mỗi 6 giây để cập nhật đơn mới realtime
  useEffect(() => {
    let ignore = false;
    staffOrderApi
      .getOrderQueue(undefined, 0, 100)
      .then((pageData) => {
        if (!ignore) {
          setOrders(pageData.content);
          setCurrentTime(Date.now());
          setIsLoading(false);
        }
      })
      .catch(() => {
        if (!ignore) {
          setIsLoading(false);
        }
      });

    const intervalId = setInterval(() => {
      fetchOrders(false);
    }, 3000);

    const handleSync = () => {
      fetchOrders(false);
    };

    if (orderSyncChannel) {
      orderSyncChannel.addEventListener('message', handleSync);
    }

    const handleVisibility = () => {
      if (document.visibilityState === 'visible') {
        fetchOrders(false);
      }
    };
    const handleFocus = () => {
      fetchOrders(false);
    };

    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('focus', handleFocus);

    return () => {
      ignore = true;
      clearInterval(intervalId);
      if (orderSyncChannel) {
        orderSyncChannel.removeEventListener('message', handleSync);
      }
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('focus', handleFocus);
    };
  }, [fetchOrders]);

  // Tính thời gian chờ (phút) kể từ lúc tạo đơn
  const getElapsedMinutes = useCallback((createdAtStr: string): number => {
    try {
      const createdTime = new Date(createdAtStr).getTime();
      const diffMs = currentTime - createdTime;
      return Math.max(0, Math.floor(diffMs / 60000));
    } catch {
      return 0;
    }
  }, [currentTime]);

  // Thống kê số lượng đơn theo trạng thái (KPI metrics)
  const metrics = useMemo(() => {
    const pending = orders.filter((o) => o.status === 'PENDING' || o.status === 'CONFIRMED').length;
    const preparing = orders.filter((o) => o.status === 'PREPARING').length;
    const ready = orders.filter((o) => o.status === 'READY_FOR_PICKUP').length;
    const delivering = orders.filter((o) => o.status === 'DELIVERING').length;
    const completed = orders.filter((o) => o.status === 'DELIVERED').length;
    return { pending, preparing, ready, delivering, completed };
  }, [orders]);

  // Lọc đơn hàng theo tab và từ khoá tìm kiếm
  const filteredOrders = useMemo(() => {
    return orders.filter((order) => {
      // Lọc theo Tab
      if (activeTab === 'NEW' && order.status !== 'PENDING' && order.status !== 'CONFIRMED') {
        return false;
      }
      if (activeTab === 'PREPARING' && order.status !== 'PREPARING') {
        return false;
      }
      if (activeTab === 'READY' && order.status !== 'READY_FOR_PICKUP') {
        return false;
      }
      if (activeTab === 'DELIVERING' && order.status !== 'DELIVERING') {
        return false;
      }
      if (activeTab === 'HISTORY' && order.status !== 'DELIVERED' && order.status !== 'CANCELLED') {
        return false;
      }

      // Lọc theo Search Query
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase().trim();
        const matchCode = order.orderCode.toLowerCase().includes(query);
        const matchName = order.receiverName.toLowerCase().includes(query);
        const matchPhone = order.receiverPhone.includes(query);
        const matchAddress = order.shippingAddress.toLowerCase().includes(query);
        return matchCode || matchName || matchPhone || matchAddress;
      }

      return true;
    });
  }, [orders, activeTab, searchQuery]);

  // Hành động: Nhận đơn và bắt đầu chế biến (PENDING -> CONFIRMED -> PREPARING)
  const handleStartPreparing = async (order: OrderResponse) => {
    setActionLoadingOrderCode(order.orderCode);
    setAlert(null);
    try {
      const updated = await staffOrderApi.startPreparingOrder(order);
      broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode: order.orderCode });
      setOrders((prev) => prev.map((o) => (o.orderCode === updated.orderCode ? updated : o)));
      setAlert({
        type: 'success',
        message: `Đơn ${order.orderCode}: Đã chuyển sang trạng thái 👨‍🍳 ĐANG CHẾ BIẾN!`,
      });
    } catch (err: unknown) {
      const error = err as Error;
      setAlert({
        type: 'error',
        message: error.message || 'Không thể chuyển trạng thái đơn sang PREPARING.',
      });
    } finally {
      setActionLoadingOrderCode(null);
    }
  };

  // Hành động: Bánh đã làm xong (PREPARING -> READY_FOR_PICKUP)
  const handleMarkReady = async (orderCode: string) => {
    setActionLoadingOrderCode(orderCode);
    setAlert(null);
    try {
      const updated = await staffOrderApi.markReadyForPickup(orderCode);
      broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode });
      setOrders((prev) => prev.map((o) => (o.orderCode === updated.orderCode ? updated : o)));
      setAlert({
        type: 'success',
        message: `Đơn ${orderCode}: Đã làm xong! Sẵn sàng điều phối Shipper giao hàng.`,
      });
    } catch (err: unknown) {
      const error = err as Error;
      setAlert({
        type: 'error',
        message: error.message || 'Không thể chuyển trạng thái đơn sang READY_FOR_PICKUP.',
      });
    } finally {
      setActionLoadingOrderCode(null);
    }
  };

  // Callback sau khi gán Shipper thành công từ Modal
  const handleAssignSuccess = (updatedOrder: OrderResponse) => {
    setOrders((prev) => prev.map((o) => (o.orderCode === updatedOrder.orderCode ? updatedOrder : o)));
    setAssigningOrder(null);
    setAlert({
      type: 'success',
      message: `Đã gán đơn ${updatedOrder.orderCode} cho Shipper thành công! Đơn chuyển sang trạng thái 🚀 ĐANG GIAO.`,
    });
  };

  // Hành động: Hủy đơn hàng
  const handleConfirmCancel = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!cancellingOrder) return;
    if (!cancelReason.trim()) {
      setAlert({ type: 'error', message: 'Vui lòng nhập lý do hủy đơn hàng.' });
      return;
    }

    setActionLoadingOrderCode(cancellingOrder.orderCode);
    try {
      const updated = await staffOrderApi.cancelOrder(cancellingOrder.orderCode, cancelReason.trim());
      broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode: cancellingOrder.orderCode });
      setOrders((prev) => prev.map((o) => (o.orderCode === updated.orderCode ? updated : o)));
      setAlert({
        type: 'success',
        message: `Đã hủy đơn ${cancellingOrder.orderCode}. Lý do: ${cancelReason.trim()}`,
      });
      setCancellingOrder(null);
      setCancelReason('');
    } catch (err: unknown) {
      const error = err as Error;
      setAlert({ type: 'error', message: error.message || 'Hủy đơn hàng thất bại.' });
    } finally {
      setActionLoadingOrderCode(null);
    }
  };

  // Helper render badge trạng thái đơn hàng
  const renderStatusBadge = (status: OrderStatus) => {
    switch (status) {
      case 'PENDING':
        return <span className="status-badge pending">🟡 Đơn Mới Đặt</span>;
      case 'CONFIRMED':
        return <span className="status-badge confirmed">🔵 Đã Xác Nhận</span>;
      case 'PREPARING':
        return <span className="status-badge preparing">👨‍🍳 Đang Chế Biến</span>;
      case 'READY_FOR_PICKUP':
        return <span className="status-badge ready">📦 Chờ Shipper Lấy</span>;
      case 'DELIVERING':
        return <span className="status-badge delivering">🛵 Đang Giao Hàng</span>;
      case 'DELIVERED':
        return <span className="status-badge delivered">✅ Đã Giao Thành Công</span>;
      case 'CANCELLED':
        return <span className="status-badge cancelled">🔴 Đã Hủy</span>;
      default:
        return <span className="status-badge">{status}</span>;
    }
  };

  return (
    <StaffLayout
      title="Hàng Đợi Tiếp Nhận & Điều Phối Đơn Hàng"
      subtitle="Tiếp nhận đơn realtime, điều phối quy trình làm bánh và bàn giao cho Shipper"
      onRefresh={() => fetchOrders(true)}
      isRefreshing={isRefreshing}
    >
      {/* Alert Banner */}
      {alert && (
        <div
          className={`alert-banner ${alert.type === 'success' ? 'alert-success' : 'alert-error'}`}
          style={{ marginBottom: '1.25rem' }}
        >
          <div>{alert.type === 'success' ? '✅' : '⚠️'} {alert.message}</div>
          <button type="button" className="alert-close-btn" onClick={() => setAlert(null)}>
            ✕
          </button>
        </div>
      )}

      {/* KPI Metrics Dashboard Row */}
      <div className="staff-metrics-grid">
        <div
          className={`metric-card ${activeTab === 'NEW' ? 'active-metric' : ''}`}
          onClick={() => setActiveTab('NEW')}
        >
          <div className="metric-header">
            <span className="metric-icon">🔔</span>
            <span className="metric-badge-count">{metrics.pending}</span>
          </div>
          <div className="metric-title">Đơn Mới Cần Làm</div>
          <div className="metric-desc">PENDING & CONFIRMED</div>
        </div>

        <div
          className={`metric-card ${activeTab === 'PREPARING' ? 'active-metric' : ''}`}
          onClick={() => setActiveTab('PREPARING')}
        >
          <div className="metric-header">
            <span className="metric-icon">👨‍🍳</span>
            <span className="metric-badge-count">{metrics.preparing}</span>
          </div>
          <div className="metric-title">Bếp Đang Làm</div>
          <div className="metric-desc">PREPARING</div>
        </div>

        <div
          className={`metric-card ${activeTab === 'READY' ? 'active-metric' : ''}`}
          onClick={() => setActiveTab('READY')}
        >
          <div className="metric-header">
            <span className="metric-icon">📦</span>
            <span className="metric-badge-count">{metrics.ready}</span>
          </div>
          <div className="metric-title">Chờ Shipper Nhận</div>
          <div className="metric-desc">READY_FOR_PICKUP</div>
        </div>

        <div
          className={`metric-card ${activeTab === 'DELIVERING' ? 'active-metric' : ''}`}
          onClick={() => setActiveTab('DELIVERING')}
        >
          <div className="metric-header">
            <span className="metric-icon">🛵</span>
            <span className="metric-badge-count">{metrics.delivering}</span>
          </div>
          <div className="metric-title">Shipper Đang Giao</div>
          <div className="metric-desc">DELIVERING</div>
        </div>

        <div
          className={`metric-card ${activeTab === 'HISTORY' ? 'active-metric' : ''}`}
          onClick={() => setActiveTab('HISTORY')}
        >
          <div className="metric-header">
            <span className="metric-icon">🏁</span>
            <span className="metric-badge-count">{metrics.completed}</span>
          </div>
          <div className="metric-title">Hoàn Tất Hôm Nay</div>
          <div className="metric-desc">DELIVERED</div>
        </div>
      </div>

      {/* Control Bar: Filter Tabs & Search */}
      <div className="queue-controls-bar">
        <div className="queue-filter-tabs">
          <button
            type="button"
            className={`filter-tab-btn ${activeTab === 'ALL' ? 'active' : ''}`}
            onClick={() => setActiveTab('ALL')}
          >
            Tất cả ({orders.length})
          </button>
          <button
            type="button"
            className={`filter-tab-btn ${activeTab === 'NEW' ? 'active' : ''}`}
            onClick={() => setActiveTab('NEW')}
          >
            🔥 Cần làm ngay ({metrics.pending})
          </button>
          <button
            type="button"
            className={`filter-tab-btn ${activeTab === 'PREPARING' ? 'active' : ''}`}
            onClick={() => setActiveTab('PREPARING')}
          >
            👨‍🍳 Đang chế biến ({metrics.preparing})
          </button>
          <button
            type="button"
            className={`filter-tab-btn ${activeTab === 'READY' ? 'active' : ''}`}
            onClick={() => setActiveTab('READY')}
          >
            📦 Chờ Shipper ({metrics.ready})
          </button>
          <button
            type="button"
            className={`filter-tab-btn ${activeTab === 'DELIVERING' ? 'active' : ''}`}
            onClick={() => setActiveTab('DELIVERING')}
          >
            🛵 Đang giao ({metrics.delivering})
          </button>
          <button
            type="button"
            className={`filter-tab-btn ${activeTab === 'HISTORY' ? 'active' : ''}`}
            onClick={() => setActiveTab('HISTORY')}
          >
            📜 Lịch sử / Hủy
          </button>
        </div>

        <div className="search-input-wrapper" style={{ minWidth: '280px' }}>
          <span className="search-icon">🔍</span>
          <input
            type="text"
            placeholder="Tìm mã đơn, tên khách, SĐT..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="users-search-input"
          />
        </div>
      </div>

      {/* Order Cards List */}
      {isLoading && orders.length === 0 ? (
        <div className="dashboard-loading-skeleton">
          <div className="spinner-royal"></div>
          <p>Đang đồng bộ đơn hàng từ máy chủ Bánh Mỳ King...</p>
        </div>
      ) : filteredOrders.length === 0 ? (
        <div className="menu-empty-card">
          <span style={{ fontSize: '3.5rem' }}>👨‍🍳</span>
          <h3>Không có đơn hàng nào trong danh sách</h3>
          <p>Hiện không có đơn hàng nào khớp với điều kiện lọc. Các đơn mới sẽ tự động hiển thị tại đây theo thời gian thực.</p>
        </div>
      ) : (
        <div className="queue-orders-list">
          {filteredOrders.map((order) => {
            const elapsed = getElapsedMinutes(order.createdAt);
            const isUrgent = elapsed >= 15 && order.status !== 'DELIVERED' && order.status !== 'CANCELLED';
            const isProcessingThis = actionLoadingOrderCode === order.orderCode;

            return (
              <div
                key={order.id}
                className={`order-queue-card ${isUrgent ? 'urgent' : ''} ${order.status.toLowerCase()}`}
              >
                {/* Header Card */}
                <div className="order-queue-header">
                  <div className="order-main-identifiers">
                    <span className="order-code-badge">{order.orderCode}</span>
                    {renderStatusBadge(order.status)}

                    {/* Timer Badge */}
                    <span className={`elapsed-timer-badge ${isUrgent ? 'urgent-timer' : ''}`}>
                      {isUrgent ? '⚠️ Chờ: ' : '⏱️ Chờ: '}
                      {elapsed} phút
                    </span>
                  </div>

                  <div className="order-meta-info">
                    <span className="order-time-text">
                      {formatDateTime(order.createdAt)}
                    </span>
                    {order.paymentStatus === 'PAID' ? (
                      <span className="payment-status-pill paid">
                        💳 Đã thanh toán ({order.paymentMethod})
                      </span>
                    ) : (
                      <span className="payment-status-pill pending">
                        💵 Chưa thanh toán ({order.paymentMethod})
                      </span>
                    )}
                  </div>
                </div>

                {/* Grid Body: Khách hàng & Món ăn */}
                <div className="order-queue-body">
                  {/* Cột 1: Thông tin khách hàng & Giao nhận */}
                  <div className="order-info-column">
                    <div className="info-block-item">
                      <span className="info-label">👤 Khách nhận:</span>
                      <span className="info-value strong">
                        {order.receiverName} ({order.receiverPhone})
                      </span>
                    </div>

                    <div className="info-block-item">
                      <span className="info-label">📍 Địa chỉ:</span>
                      <span className="info-value" title={order.shippingAddress}>
                        {order.shippingAddress}
                      </span>
                    </div>

                    {order.note && (
                      <div className="order-note-alert">
                        <span className="note-label">📝 Ghi chú của khách:</span>
                        <span className="note-content">"{order.note}"</span>
                      </div>
                    )}

                    {/* Thông tin Shipper nếu đã gán */}
                    {order.shipperName && (
                      <div className="shipper-assigned-box">
                        <span className="shipper-label">🛵 Tài xế giao hàng:</span>
                        <span className="shipper-val">
                          <strong>{order.shipperName}</strong> ({order.shipperPhone || 'SĐT cập nhật sau'})
                        </span>
                      </div>
                    )}

                    {order.cancelReason && (
                      <div className="cancel-reason-box">
                        <span className="cancel-label">🔴 Lý do hủy:</span>
                        <span className="cancel-val">{order.cancelReason}</span>
                      </div>
                    )}
                  </div>

                  {/* Cột 2: Danh sách món ăn chi tiết (Slip phiếu bếp) */}
                  <div className="order-items-column">
                    <div className="kitchen-slip-title">
                      <span>CHI TIẾT MÓN ĂN</span>
                      <span className="slip-count">{order.items.length} món</span>
                    </div>

                    <div className="kitchen-items-list">
                      {order.items.map((item) => (
                        <div key={item.id} className="kitchen-item-row">
                          <div className="kitchen-item-qty">{item.quantity}x</div>
                          <div className="kitchen-item-content">
                            <div className="kitchen-item-name">{item.productName}</div>
                            {item.options && item.options.length > 0 && (
                              <div className="kitchen-item-options">
                                {item.options.map((opt) => (
                                  <span key={opt.id} className="item-opt-pill">
                                    + {opt.optionName} ({formatCurrency(opt.optionPrice)})
                                  </span>
                                ))}
                              </div>
                            )}
                          </div>
                          <div className="kitchen-item-price">
                            {formatCurrency(item.lineTotal)}
                          </div>
                        </div>
                      ))}
                    </div>

                    {/* Order Cost summary */}
                    <div className="order-cost-summary">
                      <div className="cost-row">
                        <span>Tạm tính:</span>
                        <span>{formatCurrency(order.subtotal)}</span>
                      </div>
                      <div className="cost-row">
                        <span>Phí ship:</span>
                        <span>{formatCurrency(order.shippingFee)}</span>
                      </div>
                      {order.discountAmount > 0 && (
                        <div className="cost-row discount">
                          <span>Giảm giá ({order.promotionCode}):</span>
                          <span>-{formatCurrency(order.discountAmount)}</span>
                        </div>
                      )}
                      <div className="cost-row total-highlight">
                        <span>TỔNG CỘNG:</span>
                        <span className="total-number">{formatCurrency(order.total)}</span>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Footer Actions Row */}
                <div className="order-queue-actions">
                  <div className="action-status-prompt">
                    {order.status === 'PENDING' && (
                      <span className="prompt-text">
                        👉 Đơn hàng mới đặt từ khách, hãy bấm tiếp nhận để bắt đầu làm bánh.
                      </span>
                    )}
                    {order.status === 'CONFIRMED' && (
                      <span className="prompt-text">
                        👉 Đơn đã xác nhận, bấm bắt đầu làm để chuyển vào hàng đợi bếp.
                      </span>
                    )}
                    {order.status === 'PREPARING' && (
                      <span className="prompt-text">
                        👨‍🍳 Bếp đang làm bánh... Khi hoàn tất, bấm "Bánh đã làm xong".
                      </span>
                    )}
                    {order.status === 'READY_FOR_PICKUP' && (
                      <span className="prompt-text highlight">
                        {order.shipperName ? (
                          <>🛵 Đã gán cho shipper: <strong>{order.shipperName}</strong> (Đang chờ tài xế nhận đơn)</>
                        ) : (
                          <>📦 Bánh đã sẵn sàng! Hãy gán cho một SHIPPER đang rảnh để nhận đơn.</>
                        )}
                      </span>
                    )}
                    {order.status === 'DELIVERING' && (
                      <span className="prompt-text">
                        🚀 Đang giao hàng bởi {order.shipperName || 'Shipper'}.
                      </span>
                    )}
                    {order.status === 'DELIVERED' && (
                      <span className="prompt-text success">
                        ✅ Đơn hàng đã được giao thành công tới tay khách hàng!
                      </span>
                    )}
                  </div>

                  <div className="action-buttons-group">
                    {/* Luồng 1: Chuyển PENDING / CONFIRMED -> PREPARING */}
                    {(order.status === 'PENDING' || order.status === 'CONFIRMED') && (
                      <button
                        type="button"
                        className="btn-primary btn-queue-action"
                        onClick={() => handleStartPreparing(order)}
                        disabled={isProcessingThis}
                      >
                        {isProcessingThis ? (
                          <>
                            <span className="spinner-mini"></span> Đang nhận đơn...
                          </>
                        ) : (
                          <>👨‍🍳 Nhận Đơn & Bắt Đầu Làm</>
                        )}
                      </button>
                    )}

                    {/* Luồng 2: Chuyển PREPARING -> READY_FOR_PICKUP */}
                    {order.status === 'PREPARING' && (
                      <button
                        type="button"
                        className="btn-success btn-queue-action"
                        onClick={() => handleMarkReady(order.orderCode)}
                        disabled={isProcessingThis}
                      >
                        {isProcessingThis ? (
                          <>
                            <span className="spinner-mini"></span> Đang cập nhật...
                          </>
                        ) : (
                          <>✅ Bánh Đã Làm Xong (Sẵn Sàng Giao)</>
                        )}
                      </button>
                    )}

                    {/* Luồng 3: Điều phối Shipper khi READY_FOR_PICKUP */}
                    {order.status === 'READY_FOR_PICKUP' && (
                      <button
                        type="button"
                        className="btn-dispatch btn-queue-action"
                        onClick={() => setAssigningOrder(order)}
                        disabled={isProcessingThis}
                      >
                        {order.shipperName ? '🛵 Đổi Shipper Khác' : '🛵 Gán Shipper Đang Rảnh'}
                      </button>
                    )}

                    {/* Tùy chọn Hủy Đơn nếu đơn chưa giao xong */}
                    {order.status !== 'DELIVERED' &&
                      order.status !== 'CANCELLED' &&
                      order.status !== 'FAILED' && (
                        <button
                          type="button"
                          className="btn-danger-outline"
                          onClick={() => setCancellingOrder(order)}
                          disabled={isProcessingThis}
                          title="Hủy đơn hàng này"
                        >
                          ❌ Hủy Đơn
                        </button>
                      )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* ─── MODAL: GÁN SHIPPER ĐIỀU PHỐI ─── */}
      {assigningOrder && (
        <AssignShipperModal
          order={assigningOrder}
          onClose={() => setAssigningOrder(null)}
          onSuccess={handleAssignSuccess}
        />
      )}

      {/* ─── MODAL: HỦY ĐƠN HÀNG ─── */}
      {cancellingOrder && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3 className="modal-title">❌ Xác Nhận Hủy Đơn Hàng {cancellingOrder.orderCode}</h3>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => {
                  setCancellingOrder(null);
                  setCancelReason('');
                }}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleConfirmCancel}>
              <div className="modal-body">
                <p style={{ color: '#ef4444', marginBottom: '1rem', fontWeight: 500 }}>
                  ⚠️ Bạn đang hủy đơn của khách <strong>{cancellingOrder.receiverName}</strong> (
                  {cancellingOrder.receiverPhone}). Hành động này không thể hoàn tác!
                </p>

                <div className="form-group">
                  <label className="form-label">
                    Lý do hủy đơn hàng <span className="req">*</span>
                  </label>
                  <textarea
                    required
                    rows={3}
                    placeholder="Ví dụ: Khách gọi yêu cầu hủy, hoặc hết nguyên liệu làm bánh..."
                    value={cancelReason}
                    onChange={(e) => setCancelReason(e.target.value)}
                    className="form-input"
                  />
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-outline"
                  onClick={() => {
                    setCancellingOrder(null);
                    setCancelReason('');
                  }}
                  disabled={actionLoadingOrderCode !== null}
                >
                  Đóng
                </button>
                <button
                  type="submit"
                  className="btn-danger"
                  disabled={actionLoadingOrderCode !== null || !cancelReason.trim()}
                >
                  {actionLoadingOrderCode ? 'Đang hủy...' : 'Xác nhận hủy đơn'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </StaffLayout>
  );
};
