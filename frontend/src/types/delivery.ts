/** Khớp DeliveryFeeResult + DeliveryArea của backend */
export type DeliveryArea = 'INNER_CITY' | 'SUBURBAN' | 'OTHER';

export interface DeliveryFeeResult {
  /** Phí phải trả sau khi áp freeship */
  shippingFee: number;
  /** Phí gốc trước khi áp freeship */
  originalFee: number;
  distanceKm?: number;
  area: DeliveryArea;
  freeship: boolean;
  /** Ngưỡng đơn hàng để được miễn phí giao hàng */
  freeshipThreshold: number;
  /** Mô tả cách tính, hiển thị trực tiếp cho khách */
  description: string;
}
