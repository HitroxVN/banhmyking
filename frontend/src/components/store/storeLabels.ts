import type { BadgeTone } from '../ui';
import type { PublicStore } from '../../types/store';

/** Nhãn trạng thái cơ sở hiển thị cho khách */
export const storeStatus = (store: Pick<PublicStore, 'openNow' | 'acceptingOrders'>): { label: string; tone: BadgeTone } => {
  if (!store.openNow) return { label: 'Đã đóng cửa', tone: 'neutral' };
  if (!store.acceptingOrders) return { label: 'Tạm ngưng nhận đơn', tone: 'warning' };
  return { label: 'Đang mở cửa', tone: 'success' };
};

/** Khoảng cách đường chim bay (km) — chỉ để sắp xếp "gần tôi", phí ship do server tính */
export const haversineKm = (lat1: number, lng1: number, lat2: number, lng2: number): number => {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371.0088 * Math.asin(Math.min(1, Math.sqrt(a)));
};

export const directionsUrl = (latitude: number | null | undefined, longitude: number | null | undefined, address: string) =>
  latitude != null && longitude != null
    ? `https://www.google.com/maps/dir/?api=1&destination=${latitude},${longitude}`
    : `https://maps.google.com/?q=${encodeURIComponent(address)}`;
