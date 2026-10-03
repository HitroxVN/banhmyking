import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { useToast } from '../components/ui';
import { useAuth } from '../context/useAuth';
import { useRealtime } from '../context/useRealtime';
import type { OrderStatus } from '../types/order';

const MESSAGE: Partial<Record<OrderStatus, (code: string) => string>> = {
  CONFIRMED: (code) => `Lò đã nhận đơn ${code} của bạn!`,
  PREPARING: () => 'Bếp đang làm bánh của bạn 🔥',
  READY_FOR_PICKUP: () => 'Bánh xong rồi, đang chờ shipper tới lấy',
  DELIVERING: () => 'Shipper đang trên đường tới 🛵',
  DELIVERED: () => 'Giao xong, chúc bạn ngon miệng!',
  CANCELLED: (code) => `Đơn ${code} đã bị huỷ`,
  FAILED: (code) => `Đơn ${code} giao không thành công`,
};

/**
 * Báo khách khi đơn đổi trạng thái, ở bất kỳ trang nào của khu khách (spec realtime §5.5).
 * Chỉ cho vai trò CUSTOMER (admin/staff xem trang khách nhận tin của mọi đơn). Bỏ qua khi đang
 * mở trang theo dõi của chính đơn đó — trang này tự báo và là nơi duy nhất khách tự huỷ đơn.
 */
export const useOrderStatusToasts = () => {
  const toast = useToast();
  const { user } = useAuth();
  const { pathname } = useLocation();
  const pathRef = useRef(pathname);
  useEffect(() => {
    pathRef.current = pathname;
  }, [pathname]);

  const isCustomer = user?.role === 'CUSTOMER';

  useRealtime((message) => {
    if (!isCustomer || message.type !== 'order' || message.payload.kind !== 'STATUS_CHANGED') return;
    const { orderCode, status } = message.payload;
    if (pathRef.current === `/orders/${orderCode}`) return;
    const text = MESSAGE[status]?.(orderCode);
    if (!text) return;
    if (status === 'CANCELLED' || status === 'FAILED') toast.error(text);
    else toast.success(text);
  });
};
