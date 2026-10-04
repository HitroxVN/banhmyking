import { playAlertSound } from './alertSound';

/**
 * Kênh đồng bộ thời gian thực đa tab / đa cửa sổ (Cross-tab Realtime Sync)
 * Sử dụng Web BroadcastChannel API kết hợp Web Audio API phát chuông thông báo.
 */

export interface OrderSyncMessage {
  type: 'ORDER_ASSIGNED' | 'ORDER_REJECTED' | 'ORDER_ACCEPTED' | 'ORDER_STATUS_CHANGED' | 'ORDER_REFRESH';
  payload?: any;
  timestamp: number;
}

// Khởi tạo BroadcastChannel nếu trình duyệt hỗ trợ
export const orderSyncChannel: BroadcastChannel | null =
  typeof window !== 'undefined' && 'BroadcastChannel' in window
    ? new BroadcastChannel('banhmyking_order_sync')
    : null;

/**
 * Phát tín hiệu đồng bộ đơn hàng đến tất cả các tab khác (Shipper, Staff, Admin)
 */
export const broadcastOrderChange = (
  type: OrderSyncMessage['type'],
  payload?: any
) => {
  if (!orderSyncChannel) return;
  try {
    orderSyncChannel.postMessage({
      type,
      payload,
      timestamp: Date.now(),
    });
  } catch (err) {
    console.warn('Lỗi khi broadcast tín hiệu đồng bộ đơn hàng:', err);
  }
};

/**
 * Phát chuông báo (Web Audio API, không file ngoài) qua AudioContext dùng chung của alertSound —
 * tôn trọng nút bật/tắt âm báo trong khu vận hành.
 */
export const playNotificationSound = () => {
  playAlertSound();
};
