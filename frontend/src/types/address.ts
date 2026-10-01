export interface AddressResponse {
  id: number;
  userId: number;
  receiverName: string;
  receiverPhone: string;
  fullAddress: string;
  /** Các ô tách + toạ độ ghim trên bản đồ — tuỳ chọn, địa chỉ cũ để trống */
  street?: string | null;
  ward?: string | null;
  province?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  defaultAddress: boolean;
}

/** Body cho POST / PUT /addresses — khớp AddressRequest của backend */
export interface AddressRequest {
  receiverName: string;
  receiverPhone: string;
  fullAddress: string;
  /** Các ô tách + toạ độ ghim trên bản đồ — tuỳ chọn, địa chỉ cũ để trống */
  street?: string | null;
  ward?: string | null;
  province?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  defaultAddress: boolean;
}
