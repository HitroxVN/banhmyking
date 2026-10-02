import { createContext } from 'react';

export interface StoreScopeContextType {
  /** Cơ sở đang làm việc; null = ADMIN chưa chọn */
  storeId: number | null;
  storeName: string | null;
  setStore: (storeId: number | null, storeName: string | null) => void;
  /** Chỉ ADMIN được chọn cơ sở; nhân sự cơ sở luôn bị khoá về cơ sở mình */
  canChoose: boolean;
}

export const StoreScopeContext = createContext<StoreScopeContextType | undefined>(undefined);
