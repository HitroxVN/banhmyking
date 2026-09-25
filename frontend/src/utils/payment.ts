import type { PaymentMethod, PaymentStatus } from '../types/order';

/** Nhãn hiển thị cho phương thức / trạng thái thanh toán — dùng chung mọi trang vận hành. */
export const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  COD: 'Tiền mặt (COD)',
  BANK_TRANSFER: 'Chuyển khoản',
  E_WALLET: 'Ví điện tử',
};

export const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  PENDING: 'Chưa thanh toán',
  PAID: 'Đã thanh toán',
  FAILED: 'Thanh toán lỗi',
  REFUNDED: 'Đã hoàn tiền',
};

export const PAYMENT_STATUS_TONE: Record<PaymentStatus, 'success' | 'warning' | 'danger' | 'neutral'> = {
  PAID: 'success',
  PENDING: 'warning',
  FAILED: 'danger',
  REFUNDED: 'neutral',
};
