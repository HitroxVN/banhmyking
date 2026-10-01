import { useEffect, useRef } from 'react';
import { orderSyncChannel } from '../utils/orderSyncChannel';

interface PollingOptions {
  /** Chu kỳ hỏi lại server (ms) */
  intervalMs: number;
  /** false = tạm dừng (vd đơn đã kết thúc) */
  enabled?: boolean;
}

/**
 * Gọi lại `refresh` định kỳ để màn hình tự cập nhật khi người khác (staff/shipper) đổi dữ liệu.
 *
 * - Bỏ qua lượt hỏi khi tab đang ẩn (đỡ tốn request), nhưng quay lại tab / focus cửa sổ là tải NGAY,
 *   không bắt người dùng chờ hết chu kỳ.
 * - Cùng trình duyệt: nghe thêm BroadcastChannel đơn hàng để cập nhật tức thì.
 * - `refresh` đọc qua ref nên truyền hàm inline thoải mái, không làm khởi động lại interval.
 */
export const usePolling = (refresh: () => void | Promise<void>, { intervalMs, enabled = true }: PollingOptions) => {
  const refreshRef = useRef(refresh);
  useEffect(() => {
    refreshRef.current = refresh;
  });

  useEffect(() => {
    if (!enabled) return;

    const run = () => {
      void refreshRef.current();
    };
    const tick = () => {
      if (document.visibilityState === 'visible') run();
    };
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') run();
    };

    const intervalId = window.setInterval(tick, intervalMs);
    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('focus', run);
    orderSyncChannel?.addEventListener('message', run);

    return () => {
      window.clearInterval(intervalId);
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('focus', run);
      orderSyncChannel?.removeEventListener('message', run);
    };
  }, [intervalMs, enabled]);
};
