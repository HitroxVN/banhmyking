import type { OrderStatus } from '../types/order';

/** Nhãn tiếng Việt duy nhất cho mọi trạng thái đơn — dùng chung mọi trang. */
export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  PREPARING: 'Đang chế biến',
  READY_FOR_PICKUP: 'Chờ lấy hàng',
  DELIVERING: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã huỷ',
  FAILED: 'Giao thất bại',
};

export const ORDER_STATUS_OPTIONS = (Object.keys(ORDER_STATUS_LABEL) as OrderStatus[]).map(
  (status) => ({ value: status, label: ORDER_STATUS_LABEL[status] })
);
