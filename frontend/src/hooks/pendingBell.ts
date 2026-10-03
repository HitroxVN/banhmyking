import type { OrderResponse } from '../types/order';

/** Chuông nhắc lặp lại mỗi ngần này khi còn đơn chờ bếp nhận */
export const BELL_INTERVAL_MS = 3000;

/**
 * Số đơn đang chờ bếp bấm "Nhận đơn": trạng thái PENDING và bếp được phép làm — tiền mặt khi nhận
 * (COD) hoặc đã thanh toán. Đơn chuyển khoản chưa trả tiền không tính, nếu không chuông kêu suốt
 * thời gian khách chờ chuyển khoản.
 */
export const countWaitingOrders = (orders: OrderResponse[]): number =>
  orders.filter(
    (order) => order.status === 'PENDING' && (order.paymentMethod === 'COD' || order.paymentStatus === 'PAID')
  ).length;
