import { useMemo } from 'react';
import { MapContainer, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
import type { PublicStore } from '../../types/store';
import { MapTiles } from '../map/MapTiles';
import { storeStatus } from './storeLabels';

const PIN_ICON = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  shadowSize: [41, 41],
});

const FALLBACK_CENTER: [number, number] = [21.028511, 105.854167];

export interface StoresMapProps {
  stores: PublicStore[];
  height?: number;
}

/** Bản đồ ghim mọi cơ sở đã có toạ độ (không gọi tra địa chỉ — chỉ tải tile) */
export const StoresMap = ({ stores, height = 360 }: StoresMapProps) => {
  const pinned = stores.filter((s) => s.latitude != null && s.longitude != null);
  const bounds = useMemo(
    () =>
      pinned.length > 1
        ? L.latLngBounds(pinned.map((s) => [s.latitude as number, s.longitude as number]))
        : undefined,
    [pinned]
  );
  const center: [number, number] =
    pinned.length === 1 ? [pinned[0].latitude as number, pinned[0].longitude as number] : FALLBACK_CENTER;

  return (
    <div className="addr-map__canvas" style={{ height }}>
      <MapContainer
        center={center}
        zoom={12}
        bounds={bounds}
        boundsOptions={{ padding: [32, 32] }}
        scrollWheelZoom={false}
        style={{ height: '100%', width: '100%' }}
      >
        <MapTiles />
        {pinned.map((store) => (
          <Marker key={store.id} position={[store.latitude as number, store.longitude as number]} icon={PIN_ICON}>
            <Popup>
              <strong>{store.name}</strong>
              <br />
              {store.address}
              <br />
              {store.openTime}–{store.closeTime} · {storeStatus(store).label}
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
};
