import { createContext, useContext } from 'react';
import type { ReactNode } from 'react';

export interface ConfirmOptions {
  title?: string;
  message: ReactNode;
  confirmText?: string;
  cancelText?: string;
  /** Tô đỏ nút xác nhận cho hành động phá huỷ */
  danger?: boolean;
}

export type ConfirmFn = (options: ConfirmOptions | string) => Promise<boolean>;

export const ConfirmContext = createContext<ConfirmFn | null>(null);

/** Thay thế `window.confirm` — trả về Promise<boolean>. */
export const useConfirm = (): ConfirmFn => {
  const context = useContext(ConfirmContext);
  if (!context) {
    throw new Error('useConfirm must be used within a ConfirmProvider');
  }
  return context;
};
