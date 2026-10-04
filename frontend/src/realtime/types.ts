import type { OrderStatus } from '../types/order';

export type RealtimeStatus = 'live' | 'reconnecting' | 'offline';

export type OrderChangeKind = 'CREATED' | 'STATUS_CHANGED' | 'SHIPPER_ASSIGNED' | 'STORE_TRANSFERRED' | 'UPDATED';

/** Tín hiệu "đơn vừa đổi" — trang tự gọi lại API để lấy dữ liệu thật */
export interface OrderSignal {
  orderCode: string;
  status: OrderStatus;
  kind: OrderChangeKind;
  storeId: number | null;
  shipperId: number | null;
  /** Cơ sở cũ khi đơn bị chuyển cơ sở — để hàng đợi cơ sở cũ biết mà tải lại. */
  previousStoreId: number | null;
}

export interface InboxSignal {
  type: 'FEEDBACK' | 'JOB_APPLICATION';
}

/** `resync` không đến từ server: provider phát khi nối lại được, để trang tải bù những gì đã lỡ */
export type RealtimeMessage =
  | { type: 'ready' }
  | { type: 'resync' }
  | { type: 'order'; payload: OrderSignal }
  | { type: 'inbox'; payload: InboxSignal };
