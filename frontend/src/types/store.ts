/** Khớp DTO cơ sở ở backend (dto/store/*) */
export interface PublicStore {
  id: number;
  code: string;
  name: string;
  address: string;
  phone?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  /** HH:mm */
  openTime: string;
  closeTime: string;
  openNow: boolean;
  acceptingOrders: boolean;
}

export interface Store extends PublicStore {
  deliveryRadiusKm: number;
  freeShipRadiusKm: number;
  minOrderAmount: number;
  active: boolean;
  staffCount: number;
}

export interface StorePayload {
  code: string;
  name: string;
  address: string;
  phone?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  openTime: string;
  closeTime: string;
  deliveryRadiusKm: number;
  freeShipRadiusKm: number;
  minOrderAmount: number;
  active: boolean;
}

export type StoreQuoteReason = 'CLOSED' | 'NOT_ACCEPTING' | 'OUT_OF_RADIUS' | 'BELOW_MIN_ORDER' | 'ITEM_UNAVAILABLE';

export interface StoreQuoteOption {
  storeId: number;
  storeCode: string;
  storeName: string;
  storeAddress: string;
  storePhone?: string | null;
  openTime: string;
  closeTime: string;
  minOrderAmount: number;
  distanceKm?: number | null;
  shippingFee: number;
  originalFee: number;
  freeship: boolean;
  feeDescription: string;
  eligible: boolean;
  reasons: StoreQuoteReason[];
  /** Câu giải thích tiếng Việt, cùng thứ tự với reasons */
  reasonMessages: string[];
  unavailableItems: string[];
}

export interface DeliveryQuote {
  recommendedStoreId: number | null;
  options: StoreQuoteOption[];
}

export interface StoreStockItem {
  productId: number;
  productName: string;
  categoryName?: string | null;
  imageUrl?: string | null;
  price: number;
  onChainMenu: boolean;
  available: boolean;
  stockQuantity?: number | null;
  lowStockThreshold: number;
  lowStock: boolean;
}

export interface StoreRevenue {
  storeId: number;
  storeName: string;
  orderCount: number;
  revenue: number;
}
