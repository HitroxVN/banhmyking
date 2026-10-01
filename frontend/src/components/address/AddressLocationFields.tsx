import { useState } from 'react';
import { MapPinOff } from 'lucide-react';
import { Button, Input, Textarea } from '../ui';
import { AddressMapPicker } from './AddressMapPicker';
import { composeFullAddress, type GeocodeResult, type GeoPoint } from '../../utils/geocoding';
import type { AddressLocation } from './addressLocation';

export interface AddressLocationFieldsProps {
  value: AddressLocation;
  onChange: (next: AddressLocation) => void;
  /** Lỗi của ô địa chỉ đầy đủ (validate ở component cha) */
  fullAddressError?: string;
  fullAddressLabel?: string;
  mapHeight?: number;
}

/**
 * Bản đồ ghim + các ô địa chỉ. Ghim trên bản đồ → các ô tự điền; người dùng sửa tay ô nào cũng được.
 * Ô "Địa chỉ đầy đủ" tự ghép lại từ các ô nhỏ cho tới khi người dùng sửa tay chính nó.
 */
export const AddressLocationFields = ({
  value,
  onChange,
  fullAddressError,
  fullAddressLabel = 'Địa chỉ đầy đủ',
  mapHeight,
}: AddressLocationFieldsProps) => {
  // Đang sửa địa chỉ có sẵn mà ô tổng khác bản ghép → người dùng từng sửa tay, đừng ghi đè.
  const [fullTouched, setFullTouched] = useState(
    () => value.fullAddress !== '' && value.fullAddress !== composeFullAddress(value)
  );

  const pinned: GeoPoint | null =
    value.latitude != null && value.longitude != null ? { latitude: value.latitude, longitude: value.longitude } : null;

  const handlePick = (point: GeoPoint, address: GeocodeResult | null) => {
    if (!address) {
      // Mới có toạ độ (đang tra địa chỉ hoặc tra lỗi) — giữ nguyên các ô người dùng đã gõ
      onChange({ ...value, latitude: point.latitude, longitude: point.longitude });
      return;
    }
    // Ghim mới = chọn lại địa chỉ: điền đè các ô theo kết quả bản đồ
    setFullTouched(false);
    onChange({
      street: address.street,
      ward: address.ward,
      province: address.province,
      fullAddress: address.fullAddress,
      latitude: point.latitude,
      longitude: point.longitude,
    });
  };

  const setPart = (key: 'street' | 'ward' | 'province', text: string) => {
    const next = { ...value, [key]: text };
    onChange(fullTouched ? next : { ...next, fullAddress: composeFullAddress(next) });
  };

  return (
    <div className="addr-fields">
      <AddressMapPicker value={pinned} onPick={handlePick} height={mapHeight} />

      {pinned && (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          icon={<MapPinOff size={15} />}
          onClick={() => onChange({ ...value, latitude: null, longitude: null })}
        >
          Bỏ ghim (phí ship sẽ tính theo khu vực)
        </Button>
      )}

      <Input
        label="Số nhà, tên đường"
        value={value.street}
        onChange={(event) => setPart('street', event.target.value)}
        placeholder="12 Láng Hạ"
        maxLength={255}
      />
      <div className="addr-fields__row">
        <Input
          label="Phường / Xã"
          value={value.ward}
          onChange={(event) => setPart('ward', event.target.value)}
          placeholder="Phường Giảng Võ"
          maxLength={100}
        />
        <Input
          label="Tỉnh / Thành phố"
          value={value.province}
          onChange={(event) => setPart('province', event.target.value)}
          placeholder="Hà Nội"
          maxLength={100}
        />
      </div>
      <Textarea
        label={fullAddressLabel}
        required
        rows={2}
        value={value.fullAddress}
        onChange={(event) => {
          setFullTouched(true);
          onChange({ ...value, fullAddress: event.target.value });
        }}
        error={fullAddressError}
        hint="Shipper giao theo địa chỉ này — ghi thêm ngõ, toà nhà, mốc dễ tìm nếu cần."
        placeholder="Số nhà, đường, phường/xã, tỉnh/thành"
        maxLength={500}
      />
    </div>
  );
};
