import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
import type { StockMovement } from '../types/staff';
import type { StoreStockItem } from '../types/store';

const base = (storeId: number) => `/store-inventory/${storeId}/products`;

export const storeInventoryApi = {
  async list(storeId: number): Promise<StoreStockItem[]> {
    const res = await axiosClient.get<ApiResponse<StoreStockItem[]>>(base(storeId));
    return res.data.data;
  },
  async setAvailability(storeId: number, productId: number, available: boolean): Promise<StoreStockItem> {
    const res = await axiosClient.patch<ApiResponse<StoreStockItem>>(`${base(storeId)}/${productId}/availability`, {
      available,
    });
    return res.data.data;
  },
  async adjustStock(storeId: number, productId: number, changeQty: number, note?: string): Promise<StoreStockItem> {
    const res = await axiosClient.post<ApiResponse<StoreStockItem>>(`${base(storeId)}/${productId}/stock`, {
      changeQty,
      note,
    });
    return res.data.data;
  },
  async movements(storeId: number, productId: number, size = 8): Promise<StockMovement[]> {
    const res = await axiosClient.get<ApiResponse<PageResponse<StockMovement>>>(
      `${base(storeId)}/${productId}/movements`,
      { params: { page: 0, size } }
    );
    return res.data.data.content;
  },
};
