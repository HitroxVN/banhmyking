import { useEffect, useRef } from 'react';
import { useRealtime } from '../context/useRealtime';
import type { RoleName } from '../types/auth';
import { playAlertSound } from '../utils/alertSound';

interface OrderAlertsOptions {
  role?: RoleName;
  userId?: number;
  /** Cơ sở đang chọn trong khu vận hành; admin chưa chọn = null → không kêu */
  storeId: number | null;
  /**
   * Kêu một tiếng khi có đơn mới. Khu bếp tắt cờ này vì đã có chuông lặp (usePendingOrderBell)
   * kêu tới khi nhân viên nhận đơn — để không kêu chồng hai lớp.
   */
  ringOnNewOrder?: boolean;
}

/**
 * Chuông + nháy tiêu đề tab cho khu vận hành (spec realtime §5.4).
 * - Bếp (staff/manager/admin): kêu một tiếng khi có đơn mới ở cơ sở đang chọn (chỉ khi ringOnNewOrder;
 *   trong khu bếp chuông lặp của usePendingOrderBell lo phần âm thanh).
 * - Shipper: KHÔNG kêu ở đây — ShipperOrdersPage tự kêu khi thấy đơn mới; ở đây chỉ nháy tab.
 */
export const useOrderAlerts = ({ role, userId, storeId, ringOnNewOrder = true }: OrderAlertsOptions) => {
  const baseTitleRef = useRef<string | null>(null);
  const unseenRef = useRef(0);

  useEffect(() => {
    const restoreTitle = () => {
      if (document.visibilityState !== 'visible' || baseTitleRef.current === null) return;
      document.title = baseTitleRef.current;
      baseTitleRef.current = null;
      unseenRef.current = 0;
    };
    document.addEventListener('visibilitychange', restoreTitle);
    return () => {
      document.removeEventListener('visibilitychange', restoreTitle);
      // Đăng xuất khi tiêu đề đang nhấp nháy: trả lại tiêu đề gốc
      if (baseTitleRef.current !== null) document.title = baseTitleRef.current;
    };
  }, []);

  useRealtime((message) => {
    if (message.type !== 'order') return;
    const signal = message.payload;
    const isKitchen = role === 'STAFF' || role === 'MANAGER' || role === 'ADMIN';
    const newOrderHere = isKitchen && storeId != null && signal.storeId === storeId && signal.kind === 'CREATED';
    const assignedToMe =
      role === 'SHIPPER' && signal.kind === 'SHIPPER_ASSIGNED' && userId != null && signal.shipperId === userId;
    if (!newOrderHere && !assignedToMe) return;

    if (newOrderHere && ringOnNewOrder) playAlertSound();
    if (document.visibilityState === 'hidden') {
      if (baseTitleRef.current === null) baseTitleRef.current = document.title;
      unseenRef.current += 1;
      document.title = `(${unseenRef.current}) ${newOrderHere ? 'Đơn mới' : 'Đơn được giao'} — ${baseTitleRef.current}`;
    }
  });
};
