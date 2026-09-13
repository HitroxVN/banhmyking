import { useContext } from 'react';
import { CartContext } from '../context/cartContextDef';
import type { CartContextType } from '../context/cartContextDef';

export const useCart = (): CartContextType => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
};

export default useCart;
