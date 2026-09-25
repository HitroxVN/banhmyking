import { useCallback, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { AlertTriangle, Check, Info, X } from 'lucide-react';
import { ToastContext } from './toast';
import type { ToastApi, ToastType } from './toast';
import '../../styles/components/toast.css';

interface ToastItem {
  id: number;
  text: string;
  type: ToastType;
}

const ICONS: Record<ToastType, ReactNode> = {
  success: <Check size={18} />,
  error: <AlertTriangle size={18} />,
  info: <Info size={18} />,
};

const AUTO_DISMISS_MS = 3200;

/** Kho toast toàn cục — thay các khối toast tự đặt setTimeout ở từng trang. */
export const ToastProvider = ({ children }: { children: ReactNode }) => {
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(0);

  const remove = useCallback((id: number) => {
    setItems((prev) => prev.filter((item) => item.id !== id));
  }, []);

  const push = useCallback(
    (text: string, type: ToastType = 'success') => {
      const id = nextId.current++;
      setItems((prev) => [...prev, { id, text, type }]);
      window.setTimeout(() => remove(id), AUTO_DISMISS_MS);
    },
    [remove]
  );

  const api = useMemo<ToastApi>(
    () => ({
      push,
      success: (text: string) => push(text, 'success'),
      error: (text: string) => push(text, 'error'),
      info: (text: string) => push(text, 'info'),
    }),
    [push]
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="ui-toasts" role="region" aria-live="polite">
        {items.map((item) => (
          <div key={item.id} className={`ui-toast ui-toast--${item.type}`}>
            <span className="ui-toast__icon" aria-hidden="true">
              {ICONS[item.type]}
            </span>
            <span className="ui-toast__text">{item.text}</span>
            <button
              type="button"
              className="ui-toast__close"
              onClick={() => remove(item.id)}
              aria-label="Đóng thông báo"
            >
              <X size={14} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};
