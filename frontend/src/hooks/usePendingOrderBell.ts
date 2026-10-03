import { useCallback, useEffect, useState } from 'react';
import { staffOrderApi } from '../api/staffOrderApi';
import { playAlertSound } from '../utils/alertSound';
import { BELL_INTERVAL_MS, countWaitingOrders } from './pendingBell';
import { useLiveRefresh } from './useLiveRefresh';

/** Đủ phủ hàng chờ của một cơ sở — quán không có tới 50 đơn chờ nhận cùng lúc */
const PAGE_SIZE = 50;

interface PendingOrderBellOptions {
  /** Chỉ bật trong khu bếp (có ô chọn cơ sở) cho staff / manager / admin */
  enabled: boolean;
  storeId: number | null;
}

/**
 * Chuông "ting" kêu LẶP LẠI mỗi 3 giây chừng nào cơ sở đang chọn còn đơn chờ nhận, và tự tắt khi
 * nhân viên đã bấm "Nhận đơn & bắt đầu làm" (hoặc huỷ) hết. Số đơn chờ được tải lại theo tín hiệu
 * realtime, dự phòng 30 giây khi mất luồng. Nút "Âm báo: tắt" vẫn tắt được chuông.
 */
export const usePendingOrderBell = ({ enabled, storeId }: PendingOrderBellOptions) => {
  const [waiting, setWaiting] = useState(0);
  const active = enabled && storeId != null;

  const refresh = useCallback(async () => {
    if (storeId == null) return;
    try {
      const page = await staffOrderApi.getOrders({ status: 'PENDING', storeId, page: 0, size: PAGE_SIZE });
      setWaiting(countWaitingOrders(page.content ?? []));
    } catch {
      // Lỗi mạng: giữ số cũ, lượt tải sau thử lại
    }
  }, [storeId]);

  useEffect(() => {
    if (!active) return;
    // Hỏi lần đầu qua timeout 0 để không setState đồng bộ trong effect
    const initial = window.setTimeout(() => void refresh(), 0);
    return () => window.clearTimeout(initial);
  }, [active, refresh]);

  useLiveRefresh(refresh, {
    enabled: active,
    matchOrder: (signal) => signal.storeId === storeId || signal.previousStoreId === storeId,
  });

  const ringing = active && waiting > 0;
  useEffect(() => {
    if (!ringing) return;
    playAlertSound();
    const intervalId = window.setInterval(playAlertSound, BELL_INTERVAL_MS);
    return () => window.clearInterval(intervalId);
  }, [ringing]);
};
