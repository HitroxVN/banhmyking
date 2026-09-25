import { useContext } from 'react';
import { CartContext } from './cartContextDef';
import type { CartContextType } from './cartContextDef';

/** Hook truy cập giỏ hàng. Trước đây hook này bị trùng ở cả hooks/ và context/. */
export const useCart = (): CartContextType => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
};

export default useCart;
