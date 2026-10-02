/**
 * Cart TypeScript types matching Spring Boot backend DTOs:
 * - CartResponse
 * - CartItemResponse
 * - CartItemOptionResponse
 * - AddToCartRequest
 * - UpdateCartItemRequest
 */

import type { ComboItemInfo, ProductType } from './staff';

export interface CartItemOption {
  id: number;
  productOptionId: number;
  name: string;
  extraPrice: number;
}

export interface CartItem {
  id: number;
  productId: number;
  productName: string;
  productImageUrl: string;
  productType?: ProductType;
  /** Giá đang áp dụng của 1 phần (giá KM / giá combo), chưa gồm topping */
  basePrice: number;
  /** Giá gốc 1 phần chưa gồm topping (combo = tổng giá lẻ) */
  originalUnitPrice?: number;
  comboItems?: ComboItemInfo[];
  quantity: number;
  unitPrice: number;
  subtotal: number;
  options: CartItemOption[];
}

export interface Cart {
  cartId: number | null;
  items: CartItem[];
  totalQuantity: number;
  subtotal: number;
  /** Tiền tiết kiệm nhờ giá KM và combo */
  savingsAmount?: number;
}

export interface AddToCartRequest {
  productId: number;
  quantity: number;
  optionIds?: number[];
}

export interface UpdateCartItemRequest {
  quantity: number;
}
