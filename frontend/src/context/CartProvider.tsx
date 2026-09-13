import React, { useCallback, useEffect, useMemo, useState } from 'react';
import type { Cart, CartItem } from '../types/cart';
import { CartContext } from './cartContextDef';
import { cartApi } from '../api/cartApi';
import { useAuth } from './useAuth';

interface CartProviderProps {
  children: React.ReactNode;
}

const EMPTY_CART: Cart = {
  cartId: null,
  items: [],
  totalQuantity: 0,
  subtotal: 0,
};

export const CartProvider: React.FC<CartProviderProps> = ({ children }) => {
  const { isAuthenticated } = useAuth();
  const [cart, setCart] = useState<Cart | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isUpdating, setIsUpdating] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  /**
   * Tính toán realtime tổng tiền và tổng số lượng từ state items hiện thời
   */
  const computedTotals = useMemo(() => {
    if (!cart || !cart.items || cart.items.length === 0) {
      return { totalQuantity: 0, subtotal: 0 };
    }
    const totalQuantity = cart.items.reduce((sum, item) => sum + (item.quantity || 0), 0);
    const subtotal = cart.items.reduce(
      (sum, item) => sum + (item.unitPrice || item.basePrice || 0) * (item.quantity || 0),
      0
    );
    return { totalQuantity, subtotal };
  }, [cart]);

  /**
   * Tải lại toàn bộ giỏ hàng từ backend
   */
  const refreshCart = useCallback(async () => {
    if (!isAuthenticated) {
      setCart(EMPTY_CART);
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const serverCart = await cartApi.getCart();
      setCart(serverCart || EMPTY_CART);
    } catch (err: unknown) {
      const errObj = err as Error;
      console.error('Fetch cart error:', errObj);
      setError(errObj.message || 'Không thể tải giỏ hàng');
      setCart(EMPTY_CART);
    } finally {
      setIsLoading(false);
    }
  }, [isAuthenticated]);

  // Tự động tải giỏ hàng khi người dùng đã xác thực
  useEffect(() => {
    if (isAuthenticated) {
      refreshCart();
    } else {
      setCart(EMPTY_CART);
    }
  }, [isAuthenticated, refreshCart]);

  /**
   * Cập nhật số lượng món ăn (Optimistic Realtime Update + Sync Backend)
   */
  const updateQuantity = useCallback(
    async (itemId: number, newQuantity: number) => {
      if (!cart) return;

      // Nếu số lượng <= 0, chuyển sang xóa món
      if (newQuantity <= 0) {
        await removeItem(itemId);
        return;
      }

      const previousCart = cart;
      // Optimistic update: Tính toán realtime tức thời
      const updatedItems: CartItem[] = cart.items.map((item) => {
        if (item.id === itemId) {
          const itemUnitPrice = Number(item.unitPrice || item.basePrice || 0);
          return {
            ...item,
            quantity: newQuantity,
            subtotal: itemUnitPrice * newQuantity,
          };
        }
        return item;
      });

      const newTotalQuantity = updatedItems.reduce((sum, i) => sum + i.quantity, 0);
      const newSubtotal = updatedItems.reduce((sum, i) => sum + i.subtotal, 0);

      setCart({
        ...cart,
        items: updatedItems,
        totalQuantity: newTotalQuantity,
        subtotal: newSubtotal,
      });

      setIsUpdating(true);
      setError(null);

      try {
        const syncedCart = await cartApi.updateQuantity(itemId, newQuantity);
        setCart(syncedCart);
      } catch (err: unknown) {
        const errObj = err as Error;
        console.error('Update quantity error:', errObj);
        setError(errObj.message || 'Cập nhật số lượng thất bại');
        // Rollback state nếu lỗi
        setCart(previousCart);
      } finally {
        setIsUpdating(false);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [cart]
  );

  /**
   * Xóa một món ăn khỏi giỏ hàng
   */
  const removeItem = useCallback(
    async (itemId: number) => {
      if (!cart) return;

      const previousCart = cart;
      // Optimistic update: Loại bỏ món và tính lại tổng tiền realtime
      const updatedItems = cart.items.filter((item) => item.id !== itemId);
      const newTotalQuantity = updatedItems.reduce((sum, i) => sum + i.quantity, 0);
      const newSubtotal = updatedItems.reduce(
        (sum, i) => sum + (i.unitPrice || i.basePrice || 0) * i.quantity,
        0
      );

      setCart({
        ...cart,
        items: updatedItems,
        totalQuantity: newTotalQuantity,
        subtotal: newSubtotal,
      });

      setIsUpdating(true);
      setError(null);

      try {
        const syncedCart = await cartApi.removeItem(itemId);
        setCart(syncedCart);
      } catch (err: unknown) {
        const errObj = err as Error;
        console.error('Remove item error:', errObj);
        setError(errObj.message || 'Xóa món khỏi giỏ hàng thất bại');
        // Rollback state nếu lỗi
        setCart(previousCart);
      } finally {
        setIsUpdating(false);
      }
    },
    [cart]
  );

  /**
   * Xóa sạch toàn bộ giỏ hàng
   */
  const clearCart = useCallback(async () => {
    const previousCart = cart;
    setCart(EMPTY_CART);
    setIsUpdating(true);
    setError(null);

    try {
      const syncedCart = await cartApi.clearCart();
      setCart(syncedCart || EMPTY_CART);
    } catch (err: unknown) {
      const errObj = err as Error;
      console.error('Clear cart error:', errObj);
      setError(errObj.message || 'Xóa giỏ hàng thất bại');
      setCart(previousCart);
    } finally {
      setIsUpdating(false);
    }
  }, [cart]);

  /**
   * Thêm món vào giỏ hàng
   */
  const addItem = useCallback(
    async (productId: number, quantity: number, optionIds?: number[]) => {
      setIsUpdating(true);
      setError(null);
      try {
        const syncedCart = await cartApi.addToCart({
          productId,
          quantity,
          optionIds: optionIds || [],
        });
        setCart(syncedCart);
      } catch (err: unknown) {
        const errObj = err as Error;
        console.error('Add item error:', errObj);
        setError(errObj.message || 'Thêm món vào giỏ hàng thất bại');
        throw err;
      } finally {
        setIsUpdating(false);
      }
    },
    []
  );

  const contextValue = useMemo(
    () => ({
      cart,
      isLoading,
      isUpdating,
      error,
      totalQuantity: computedTotals.totalQuantity,
      subtotal: computedTotals.subtotal,
      refreshCart,
      updateQuantity,
      removeItem,
      clearCart,
      addItem,
    }),
    [
      cart,
      isLoading,
      isUpdating,
      error,
      computedTotals.totalQuantity,
      computedTotals.subtotal,
      refreshCart,
      updateQuantity,
      removeItem,
      clearCart,
      addItem,
    ]
  );

  return <CartContext.Provider value={contextValue}>{children}</CartContext.Provider>;
};
