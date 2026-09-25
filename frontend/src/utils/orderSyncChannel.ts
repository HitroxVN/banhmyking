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
 * Phát chuông báo âm thanh nhẹ nhàng (Web Audio API không phụ thuộc file ngoài)
 * Giúp tài xế/nhân viên nhận biết ngay lập tức khi có đơn mới được gán.
 */
export const playNotificationSound = () => {
  try {
    const AudioContextClass =
      window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextClass) return;

    const ctx = new AudioContextClass();
    const now = ctx.currentTime;

    // Nốt thứ nhất (E5 - 659.25Hz)
    const osc1 = ctx.createOscillator();
    const gain1 = ctx.createGain();
    osc1.type = 'sine';
    osc1.frequency.setValueAtTime(659.25, now);
    gain1.gain.setValueAtTime(0.2, now);
    gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.2);
    osc1.connect(gain1);
    gain1.connect(ctx.destination);
    osc1.start(now);
    osc1.stop(now + 0.2);

    // Nốt thứ hai ngân cao (G5 - 783.99Hz)
    const osc2 = ctx.createOscillator();
    const gain2 = ctx.createGain();
    osc2.type = 'sine';
    osc2.frequency.setValueAtTime(783.99, now + 0.12);
    gain2.gain.setValueAtTime(0.25, now + 0.12);
    gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.38);
    osc2.connect(gain2);
    gain2.connect(ctx.destination);
    osc2.start(now + 0.12);
    osc2.stop(now + 0.38);
  } catch {
    // Trình duyệt có thể hạn chế audio nếu chưa có user gesture, không crash ứng dụng
  }
};
