import { useEffect, useState } from 'react';
import { storeApi } from '../../api/storeApi';
import { Select } from '../ui';
import type { Store } from '../../types/store';

export interface StoreScopeSelectProps {
  value: number | null;
  onChange: (storeId: number | null) => void;
  /** Có lựa chọn "Tất cả cơ sở" (null) */
  allowAll?: boolean;
  label?: string;
}

/** Ô chọn cơ sở dùng chung cho các trang ADMIN (đơn, báo cáo, dashboard, tài khoản) */
export const StoreScopeSelect = ({ value, onChange, allowAll = true, label = 'Cơ sở' }: StoreScopeSelectProps) => {
  const [stores, setStores] = useState<Store[]>([]);

  useEffect(() => {
    let alive = true;
    storeApi
      .adminList()
      .then((data) => {
        if (alive) setStores(data);
      })
      .catch(() => {
        if (alive) setStores([]);
      });
    return () => {
      alive = false;
    };
  }, []);

  return (
    <Select
      label={label}
      value={value == null ? '' : String(value)}
      onChange={(event) => onChange(event.target.value === '' ? null : Number(event.target.value))}
    >
      {allowAll && <option value="">Tất cả cơ sở</option>}
      {!allowAll && value == null && <option value="">— Chọn cơ sở —</option>}
      {stores.map((store) => (
        <option key={store.id} value={store.id}>
          {store.code} · {store.name}
        </option>
      ))}
    </Select>
  );
};
