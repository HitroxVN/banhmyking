import { createContext } from 'react';
import type { Cart } from '../types/cart';

export interface CartContextType {
  cart: Cart | null;
  isLoading: boolean;
  isUpdating: boolean;
  error: string | null;
  totalQuantity: number;
  subtotal: number;
  refreshCart: () => Promise<void>;
  updateQuantity: (itemId: number, quantity: number) => Promise<void>;
  removeItem: (itemId: number) => Promise<void>;
  clearCart: () => Promise<void>;
  addItem: (productId: number, quantity: number, optionIds?: number[]) => Promise<void>;
}

export const CartContext = createContext<CartContextType | undefined>(undefined);
