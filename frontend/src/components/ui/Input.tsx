import { useState } from 'react';
import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';
import { ChevronDown, Eye, EyeOff } from 'lucide-react';
import '../../styles/components/field.css';

interface FieldShellProps {
  label?: ReactNode;
  error?: string;
  hint?: string;
  requiredMark?: boolean;
  children: ReactNode;
}

const FieldShell = ({ label, error, hint, requiredMark, children }: FieldShellProps) => (
  <label className="ui-field">
    {label && (
      <span className="ui-field__label">
        {label}
        {requiredMark && <span className="ui-field__label-required">*</span>}
      </span>
    )}
    {children}
    {error ? (
      <span className="ui-field__msg ui-field__msg--error">{error}</span>
    ) : hint ? (
      <span className="ui-field__msg">{hint}</span>
    ) : null}
  </label>
);

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: ReactNode;
  error?: string;
  hint?: string;
  /** Icon hiển thị bên trái ô nhập */
  icon?: ReactNode;
}

/** Ô nhập. `type="password"` tự có nút hiện/ẩn mật khẩu. */
export const Input = ({ label, error, hint, icon, className = '', type = 'text', ...rest }: InputProps) => {
  const [revealed, setRevealed] = useState(false);
  const isPassword = type === 'password';
  const inputType = isPassword && revealed ? 'text' : type;
  const hasTrail = isPassword;

  return (
    <FieldShell label={label} error={error} hint={hint} requiredMark={rest.required}>
      <span className="ui-field__wrap">
        {icon && <span className="ui-field__lead">{icon}</span>}
        <input
          type={inputType}
          className={[
            'ui-field__input',
            icon ? 'ui-field__input--lead' : '',
            hasTrail ? 'ui-field__input--trail' : '',
            error ? 'ui-field__input--invalid' : '',
            className,
          ]
            .filter(Boolean)
            .join(' ')}
          {...rest}
        />
        {hasTrail && (
          <button
            type="button"
            className="ui-field__trail"
            onClick={() => setRevealed((v) => !v)}
            aria-label={revealed ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            tabIndex={-1}
          >
            {revealed ? <EyeOff size={18} /> : <Eye size={18} />}
          </button>
        )}
      </span>
    </FieldShell>
  );
};

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: ReactNode;
  error?: string;
  hint?: string;
}

export const Textarea = ({ label, error, hint, className = '', ...rest }: TextareaProps) => (
  <FieldShell label={label} error={error} hint={hint} requiredMark={rest.required}>
    <span className="ui-field__wrap">
      <textarea
        className={`ui-field__input${error ? ' ui-field__input--invalid' : ''}${className ? ` ${className}` : ''}`}
        {...rest}
      />
    </span>
  </FieldShell>
);

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: ReactNode;
  error?: string;
  hint?: string;
  children: ReactNode;
}

export const Select = ({ label, error, hint, className = '', children, ...rest }: SelectProps) => (
  <FieldShell label={label} error={error} hint={hint} requiredMark={rest.required}>
    <span className="ui-field__wrap">
      <select
        className={`ui-field__input${error ? ' ui-field__input--invalid' : ''}${className ? ` ${className}` : ''}`}
        {...rest}
      >
        {children}
      </select>
      <span className="ui-field__caret">
        <ChevronDown size={18} />
      </span>
    </span>
  </FieldShell>
);
