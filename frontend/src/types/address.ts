export interface AddressResponse {
  id: number;
  userId: number;
  receiverName: string;
  receiverPhone: string;
  fullAddress: string;
  defaultAddress: boolean;
}

/** Body cho POST / PUT /addresses — khớp AddressRequest của backend */
export interface AddressRequest {
  receiverName: string;
  receiverPhone: string;
  fullAddress: string;
  defaultAddress: boolean;
}
