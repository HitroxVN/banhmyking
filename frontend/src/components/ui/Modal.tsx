import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import '../../styles/components/modal.css';

export type ModalSize = 'sm' | 'md' | 'lg' | 'xl';

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title?: ReactNode;
  size?: ModalSize;
  children: ReactNode;
  /** Vùng nút dưới chân modal */
  footer?: ReactNode;
  /** Bấm ra ngoài để đóng (mặc định bật) */
  closeOnBackdrop?: boolean;
  /** Ẩn nút X góc phải (dùng cho ConfirmDialog) */
  hideClose?: boolean;
}

/** Modal dựng bằng portal: tự khoá cuộn nền, đóng bằng ESC hoặc click ra ngoài. */
export const Modal = ({
  open,
  onClose,
  title,
  size = 'md',
  children,
  footer,
  closeOnBackdrop = true,
  hideClose = false,
}: ModalProps) => {
  useEffect(() => {
    if (!open) return;

    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKey);

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', handleKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onClose]);

  if (!open) return null;

  const showHeader = Boolean(title) || !hideClose;

  return createPortal(
    <div
      className="ui-modal__overlay"
      onMouseDown={(event) => {
        if (closeOnBackdrop && event.target === event.currentTarget) onClose();
      }}
    >
      <div
        className={`ui-modal ui-modal--${size}`}
        role="dialog"
        aria-modal="true"
        aria-label={typeof title === 'string' ? title : undefined}
      >
        {showHeader && (
          <header className="ui-modal__header">
            {title ? <h2 className="ui-modal__title">{title}</h2> : <span />}
            {!hideClose && (
              <button type="button" className="ui-modal__close" onClick={onClose} aria-label="Đóng">
                <X size={18} />
              </button>
            )}
          </header>
        )}
        <div className="ui-modal__body">{children}</div>
        {footer && <footer className="ui-modal__footer">{footer}</footer>}
      </div>
    </div>,
    document.body
  );
};
