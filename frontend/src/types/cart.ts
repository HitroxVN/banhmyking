/**
 * Cart TypeScript types matching Spring Boot backend DTOs:
 * - CartResponse
 * - CartItemResponse
 * - CartItemOptionResponse
 * - AddToCartRequest
 * - UpdateCartItemRequest
 */

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
  basePrice: number;
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
}

export interface AddToCartRequest {
  productId: number;
  quantity: number;
  optionIds?: number[];
}

export interface UpdateCartItemRequest {
  quantity: number;
}
