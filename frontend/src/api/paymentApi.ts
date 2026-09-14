import type { ApiResponse } from '../types/auth';
import type { PaymentResponse, ProcessPaymentRequest } from '../types/payment';
import { axiosClient } from './axiosClient';

export const paymentApi = {
  /**
   * Xem thông tin thanh toán theo mã đơn hàng
   */
  async getPaymentByOrderCode(orderCode: string): Promise<PaymentResponse> {
    const response = await axiosClient.get<ApiResponse<PaymentResponse>>(`/payments/orders/${orderCode}`);
    return response.data.data;
  },

  /**
   * Xem chi tiết thanh toán theo Payment ID
   */
  async getPaymentById(paymentId: number): Promise<PaymentResponse> {
    const response = await axiosClient.get<ApiResponse<PaymentResponse>>(`/payments/${paymentId}`);
    return response.data.data;
  },

  /**
   * Xử lý giao dịch thanh toán cho đơn hàng
   */
  async processPayment(orderCode: string, data: ProcessPaymentRequest): Promise<PaymentResponse> {
    const response = await axiosClient.post<ApiResponse<PaymentResponse>>(
      `/payments/orders/${orderCode}/process`,
      data
    );
    return response.data.data;
  },
};
