import { describe, expect, it } from 'vitest';
import { countWaitingOrders } from './pendingBell';
import type { OrderResponse } from '../types/order';

const order = (status: string, paymentMethod: string, paymentStatus?: string) =>
  ({ status, paymentMethod, paymentStatus }) as unknown as OrderResponse;

describe('countWaitingOrders', () => {
  it('đếm đơn chờ nhận trả tiền khi nhận hàng (COD)', () => {
    expect(countWaitingOrders([order('PENDING', 'COD', 'PENDING'), order('PENDING', 'COD')])).toBe(2);
  });

  it('bỏ qua đơn chuyển khoản chưa trả tiền — bếp chưa được làm', () => {
    expect(countWaitingOrders([order('PENDING', 'BANK_TRANSFER', 'PENDING')])).toBe(0);
  });

  it('vẫn đếm đơn chờ đã thanh toán', () => {
    expect(countWaitingOrders([order('PENDING', 'BANK_TRANSFER', 'PAID')])).toBe(1);
  });

  it('không đếm đơn đã nhận / đã huỷ', () => {
    expect(countWaitingOrders([order('CONFIRMED', 'COD'), order('PREPARING', 'COD'), order('CANCELLED', 'COD')])).toBe(0);
  });
});
