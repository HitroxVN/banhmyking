import type { ReactNode } from 'react';
import '../../styles/components/chips.css';

export interface ChipOption<T> {
  value: T;
  label: ReactNode;
}

export interface ChipGroupProps<T> {
  options: ChipOption<T>[];
  value: T;
  onChange: (value: T) => void;
  ariaLabel?: string;
}

/** Dãy chip lọc ngang (danh mục món, trạng thái đơn) — cuộn ngang khi tràn. */
export const ChipGroup = <T,>({ options, value, onChange, ariaLabel }: ChipGroupProps<T>) => (
  <div className="ui-chips" role="tablist" aria-label={ariaLabel}>
    {options.map((option) => (
      <button
        key={String(option.value)}
        type="button"
        role="tab"
        aria-selected={option.value === value}
        className={`ui-chip${option.value === value ? ' ui-chip--active' : ''}`}
        onClick={() => onChange(option.value)}
      >
        {option.label}
      </button>
    ))}
  </div>
);
