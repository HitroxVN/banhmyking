/** Phần "vị trí" của một địa chỉ — dùng chung cho sổ địa chỉ, thanh toán và vị trí cửa hàng */
export interface AddressLocation {
  street: string;
  ward: string;
  province: string;
  fullAddress: string;
  latitude: number | null;
  longitude: number | null;
}

export const EMPTY_LOCATION: AddressLocation = {
  street: '',
  ward: '',
  province: '',
  fullAddress: '',
  latitude: null,
  longitude: null,
};
