import { Minus, Plus } from 'lucide-react';
import '../../styles/components/stepper.css';

export interface QuantityStepperProps {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  size?: 'sm' | 'md' | 'lg';
  disabled?: boolean;
  'aria-label'?: string;
}

export const QuantityStepper = ({
  value,
  onChange,
  min = 1,
  max = 99,
  size = 'md',
  disabled = false,
  'aria-label': ariaLabel = 'Số lượng',
}: QuantityStepperProps) => {
  const clamp = (next: number) => Math.min(max, Math.max(min, next));

  return (
    <span className={`ui-stepper${size !== 'md' ? ` ui-stepper--${size}` : ''}`}>
      <button
        type="button"
        className="ui-stepper__btn"
        onClick={() => onChange(clamp(value - 1))}
        disabled={disabled || value <= min}
        aria-label="Giảm số lượng"
      >
        <Minus size={16} />
      </button>
      <span className="ui-stepper__value" aria-label={ariaLabel}>
        {value}
      </span>
      <button
        type="button"
        className="ui-stepper__btn"
        onClick={() => onChange(clamp(value + 1))}
        disabled={disabled || value >= max}
        aria-label="Tăng số lượng"
      >
        <Plus size={16} />
      </button>
    </span>
  );
};
