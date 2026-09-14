import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { ShipperLayout } from '../../components/shipper/ShipperLayout';
import { shipperOrderApi, type OrderStatusHistoryItem } from '../../api/shipperOrderApi';
import type { OrderResponse } from '../../types/order';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import {
  orderSyncChannel,
  broadcastOrderChange,
  playNotificationSound,
} from '../../utils/orderSyncChannel';

type TabType = 'ALL' | 'DELIVERING' | 'READY_FOR_PICKUP' | 'DELIVERED' | 'FAILED';

const PRESET_FAILURE_REASONS = [
  'Khách không nhấc máy sau 3 lần gọi',
  'Sai địa chỉ / Không tìm thấy nhà khách',
  'Khách từ chối nhận hàng',
  'Khách hẹn giao lại vào thời gian khác',
  'Không thể liên lạc được với khách hàng',
];

const PRESET_REJECT_REASONS = [
  'Xe gặp sự cố hỏng hóc giữa đường',
  'Khoảng cách giao hàng quá xa khu vực',
  'Đang chở nhiều đơn cồng kềnh, quá tải',
  'Đã hết ca làm việc / có việc bận đột xuất',
  'Thời tiết xấu / mưa ngập không thể di chuyển',
];

export const ShipperOrdersPage: React.FC = () => {
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [activeTab, setActiveTab] = useState<TabType>('DELIVERING');
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Expanded items in cards
  const [expandedOrders, setExpandedOrders] = useState<Record<string, boolean>>({});
  const [startingOrderCode, setStartingOrderCode] = useState<string | null>(null);

  // Modal states
  const [deliveringOrder, setDeliveringOrder] = useState<OrderResponse | null>(null);
  const [deliveryNote, setDeliveryNote] = useState('');
  const [isSubmittingDelivery, setIsSubmittingDelivery] = useState(false);

  const [failingOrder, setFailingOrder] = useState<OrderResponse | null>(null);
  const [failureReason, setFailureReason] = useState('');
  const [isSubmittingFailure, setIsSubmittingFailure] = useState(false);

  // Modal từ chối nhận đơn
  const [rejectingOrder, setRejectingOrder] = useState<OrderResponse | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [isSubmittingReject, setIsSubmittingReject] = useState(false);

  // Theo dõi đơn hàng chờ lấy để phát hiện ngay khi có đơn mới được quán gán
  const isInitialFetchDoneRef = useRef(false);
  const knownReadyOrderCodesRef = useRef<Set<string>>(new Set());
  const [newAssignedNotice, setNewAssignedNotice] = useState<{
    orderCode: string;
    receiverName: string;
    shippingAddress: string;
  } | null>(null);

  // History modal
  const [historyOrder, setHistoryOrder] = useState<OrderResponse | null>(null);
  const [orderHistory, setOrderHistory] = useState<OrderStatusHistoryItem[]>([]);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);

  const showToast = (text: string, type: 'success' | 'error' = 'success') => {
    setToastMessage({ text, type });
    setTimeout(() => {
      setToastMessage(null);
    }, 4000);
  };

  // Tải danh sách đơn hàng được gán cho shipper (tự động đồng bộ thời gian thực)
  const fetchOrders = useCallback(async (isSilent = false) => {
    if (!isSilent) setIsLoading(true);
    setErrorMessage(null);
    try {
      const res = await shipperOrderApi.getAssignedOrders(undefined, 0, 100);
      const fetchedOrders = res.content || [];
      setOrders(fetchedOrders);

      // Phân tích đơn hàng READY_FOR_PICKUP
      const currentReadyOrders = fetchedOrders.filter((o) => o.status === 'READY_FOR_PICKUP');
      const currentReadyCodes = new Set(currentReadyOrders.map((o) => o.orderCode));

      if (isInitialFetchDoneRef.current) {
        // Tìm đơn mới được quán phân công
        const newlyAssigned = currentReadyOrders.filter(
          (o) => !knownReadyOrderCodesRef.current.has(o.orderCode)
        );

        if (newlyAssigned.length > 0) {
          const newest = newlyAssigned[0];
          // 1. Phát chuông âm thanh báo đơn mới
          playNotificationSound();

          // 2. Thông báo Toast nổi bật
          showToast(
            `🔔 CÓ ĐƠN HÀNG MỚI! Quán vừa phân công đơn #${newest.orderCode} cho bạn.`,
            'success'
          );

          // 3. Hiển thị banner cảnh báo đầu trang
          setNewAssignedNotice({
            orderCode: newest.orderCode,
            receiverName: newest.receiverName,
            shippingAddress: newest.shippingAddress,
          });

          // 4. Tự động chuyển tab sang 'READY_FOR_PICKUP' nếu không có đơn nào đang giao
          const deliveringList = fetchedOrders.filter((o) => o.status === 'DELIVERING');
          if (deliveringList.length === 0) {
            setActiveTab('READY_FOR_PICKUP');
          }
        }
      } else {
        isInitialFetchDoneRef.current = true;
        // Lần đầu mở: Nếu chưa có đơn đang giao nhưng có đơn chờ lấy bánh, chuyển ngay sang tab chờ lấy
        const deliveringList = fetchedOrders.filter((o) => o.status === 'DELIVERING');
        if (deliveringList.length === 0 && currentReadyOrders.length > 0) {
          setActiveTab('READY_FOR_PICKUP');
        }
      }

      knownReadyOrderCodesRef.current = currentReadyCodes;
    } catch (err: unknown) {
      const errObj = err as Error;
      setErrorMessage(errObj.message || 'Không thể tải danh sách đơn hàng. Vui lòng thử lại!');
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchOrders();

    // 1. Polling chu kỳ 3 giây siêu nhanh
    const interval = setInterval(() => {
      fetchOrders(true);
    }, 3000);

    // 2. Lắng nghe BroadcastChannel tức thời (0ms) khi có bất kỳ thay đổi nào từ Staff
    const handleBroadcastMessage = () => {
      fetchOrders(true);
    };
    if (orderSyncChannel) {
      orderSyncChannel.addEventListener('message', handleBroadcastMessage);
    }

    // 3. Tự động làm mới ngay khi tài xế quay lại tab trình duyệt
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') {
        fetchOrders(true);
      }
    };
    const handleFocus = () => {
      fetchOrders(true);
    };

    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('focus', handleFocus);

    return () => {
      clearInterval(interval);
      if (orderSyncChannel) {
        orderSyncChannel.removeEventListener('message', handleBroadcastMessage);
      }
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('focus', handleFocus);
    };
  }, [fetchOrders]);

  const handleManualRefresh = () => {
    setIsRefreshing(true);
    fetchOrders(true);
  };

  // Toggle xem món ăn trong thẻ đơn hàng
  const toggleExpand = (orderCode: string) => {
    setExpandedOrders((prev) => ({
      ...prev,
      [orderCode]: !prev[orderCode],
    }));
  };

  // Thống kê nhanh KPI ca làm việc
  const stats = useMemo(() => {
    const deliveringCount = orders.filter((o) => o.status === 'DELIVERING').length;
    const readyCount = orders.filter((o) => o.status === 'READY_FOR_PICKUP').length;
    const deliveredCount = orders.filter((o) => o.status === 'DELIVERED').length;
    const failedCount = orders.filter((o) => o.status === 'FAILED').length;

    // Tổng tiền COD đã thu từ các đơn giao thành công
    const totalCodCollected = orders
      .filter((o) => o.status === 'DELIVERED' && o.paymentMethod === 'COD')
      .reduce((sum, o) => sum + Number(o.total || 0), 0);

    return {
      deliveringCount,
      readyCount,
      deliveredCount,
      failedCount,
      totalCodCollected,
    };
  }, [orders]);

  // Lọc đơn hàng theo Tab và Tìm kiếm
  const filteredOrders = useMemo(() => {
    return orders.filter((order) => {
      // Lọc theo Tab
      if (activeTab === 'DELIVERING' && order.status !== 'DELIVERING') return false;
      if (activeTab === 'READY_FOR_PICKUP' && order.status !== 'READY_FOR_PICKUP') return false;
      if (activeTab === 'DELIVERED' && order.status !== 'DELIVERED') return false;
      if (activeTab === 'FAILED' && order.status !== 'FAILED') return false;

      // Lọc theo tìm kiếm (mã đơn, sđt, tên khách, địa chỉ)
      if (searchQuery.trim()) {
        const query = searchQuery.trim().toLowerCase();
        const codeMatch = order.orderCode.toLowerCase().includes(query);
        const phoneMatch = order.receiverPhone?.toLowerCase().includes(query);
        const nameMatch = order.receiverName?.toLowerCase().includes(query);
        const addressMatch = order.shippingAddress?.toLowerCase().includes(query);
        return codeMatch || phoneMatch || nameMatch || addressMatch;
      }

      return true;
    });
  }, [orders, activeTab, searchQuery]);

  // Thao tác: Nhận bánh tại quán & Bắt đầu đi giao (READY_FOR_PICKUP -> DELIVERING)
  const handleStartDelivering = async (order: OrderResponse) => {
    setStartingOrderCode(order.orderCode);
    try {
      const updated = await shipperOrderApi.startDelivering(order.orderCode);
      broadcastOrderChange('ORDER_ACCEPTED', { orderCode: order.orderCode });
      setOrders((prev) =>
        prev.map((o) => (o.orderCode === updated.orderCode ? updated : o))
      );
      setNewAssignedNotice(null);
      showToast(`Đã nhận đơn #${order.orderCode} tại quán và bắt đầu đi giao! 🛵`, 'success');
    } catch (err: unknown) {
      const errObj = err as Error;
      showToast(errObj.message || 'Không thể bắt đầu đi giao. Vui lòng thử lại!', 'error');
    } finally {
      setStartingOrderCode(null);
    }
  };

  // Thao tác: Mở modal Giao thành công
  const handleOpenDeliverModal = (order: OrderResponse) => {
    setDeliveringOrder(order);
    setDeliveryNote('');
  };

  // Thao tác: Xác nhận Giao thành công
  const handleConfirmDelivery = async () => {
    if (!deliveringOrder) return;
    setIsSubmittingDelivery(true);
    try {
      const updated = await shipperOrderApi.confirmDelivery(
        deliveringOrder.orderCode,
        deliveryNote
      );
      broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode: deliveringOrder.orderCode });
      setOrders((prev) =>
        prev.map((o) => (o.orderCode === updated.orderCode ? updated : o))
      );
      showToast(`Đã giao thành công đơn hàng #${deliveringOrder.orderCode}! 🎉`, 'success');
      setDeliveringOrder(null);
    } catch (err: unknown) {
      const errObj = err as Error;
      showToast(errObj.message || 'Xác nhận giao hàng thất bại. Vui lòng thử lại!', 'error');
    } finally {
      setIsSubmittingDelivery(false);
    }
  };

  // Thao tác: Mở modal Báo giao thất bại
  const handleOpenFailModal = (order: OrderResponse) => {
    setFailingOrder(order);
    setFailureReason('');
  };

  // Thao tác: Xác nhận Báo giao thất bại
  const handleConfirmFail = async () => {
    if (!failingOrder) return;
    if (!failureReason.trim()) {
      showToast('Vui lòng chọn hoặc nhập lý do giao hàng thất bại!', 'error');
      return;
    }
    setIsSubmittingFailure(true);
    try {
      const updated = await shipperOrderApi.failDelivery(
        failingOrder.orderCode,
        failureReason.trim()
      );
      broadcastOrderChange('ORDER_STATUS_CHANGED', { orderCode: failingOrder.orderCode });
      setOrders((prev) =>
        prev.map((o) => (o.orderCode === updated.orderCode ? updated : o))
      );
      showToast(`Đã ghi nhận giao thất bại đơn hàng #${failingOrder.orderCode}`, 'success');
      setFailingOrder(null);
    } catch (err: unknown) {
      const errObj = err as Error;
      showToast(errObj.message || 'Không thể cập nhật thất bại. Vui lòng thử lại!', 'error');
    } finally {
      setIsSubmittingFailure(false);
    }
  };

  // Thao tác: Mở modal Từ chối nhận đơn
  const handleOpenRejectModal = (order: OrderResponse) => {
    setRejectingOrder(order);
    setRejectReason('');
  };

  // Thao tác: Xác nhận Từ chối nhận đơn
  const handleConfirmReject = async () => {
    if (!rejectingOrder) return;
    if (!rejectReason.trim()) {
      showToast('Vui lòng chọn hoặc nhập lý do từ chối nhận đơn!', 'error');
      return;
    }
    setIsSubmittingReject(true);
    try {
      await shipperOrderApi.rejectOrder(
        rejectingOrder.orderCode,
        rejectReason.trim()
      );
      broadcastOrderChange('ORDER_REJECTED', { orderCode: rejectingOrder.orderCode });
      // Gỡ đơn khỏi danh sách local của tài xế
      setOrders((prev) => prev.filter((o) => o.orderCode !== rejectingOrder.orderCode));
      setNewAssignedNotice(null);
      showToast(
        `Đã từ chối đơn hàng #${rejectingOrder.orderCode}. Đơn được chuyển về quán để điều phối shipper khác.`,
        'success'
      );
      setRejectingOrder(null);
    } catch (err: unknown) {
      const errObj = err as Error;
      showToast(errObj.message || 'Không thể từ chối đơn hàng. Vui lòng thử lại!', 'error');
    } finally {
      setIsSubmittingReject(false);
    }
  };

  // Xem lịch sử đơn hàng
  const handleViewHistory = async (order: OrderResponse) => {
    setHistoryOrder(order);
    setOrderHistory([]);
    setIsLoadingHistory(true);
    try {
      const history = await shipperOrderApi.getOrderHistory(order.orderCode);
      setOrderHistory(history);
    } catch (err: unknown) {
      const errObj = err as Error;
      showToast(errObj.message || 'Không thể tải lịch sử đơn hàng', 'error');
    } finally {
      setIsLoadingHistory(false);
    }
  };

  return (
    <ShipperLayout
      title="Bảng Điều Phối Giao Hàng (Shipper Portal)"
      subtitle="Theo dõi đơn hàng được phân công, thực hiện dẫn đường, liên lạc khách và cập nhật tiến trình"
      onRefresh={handleManualRefresh}
      isRefreshing={isRefreshing}
    >
      {/* Toast Notification */}
      {toastMessage && (
        <div className={`shipper-web-toast toast-${toastMessage.type}`} role="status">
          <span className="toast-icon">
            {toastMessage.type === 'success' ? '✅' : '⚠️'}
          </span>
          <span className="toast-text">{toastMessage.text}</span>
        </div>
      )}

      {/* Floating Notice: Cảnh báo có đơn mới được phân công */}
      {newAssignedNotice && (
        <div className="shipper-new-order-alert-banner" role="alert">
          <div className="alert-content-left">
            <span className="bell-pulse-icon">🔔</span>
            <div className="alert-text-meta">
              <strong>CÓ ĐƠN HÀNG MỚI ĐƯỢC PHÂN CÔNG!</strong>
              <p>
                Đơn <strong>#{newAssignedNotice.orderCode}</strong> giao tới {newAssignedNotice.shippingAddress} (Khách: {newAssignedNotice.receiverName}) đang chờ bạn phản hồi.
              </p>
            </div>
          </div>
          <div className="alert-actions-right">
            <button
              type="button"
              className="btn-alert-view-now"
              onClick={() => {
                setActiveTab('READY_FOR_PICKUP');
                setNewAssignedNotice(null);
              }}
            >
              Xem & Nhận Đơn Ngay ⚡
            </button>
            <button
              type="button"
              className="btn-alert-dismiss"
              onClick={() => setNewAssignedNotice(null)}
              title="Đóng thông báo"
            >
              ✕
            </button>
          </div>
        </div>
      )}

      {/* KPI Metric Cards Row */}
      <section className="shipper-web-kpi-grid" aria-label="Thống kê ca làm việc">
        <div className="shipper-web-kpi-card card-delivering">
          <div className="kpi-icon-box">🛵</div>
          <div className="kpi-info-box">
            <span className="kpi-label">Đang Giao Trên Đường</span>
            <span className="kpi-val">{stats.deliveringCount}</span>
            <span className="kpi-subtext">Cần hoàn thành giao khách</span>
          </div>
        </div>

        <div className="shipper-web-kpi-card card-pickup">
          <div className="kpi-icon-box">🏪</div>
          <div className="kpi-info-box">
            <span className="kpi-label">Chờ Lấy Tại Quán</span>
            <span className="kpi-val">{stats.readyCount}</span>
            <span className="kpi-subtext">Bánh đã sẵn sàng giao</span>
          </div>
        </div>

        <div className="shipper-web-kpi-card card-delivered">
          <div className="kpi-icon-box">✅</div>
          <div className="kpi-info-box">
            <span className="kpi-label">Giao Thành Công</span>
            <span className="kpi-val">{stats.deliveredCount}</span>
            <span className="kpi-subtext">Đơn đã hoàn tất</span>
          </div>
        </div>

        <div className="shipper-web-kpi-card card-cod">
          <div className="kpi-icon-box">💰</div>
          <div className="kpi-info-box">
            <span className="kpi-label">Tổng Tiền COD Đã Thu</span>
            <span className="kpi-val cod-amount">{formatCurrency(stats.totalCodCollected)}</span>
            <span className="kpi-subtext">Tiền mặt cần nộp lại</span>
          </div>
        </div>
      </section>

      {/* Control Bar: Filter Tabs & Search Box */}
      <div className="shipper-web-control-bar">
        <nav className="shipper-web-tabs" aria-label="Bộ lọc đơn hàng">
          <button
            type="button"
            className={`shipper-web-tab-btn ${activeTab === 'DELIVERING' ? 'active' : ''}`}
            onClick={() => setActiveTab('DELIVERING')}
          >
            <span>🛵 Đang giao</span>
            {stats.deliveringCount > 0 && (
              <span className="tab-badge primary">{stats.deliveringCount}</span>
            )}
          </button>

          <button
            type="button"
            className={`shipper-web-tab-btn ${activeTab === 'READY_FOR_PICKUP' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('READY_FOR_PICKUP');
              setNewAssignedNotice(null);
            }}
          >
            <span>🏪 Chờ lấy bánh</span>
            {stats.readyCount > 0 && (
              <span className="tab-badge warning pulse-badge">{stats.readyCount} mới</span>
            )}
          </button>

          <button
            type="button"
            className={`shipper-web-tab-btn ${activeTab === 'ALL' ? 'active' : ''}`}
            onClick={() => setActiveTab('ALL')}
          >
            <span>📦 Tất cả đơn</span>
            <span className="tab-badge neutral">{orders.length}</span>
          </button>

          <button
            type="button"
            className={`shipper-web-tab-btn ${activeTab === 'DELIVERED' ? 'active' : ''}`}
            onClick={() => setActiveTab('DELIVERED')}
          >
            <span>✅ Đã giao thành công</span>
            <span className="tab-badge success">{stats.deliveredCount}</span>
          </button>

          <button
            type="button"
            className={`shipper-web-tab-btn ${activeTab === 'FAILED' ? 'active' : ''}`}
            onClick={() => setActiveTab('FAILED')}
          >
            <span>❌ Giao thất bại</span>
            {stats.failedCount > 0 && (
              <span className="tab-badge danger">{stats.failedCount}</span>
            )}
          </button>
        </nav>

        {/* Search Box */}
        <div className="shipper-web-search-box">
          <span className="search-icon">🔍</span>
          <input
            id="shipper-web-search-input"
            type="text"
            className="search-input"
            placeholder="Tìm theo mã đơn, SĐT khách, tên hoặc địa chỉ giao..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          {searchQuery && (
            <button
              type="button"
              className="search-clear-btn"
              onClick={() => setSearchQuery('')}
              aria-label="Xóa từ khóa tìm kiếm"
            >
              ✕
            </button>
          )}
        </div>
      </div>

      {/* Main Orders Display Area */}
      <div className="shipper-web-orders-wrapper">
        {/* Error Alert */}
        {errorMessage && (
          <div className="shipper-web-error-card">
            <span className="error-icon">⚠️</span>
            <div className="error-content">
              <strong>Có lỗi xảy ra khi tải dữ liệu</strong>
              <p>{errorMessage}</p>
              <button
                type="button"
                className="btn-retry"
                onClick={() => fetchOrders(false)}
              >
                Thử lại ngay
              </button>
            </div>
          </div>
        )}

        {/* Loading Spinner */}
        {isLoading ? (
          <div className="shipper-web-loading-state">
            <div className="spinner-royal"></div>
            <p className="loading-text">Đang đồng bộ danh sách đơn hàng...</p>
          </div>
        ) : filteredOrders.length === 0 ? (
          /* Empty State */
          <div className="shipper-web-empty-state">
            <div className="empty-icon-circle">
              {activeTab === 'DELIVERING'
                ? '🛵'
                : activeTab === 'READY_FOR_PICKUP'
                ? '🏪'
                : activeTab === 'DELIVERED'
                ? '🎉'
                : activeTab === 'FAILED'
                ? '📦'
                : '📭'}
            </div>
            <h3 className="empty-title">
              {activeTab === 'DELIVERING'
                ? 'Không có đơn nào đang trên đường giao'
                : activeTab === 'READY_FOR_PICKUP'
                ? 'Không có đơn nào chờ lấy bánh tại quán'
                : activeTab === 'DELIVERED'
                ? 'Chưa có đơn giao thành công nào'
                : activeTab === 'FAILED'
                ? 'Không có đơn giao thất bại nào'
                : 'Không tìm thấy đơn hàng phù hợp'}
            </h3>
            <p className="empty-desc">
              {activeTab === 'DELIVERING'
                ? 'Các đơn hàng đã nhận sẽ hiển thị ở đây để bạn cập nhật trạng thái khi giao tới khách.'
                : 'Nhấn nút "Làm mới" góc phải để cập nhật dữ liệu thời gian thực từ cửa hàng.'}
            </p>
            <button
              type="button"
              className="btn-primary"
              onClick={handleManualRefresh}
            >
              🔄 Tải lại dữ liệu
            </button>
          </div>
        ) : (
          /* Web Orders Grid */
          <div className="shipper-web-cards-grid">
            {filteredOrders.map((order) => {
              const isCOD = order.paymentMethod === 'COD';
              const isPaid = order.paymentStatus === 'PAID';
              const isDelivering = order.status === 'DELIVERING';
              const isReady = order.status === 'READY_FOR_PICKUP';
              const isExpanded = !!expandedOrders[order.orderCode];

              return (
                <article
                  key={order.orderCode}
                  className={`shipper-web-order-card ${
                    isDelivering ? 'status-active-delivering' : isReady ? 'status-active-pickup' : ''
                  }`}
                  id={`web-order-card-${order.orderCode}`}
                >
                  {/* Card Header */}
                  <div className="web-card-header">
                    <div className="header-left-info">
                      <span className="order-badge-tag">MÃ ĐƠN HÀNG</span>
                      <strong className="order-code-title">#{order.orderCode}</strong>
                      <span className="order-time-text">
                        🕒 {formatDateTime(order.createdAt)}
                      </span>
                    </div>

                    <div className="header-right-status">
                      {order.status === 'READY_FOR_PICKUP' && (
                        <span className="status-badge badge-pickup">
                          🏪 Chờ lấy bánh
                        </span>
                      )}
                      {order.status === 'DELIVERING' && (
                        <span className="status-badge badge-delivering">
                          <span className="pulse-dot"></span> Đang giao hàng
                        </span>
                      )}
                      {order.status === 'DELIVERED' && (
                        <span className="status-badge badge-delivered">
                          ✅ Giao thành công
                        </span>
                      )}
                      {order.status === 'FAILED' && (
                        <span className="status-badge badge-failed">
                          ❌ Giao thất bại
                        </span>
                      )}
                      {order.status !== 'READY_FOR_PICKUP' &&
                        order.status !== 'DELIVERING' &&
                        order.status !== 'DELIVERED' &&
                        order.status !== 'FAILED' && (
                          <span className="status-badge badge-neutral">
                            {order.status}
                          </span>
                        )}

                      <button
                        type="button"
                        className="btn-view-history"
                        onClick={() => handleViewHistory(order)}
                        title="Xem lịch sử thay đổi trạng thái"
                      >
                        📜 Lịch sử
                      </button>
                    </div>
                  </div>

                  {/* Customer Information Block */}
                  <div className="web-customer-block">
                    <div className="customer-info-row">
                      <div className="customer-name-group">
                        <span className="avatar-chip">👤</span>
                        <div className="customer-text-meta">
                          <span className="meta-label">Người nhận hàng</span>
                          <strong className="meta-name">{order.receiverName}</strong>
                        </div>
                      </div>

                      {/* Direct Phone Call Button */}
                      <a
                        href={`tel:${order.receiverPhone}`}
                        id={`btn-call-web-${order.orderCode}`}
                        className="btn-action-call"
                        title="Gọi điện trực tiếp cho khách hàng"
                      >
                        <span className="icon">📞</span>
                        <span className="phone-num">{order.receiverPhone}</span>
                        <span className="call-hint">(Gọi ngay)</span>
                      </a>
                    </div>

                    {/* Delivery Address Row */}
                    <div className="address-info-row">
                      <div className="address-text-group">
                        <span className="pin-icon">📍</span>
                        <div className="address-text-meta">
                          <span className="meta-label">Địa chỉ giao tận nơi</span>
                          <span className="meta-address">{order.shippingAddress}</span>
                        </div>
                      </div>

                      {/* Google Maps Link */}
                      <a
                        href={`https://maps.google.com/?q=${encodeURIComponent(
                          order.shippingAddress
                        )}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        id={`btn-map-web-${order.orderCode}`}
                        className="btn-action-map"
                        title="Mở Google Maps dẫn đường"
                      >
                        <span className="icon">🗺️</span>
                        <span>Mở Bản Đồ Chỉ Đường</span>
                      </a>
                    </div>

                    {/* Customer Note (if any) */}
                    {order.note && (
                      <div className="customer-note-alert">
                        <span className="note-icon">📝</span>
                        <div className="note-body">
                          <strong>Ghi chú của khách:</strong> {order.note}
                        </div>
                      </div>
                    )}
                  </div>

                  {/* COD & Payment Prominent Box */}
                  <div
                    className={`web-payment-highlight-box ${
                      isCOD && !isPaid ? 'box-cod-pending' : 'box-prepaid'
                    }`}
                  >
                    <div className="box-top-row">
                      <span className="box-header-title">
                        {isCOD && !isPaid
                          ? '💵 SỐ TIỀN CẦN THU (COD):'
                          : isPaid
                          ? '✅ ĐÃ THANH TOÁN TRỰC TUYẾN:'
                          : '💳 PHƯƠNG THỨC THANH TOÁN:'}
                      </span>
                      <span className="payment-method-tag">
                        {order.paymentMethod === 'COD'
                          ? 'Tiền mặt khi nhận hàng (COD)'
                          : order.paymentMethod === 'BANK_TRANSFER'
                          ? 'Chuyển khoản ngân hàng'
                          : 'Ví điện tử'}
                      </span>
                    </div>

                    <div className="box-amount-row">
                      {isCOD && !isPaid ? (
                        <>
                          <span className="amount-val-cod">
                            {formatCurrency(order.total)}
                          </span>
                          <span className="amount-desc-warning">
                            ⚠️ Tài xế cần thu đủ số tiền mặt này từ khách trước khi bàn giao bánh.
                          </span>
                        </>
                      ) : (
                        <>
                          <span className="amount-val-zero">0 đ CẦN THU</span>
                          <span className="amount-desc-success">
                            Khách đã thanh toán trước. Tuyệt đối không thu thêm bất kỳ chi phí nào.
                          </span>
                        </>
                      )}
                    </div>
                  </div>

                  {/* Food Items Preview / Collapsible */}
                  <div className="web-items-section">
                    <button
                      type="button"
                      className="btn-toggle-items"
                      onClick={() => toggleExpand(order.orderCode)}
                      aria-expanded={isExpanded}
                    >
                      <span className="label">
                        🥖 Danh sách món ăn ({order.items?.length || 0} món)
                      </span>
                      <span className="chevron">
                        {isExpanded ? '▲ Thu gọn chi tiết' : '▼ Xem chi tiết topping & món'}
                      </span>
                    </button>

                    {isExpanded && (
                      <div className="items-table-wrapper">
                        <table className="web-items-table">
                          <thead>
                            <tr>
                              <th>Tên món & Tùy chọn</th>
                              <th className="text-center">Số lượng</th>
                              <th className="text-right">Thành tiền</th>
                            </tr>
                          </thead>
                          <tbody>
                            {order.items?.map((item, idx) => (
                              <tr key={item.id || idx}>
                                <td>
                                  <strong className="item-name">{item.productName}</strong>
                                  {item.options && item.options.length > 0 && (
                                    <div className="item-options-subtext">
                                      + {item.options.map((opt) => opt.optionName).join(', ')}
                                    </div>
                                  )}
                                </td>
                                <td className="text-center font-bold">x{item.quantity}</td>
                                <td className="text-right font-bold text-stone-700">
                                  {formatCurrency(item.lineTotal)}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>

                  {/* Result Box if DELIVERED or FAILED */}
                  {order.status === 'DELIVERED' && (
                    <div className="web-delivered-note">
                      <span>✅ Đã giao thành công lúc: <strong>{formatDateTime(order.deliveredAt || order.updatedAt)}</strong></span>
                    </div>
                  )}

                  {order.status === 'FAILED' && (
                    <div className="web-failed-note">
                      <span className="icon">⚠️</span>
                      <div className="text">
                        <strong>Lý do giao hàng thất bại:</strong>
                        <p>{order.cancelReason || 'Không có ghi chú cụ thể'}</p>
                      </div>
                    </div>
                  )}

                  {/* Action Footer */}
                  {isReady && (
                    <div className="ready-order-dispatch-section">
                      <div className="ready-order-prompt-box">
                        <span className="prompt-icon">⚡</span>
                        <div className="prompt-content">
                          <strong>Đơn hàng được quán phân công cho bạn:</strong>
                          <p>Vui lòng xác nhận nhận đơn để đến quán lấy bánh đi giao, hoặc bấm từ chối nếu không thể nhận đơn.</p>
                        </div>
                      </div>

                      <div className="web-card-actions dual-actions">
                        <button
                          type="button"
                          id={`btn-reject-web-${order.orderCode}`}
                          className="btn-web-action-reject"
                          onClick={() => handleOpenRejectModal(order)}
                          disabled={startingOrderCode === order.orderCode}
                        >
                          ❌ Từ Chối Đơn
                        </button>

                        <button
                          type="button"
                          id={`btn-pickup-web-${order.orderCode}`}
                          className="btn-web-action-accept"
                          onClick={() => handleStartDelivering(order)}
                          disabled={startingOrderCode === order.orderCode}
                        >
                          {startingOrderCode === order.orderCode ? (
                            <>
                              <span className="spinner-mini"></span>
                              Đang xử lý nhận đơn...
                            </>
                          ) : (
                            '🛵 Nhận Đơn & Bắt Đầu Giao'
                          )}
                        </button>
                      </div>
                    </div>
                  )}

                  {isDelivering && (
                    <div className="web-card-actions dual-actions">
                      <button
                        type="button"
                        id={`btn-fail-web-${order.orderCode}`}
                        className="btn-web-action-fail"
                        onClick={() => handleOpenFailModal(order)}
                      >
                        ⚠️ Báo Giao Thất Bại
                      </button>

                      <button
                        type="button"
                        id={`btn-deliver-web-${order.orderCode}`}
                        className="btn-web-action-success"
                        onClick={() => handleOpenDeliverModal(order)}
                      >
                        ✅ Xác Nhận Giao Thành Công
                      </button>
                    </div>
                  )}
                </article>
              );
            })}
          </div>
        )}
      </div>

      {/* Modal: Xác nhận Giao thành công */}
      {deliveringOrder && (
        <div className="web-modal-backdrop" role="dialog" aria-modal="true">
          <div className="web-modal-dialog">
            <div className="modal-header-success">
              <div className="modal-title-group">
                <span className="title-icon">🎉</span>
                <h3>Xác Nhận Giao Hàng Thành Công</h3>
              </div>
              <button
                type="button"
                className="modal-btn-close"
                onClick={() => setDeliveringOrder(null)}
                aria-label="Đóng"
              >
                ✕
              </button>
            </div>

            <div className="web-modal-content">
              <div className="modal-order-summary-box">
                <div className="row">
                  <span className="label">Mã đơn hàng:</span>
                  <strong className="val">#{deliveringOrder.orderCode}</strong>
                </div>
                <div className="row">
                  <span className="label">Khách hàng nhận:</span>
                  <strong className="val">{deliveringOrder.receiverName}</strong>
                </div>
                <div className="row">
                  <span className="label">Số điện thoại:</span>
                  <span className="val">{deliveringOrder.receiverPhone}</span>
                </div>
                <div className="row">
                  <span className="label">Địa chỉ giao:</span>
                  <span className="val">{deliveringOrder.shippingAddress}</span>
                </div>
              </div>

              {/* COD Reminder Box */}
              <div
                className={`modal-cod-banner ${
                  deliveringOrder.paymentMethod === 'COD' &&
                  deliveringOrder.paymentStatus !== 'PAID'
                    ? 'banner-cod'
                    : 'banner-prepaid'
                }`}
              >
                {deliveringOrder.paymentMethod === 'COD' &&
                deliveringOrder.paymentStatus !== 'PAID' ? (
                  <>
                    <span className="icon">💵</span>
                    <div className="info">
                      <div className="title">Xác nhận đã thu tiền mặt (COD):</div>
                      <div className="amount">{formatCurrency(deliveringOrder.total)}</div>
                      <p>Vui lòng đảm bảo đã nhận đủ tiền mặt từ khách hàng trước khi bấm hoàn tất.</p>
                    </div>
                  </>
                ) : (
                  <>
                    <span className="icon">✅</span>
                    <div className="info">
                      <div className="title">Đơn hàng đã thanh toán trực tuyến:</div>
                      <div className="amount">0 đ CẦN THU</div>
                      <p>Khách đã thanh toán trước qua cổng ngân hàng/ví điện tử.</p>
                    </div>
                  </>
                )}
              </div>

              {/* Delivery Note */}
              <div className="modal-input-group">
                <label htmlFor="modal-delivery-note" className="input-label">
                  Ghi chú giao hàng (tùy chọn):
                </label>
                <input
                  id="modal-delivery-note"
                  type="text"
                  className="modal-input-text"
                  placeholder="Ví dụ: Giao trực tiếp cho khách, gửi bảo vệ tòa nhà..."
                  value={deliveryNote}
                  onChange={(e) => setDeliveryNote(e.target.value)}
                />
              </div>
            </div>

            <div className="web-modal-footer">
              <button
                type="button"
                className="btn-cancel"
                onClick={() => setDeliveringOrder(null)}
                disabled={isSubmittingDelivery}
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                id="btn-confirm-deliver-web-submit"
                className="btn-submit-success"
                onClick={handleConfirmDelivery}
                disabled={isSubmittingDelivery}
              >
                {isSubmittingDelivery ? (
                  <>
                    <span className="spinner-mini"></span>
                    Đang đồng bộ về hệ thống...
                  </>
                ) : (
                  '✅ Hoàn Tất Đơn Hàng'
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Báo Giao hàng Thất bại */}
      {failingOrder && (
        <div className="web-modal-backdrop" role="dialog" aria-modal="true">
          <div className="web-modal-dialog">
            <div className="modal-header-danger">
              <div className="modal-title-group">
                <span className="title-icon">⚠️</span>
                <h3>Báo Cáo Giao Hàng Thất Bại</h3>
              </div>
              <button
                type="button"
                className="modal-btn-close"
                onClick={() => setFailingOrder(null)}
                aria-label="Đóng"
              >
                ✕
              </button>
            </div>

            <div className="web-modal-content">
              <div className="modal-danger-alert">
                Đơn hàng <strong>#{failingOrder.orderCode}</strong> sẽ chuyển sang trạng thái
                <strong> THẤT BẠI (FAILED)</strong> và thông báo ngay về cho nhân viên quản lý quán.
              </div>

              <div className="modal-input-group">
                <label className="input-label">Chọn nhanh lý do không giao được:</label>
                <div className="preset-chips-flex">
                  {PRESET_FAILURE_REASONS.map((preset) => (
                    <button
                      key={preset}
                      type="button"
                      className={`preset-chip-btn ${
                        failureReason === preset ? 'active' : ''
                      }`}
                      onClick={() => setFailureReason(preset)}
                    >
                      {preset}
                    </button>
                  ))}
                </div>
              </div>

              <div className="modal-input-group">
                <label htmlFor="modal-failure-reason" className="input-label">
                  Chi tiết lý do thất bại: <span className="text-red-500">*</span>
                </label>
                <textarea
                  id="modal-failure-reason"
                  rows={3}
                  className="modal-textarea"
                  placeholder="Nhập chi tiết cụ thể (vd: Đã gọi điện 3 lần nhưng khách thuê bao, bảo vệ không cho gửi...)"
                  value={failureReason}
                  onChange={(e) => setFailureReason(e.target.value)}
                />
              </div>
            </div>

            <div className="web-modal-footer">
              <button
                type="button"
                className="btn-cancel"
                onClick={() => setFailingOrder(null)}
                disabled={isSubmittingFailure}
              >
                Quay lại
              </button>
              <button
                type="button"
                id="btn-confirm-fail-web-submit"
                className="btn-submit-danger"
                onClick={handleConfirmFail}
                disabled={isSubmittingFailure || !failureReason.trim()}
              >
                {isSubmittingFailure ? (
                  <>
                    <span className="spinner-mini"></span>
                    Đang lưu trạng thái...
                  </>
                ) : (
                  'Xác Nhận Thất Bại'
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Từ Chối Nhận Đơn Hàng */}
      {rejectingOrder && (
        <div className="web-modal-backdrop" role="dialog" aria-modal="true">
          <div className="web-modal-dialog">
            <div className="modal-header-warning">
              <div className="modal-title-group">
                <span className="title-icon">⚠️</span>
                <h3>Từ Chối Nhận Đơn Hàng</h3>
              </div>
              <button
                type="button"
                className="modal-btn-close"
                onClick={() => setRejectingOrder(null)}
                aria-label="Đóng"
              >
                ✕
              </button>
            </div>

            <div className="web-modal-content">
              <div className="modal-warning-alert">
                Bạn đang từ chối nhận đơn hàng <strong>#{rejectingOrder.orderCode}</strong>.
                Đơn hàng sẽ được gỡ gán và hoàn về cho nhân viên quán điều phối lại cho tài xế khác.
              </div>

              <div className="modal-order-summary-box">
                <div className="row">
                  <span className="label">Mã đơn hàng:</span>
                  <strong className="val">#{rejectingOrder.orderCode}</strong>
                </div>
                <div className="row">
                  <span className="label">Khách nhận:</span>
                  <strong className="val">{rejectingOrder.receiverName} ({rejectingOrder.receiverPhone})</strong>
                </div>
                <div className="row">
                  <span className="label">Địa chỉ giao:</span>
                  <span className="val">{rejectingOrder.shippingAddress}</span>
                </div>
                <div className="row">
                  <span className="label">Tổng tiền COD:</span>
                  <strong className="val text-amber-600 font-bold">
                    {rejectingOrder.paymentMethod === 'COD' ? formatCurrency(rejectingOrder.total) : 'Đã thanh toán online'}
                  </strong>
                </div>
              </div>

              <div className="modal-input-group">
                <label className="input-label">Chọn nhanh lý do từ chối:</label>
                <div className="preset-chips-flex">
                  {PRESET_REJECT_REASONS.map((preset) => (
                    <button
                      key={preset}
                      type="button"
                      className={`preset-chip-btn ${
                        rejectReason === preset ? 'active' : ''
                      }`}
                      onClick={() => setRejectReason(preset)}
                    >
                      {preset}
                    </button>
                  ))}
                </div>
              </div>

              <div className="modal-input-group">
                <label htmlFor="modal-reject-reason" className="input-label">
                  Chi tiết lý do từ chối: <span className="text-red-500">*</span>
                </label>
                <textarea
                  id="modal-reject-reason"
                  rows={3}
                  className="modal-textarea"
                  placeholder="Nhập chi tiết lý do (vd: Xe bị thủng xăm đang sửa, kẹt mưa bão không đi được, hết ca làm...)"
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                />
              </div>
            </div>

            <div className="web-modal-footer">
              <button
                type="button"
                className="btn-cancel"
                onClick={() => setRejectingOrder(null)}
                disabled={isSubmittingReject}
              >
                Quay lại
              </button>
              <button
                type="button"
                id="btn-confirm-reject-web-submit"
                className="btn-submit-danger"
                onClick={handleConfirmReject}
                disabled={isSubmittingReject || !rejectReason.trim()}
              >
                {isSubmittingReject ? (
                  <>
                    <span className="spinner-mini"></span>
                    Đang xử lý...
                  </>
                ) : (
                  'Xác Nhận Từ Chối Đơn'
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Lịch sử đơn hàng */}
      {historyOrder && (
        <div className="web-modal-backdrop" role="dialog" aria-modal="true">
          <div className="web-modal-dialog">
            <div className="modal-header-neutral">
              <div className="modal-title-group">
                <span className="title-icon">📜</span>
                <h3>Lịch Sử Đơn Hàng #{historyOrder.orderCode}</h3>
              </div>
              <button
                type="button"
                className="modal-btn-close"
                onClick={() => setHistoryOrder(null)}
                aria-label="Đóng"
              >
                ✕
              </button>
            </div>

            <div className="web-modal-content">
              {isLoadingHistory ? (
                <div className="modal-loading-box">
                  <div className="spinner-royal"></div>
                  <p>Đang tải tiến trình chuyển trạng thái...</p>
                </div>
              ) : orderHistory.length === 0 ? (
                <p className="no-history-text">Chưa có lịch sử trạng thái cho đơn hàng này.</p>
              ) : (
                <div className="web-timeline-list">
                  {orderHistory.map((item, idx) => (
                    <div key={item.id || idx} className="timeline-item">
                      <div className="timeline-dot"></div>
                      <div className="timeline-box">
                        <div className="top-row">
                          <span className="status-chip">{item.toStatus}</span>
                          <span className="time">{formatDateTime(item.createdAt)}</span>
                        </div>
                        {item.changedByName && (
                          <div className="actor">
                            Thực hiện bởi: <strong>{item.changedByName}</strong> ({item.changedByRole})
                          </div>
                        )}
                        {item.note && <div className="note">{item.note}</div>}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="web-modal-footer">
              <button
                type="button"
                className="btn-cancel full-width"
                onClick={() => setHistoryOrder(null)}
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </ShipperLayout>
  );
};
