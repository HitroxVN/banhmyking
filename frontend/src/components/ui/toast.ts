import { createContext, useContext } from 'react';

export type ToastType = 'success' | 'error' | 'info';

export interface ToastApi {
  push: (text: string, type?: ToastType) => void;
  success: (text: string) => void;
  error: (text: string) => void;
  info: (text: string) => void;
}

export const ToastContext = createContext<ToastApi | null>(null);

/** Bắn thông báo góc màn hình. Yêu cầu `<ToastProvider>` ở gốc app. */
export const useToast = (): ToastApi => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
};
