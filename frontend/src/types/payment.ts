import type { PaymentMethod, PaymentStatus } from './order';

export interface ProcessPaymentRequest {
  method: PaymentMethod;
  transactionRef?: string;
  simulateFailure?: boolean;
}

export interface PaymentResponse {
  id: number;
  orderId: number;
  orderCode: string;
  method: PaymentMethod;
  status: PaymentStatus;
  amount: number;
  paidAt?: string;
  gatewayTxnId?: string;
  createdAt: string;
  refundAmount?: number;
  refundReason?: string;
  refundedAt?: string;
}

export interface RefundOrderRequest {
  reason: string;
  /** Bỏ trống = hoàn toàn bộ số tiền đã thu. */
  amount?: number;
}
