import { useCallback, useState } from 'react';
import type { ReactNode } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Modal } from './Modal';
import { Button } from './Button';
import { ConfirmContext } from './confirm';
import type { ConfirmFn, ConfirmOptions } from './confirm';

interface ConfirmState {
  options: ConfirmOptions;
  resolve: (accepted: boolean) => void;
}

/** Dựng một lần ở gốc app; các trang gọi `await useConfirm()({...})`. */
export const ConfirmProvider = ({ children }: { children: ReactNode }) => {
  const [state, setState] = useState<ConfirmState | null>(null);

  const confirm = useCallback<ConfirmFn>((input) => {
    const options = typeof input === 'string' ? { message: input } : input;
    return new Promise<boolean>((resolve) => setState({ options, resolve }));
  }, []);

  const settle = (accepted: boolean) => {
    state?.resolve(accepted);
    setState(null);
  };

  const options = state?.options;

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <Modal
        open={state !== null}
        onClose={() => settle(false)}
        size="sm"
        hideClose
        footer={
          <>
            <Button variant="secondary" onClick={() => settle(false)}>
              {options?.cancelText ?? 'Huỷ bỏ'}
            </Button>
            <Button variant={options?.danger ? 'danger' : 'primary'} onClick={() => settle(true)} autoFocus>
              {options?.confirmText ?? 'Xác nhận'}
            </Button>
          </>
        }
      >
        <div className="ui-confirm__head">
          <span className={`ui-confirm__icon${options?.danger ? ' ui-confirm__icon--danger' : ''}`}>
            <AlertTriangle size={20} />
          </span>
          <div>
            {options?.title && <h3 className="ui-confirm__title">{options.title}</h3>}
            <p className="ui-confirm__text">{options?.message}</p>
          </div>
        </div>
      </Modal>
    </ConfirmContext.Provider>
  );
};
