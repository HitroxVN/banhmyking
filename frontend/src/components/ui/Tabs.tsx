import type { ReactNode } from 'react';
import '../../styles/components/tabs.css';

export interface TabItem<T extends string = string> {
  key: T;
  label: ReactNode;
  /** Số lượng hiển thị cạnh nhãn (ví dụ số đơn trong mỗi trạng thái) */
  count?: number;
}

export interface TabsProps<T extends string = string> {
  tabs: TabItem<T>[];
  value: T;
  onChange: (key: T) => void;
}

/** Tabs controlled — dùng chung cho staff / shipper / hồ sơ khách. */
export const Tabs = <T extends string = string>({ tabs, value, onChange }: TabsProps<T>) => (
  <div className="ui-tabs" role="tablist">
    {tabs.map((tab) => (
      <button
        key={tab.key}
        type="button"
        role="tab"
        aria-selected={tab.key === value}
        className={`ui-tabs__tab${tab.key === value ? ' ui-tabs__tab--active' : ''}`}
        onClick={() => onChange(tab.key)}
      >
        {tab.label}
        {typeof tab.count === 'number' && <span className="ui-tabs__count">{tab.count}</span>}
      </button>
    ))}
  </div>
);
