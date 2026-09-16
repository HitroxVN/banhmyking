/**
 * Order TypeScript types matching Spring Boot backend DTOs:
 * - CreateOrderRequest
 * - OrderResponse
 * - OrderItemResponse
 * - OrderItemOptionResponse
 * - OrderStatus, PaymentMethod, PaymentStatus
 */

export type PaymentMethod = 'COD' | 'BANK_TRANSFER' | 'E_WALLET';

export type OrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'PREPARING'
  | 'READY_FOR_PICKUP'
  | 'DELIVERING'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'FAILED';

export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';

export interface CreateOrderRequest {
  addressId?: number | null;
  receiverName?: string;
  receiverPhone?: string;
  shippingAddress?: string;
  promotionCode?: string;
  paymentMethod?: PaymentMethod;
  distanceKm?: number;
  note?: string;
}

export interface OrderItemOptionResponse {
  id: number;
  optionName: string;
  optionPrice: number;
}

export interface OrderItemResponse {
  id: number;
  productId: number;
  productName: string;
  productImageUrl?: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  options: OrderItemOptionResponse[];
}

export interface OrderResponse {
  id: number;
  orderCode: string;
  status: OrderStatus;
  receiverName: string;
  receiverPhone: string;
  shippingAddress: string;
  subtotal: number;
  shippingFee: number;
  discountAmount: number;
  total: number;
  promotionCode?: string;
  paymentMethod: PaymentMethod;
  paymentStatus?: PaymentStatus;
  note?: string;
  cancelReason?: string;
  deliveredAt?: string;
  shipperId?: number;
  shipperName?: string;
  shipperPhone?: string;
  items: OrderItemResponse[];
  createdAt: string;
  updatedAt?: string;
}
