import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { MapContainer, Marker, useMap, useMapEvents } from 'react-leaflet';
import L, { type LeafletEventHandlerFnMap } from 'leaflet';
import 'leaflet/dist/leaflet.css';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
import { Crosshair, MapPin, Search } from 'lucide-react';
import { Button } from '../ui';
import { reverseGeocode, searchAddress, type GeocodeResult, type GeoPoint } from '../../utils/geocoding';
import { MapTiles } from '../map/MapTiles';
import '../../styles/components/address-map.css';

// Bundler (Vite) làm mất đường dẫn ảnh marker mặc định của Leaflet → khai báo lại tường minh.
const PIN_ICON = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  shadowSize: [41, 41],
});

/** Chưa có vị trí quán: mở bản đồ ở trung tâm Hà Nội */
const FALLBACK_CENTER: [number, number] = [21.028511, 105.854167];
const ZOOM_OVERVIEW = 13;
const ZOOM_PINNED = 17;

export interface AddressMapPickerProps {
  /** Điểm đang ghim; null = chưa ghim */
  value: GeoPoint | null;
  /**
   * Gọi khi người dùng ghim/kéo/chọn kết quả/dùng GPS.
   * `address` = địa chỉ tra ngược được (null nếu dịch vụ bản đồ lỗi — vẫn giữ toạ độ).
   */
  onPick: (point: GeoPoint, address: GeocodeResult | null) => void;
  height?: number;
}

/** Bắt sự kiện click trên bản đồ */
const ClickToPin = ({ onClick }: { onClick: (point: GeoPoint) => void }) => {
  useMapEvents({
    click: (event) => onClick({ latitude: event.latlng.lat, longitude: event.latlng.lng }),
  });
  return null;
};

/** Bay tới điểm mới khi điểm ghim đổi từ bên ngoài (tìm kiếm, GPS) */
const FlyTo = ({ point }: { point: GeoPoint | null }) => {
  const map = useMap();
  useEffect(() => {
    if (point) map.flyTo([point.latitude, point.longitude], Math.max(map.getZoom(), ZOOM_PINNED), { duration: 0.6 });
  }, [map, point]);
  return null;
};

/**
 * Bản đồ chọn vị trí: bấm để ghim, kéo ghim để chỉnh, tìm theo tên đường, hoặc dùng vị trí hiện tại.
 * Component chỉ lo toạ độ + tra ngược địa chỉ; điền vào ô nào do component cha quyết định.
 */
export const AddressMapPicker = ({ value, onPick, height = 280 }: AddressMapPickerProps) => {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<GeocodeResult[]>([]);
  const [busy, setBusy] = useState<'search' | 'reverse' | 'gps' | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  /** Chỉ request mới nhất được áp kết quả — ghim liên tục không để kết quả cũ đè kết quả mới */
  const requestIdRef = useRef(0);

  const [initialCenter] = useState<[number, number]>(() =>
    value ? [value.latitude, value.longitude] : FALLBACK_CENTER
  );

  const pinAt = async (point: GeoPoint) => {
    const requestId = ++requestIdRef.current;
    setResults([]);
    setMessage(null);
    setBusy('reverse');
    onPick(point, null);
    try {
      const address = await reverseGeocode(point);
      if (requestId !== requestIdRef.current) return;
      if (address) onPick(point, address);
      else setMessage('Không xác định được địa chỉ tại điểm này — vui lòng nhập tay các ô bên dưới.');
    } catch {
      if (requestId === requestIdRef.current) {
        setMessage('Không tra được địa chỉ (lỗi mạng hoặc dịch vụ bản đồ) — vị trí đã được ghim, vui lòng nhập tay.');
      }
    } finally {
      if (requestId === requestIdRef.current) setBusy(null);
    }
  };

  const handleSearch = async () => {
    const text = query.trim();
    if (text.length < 3) {
      setMessage('Nhập ít nhất 3 ký tự để tìm.');
      return;
    }
    const requestId = ++requestIdRef.current;
    setBusy('search');
    setMessage(null);
    try {
      const found = await searchAddress(text);
      if (requestId !== requestIdRef.current) return;
      setResults(found);
      if (found.length === 0) setMessage('Không tìm thấy — thử ghi rõ tên đường và tỉnh/thành, hoặc bấm thẳng lên bản đồ.');
    } catch {
      if (requestId === requestIdRef.current) setMessage('Tìm kiếm thất bại, vui lòng thử lại.');
    } finally {
      if (requestId === requestIdRef.current) setBusy(null);
    }
  };

  const chooseResult = (result: GeocodeResult) => {
    requestIdRef.current++;
    setResults([]);
    setMessage(null);
    onPick({ latitude: result.latitude, longitude: result.longitude }, result);
  };

  const useCurrentLocation = () => {
    if (!('geolocation' in navigator)) {
      setMessage('Trình duyệt không hỗ trợ định vị.');
      return;
    }
    setBusy('gps');
    setMessage(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setBusy(null);
        void pinAt({ latitude: position.coords.latitude, longitude: position.coords.longitude });
      },
      () => {
        setBusy(null);
        setMessage('Không lấy được vị trí — hãy cho phép trình duyệt truy cập vị trí, hoặc ghim tay trên bản đồ.');
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  };

  const markerHandlers: LeafletEventHandlerFnMap = {
    dragend: (event) => {
      const { lat, lng } = (event.target as L.Marker).getLatLng();
      void pinAt({ latitude: lat, longitude: lng });
    },
  };

  return (
    <div className="addr-map">
      {/* Không dùng <form>: picker nằm trong form của trang cha (thanh toán, hồ sơ…) — form lồng nhau
          khiến submit lan ra form ngoài (ở trang thanh toán là gửi luôn đơn hàng). */}
      <div className="addr-map__search" role="search">
        <input
          className="addr-map__input"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Tìm số nhà, tên đường, phường/xã…"
          aria-label="Tìm địa chỉ trên bản đồ"
          onKeyDown={(event: KeyboardEvent<HTMLInputElement>) => {
            if (event.key === 'Enter') {
              event.preventDefault();
              void handleSearch();
            }
          }}
        />
        <Button
          type="button"
          variant="secondary"
          size="sm"
          icon={<Search size={15} />}
          loading={busy === 'search'}
          onClick={() => void handleSearch()}
        >
          Tìm
        </Button>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          icon={<Crosshair size={15} />}
          loading={busy === 'gps'}
          onClick={useCurrentLocation}
        >
          Vị trí của tôi
        </Button>
      </div>

      {results.length > 0 && (
        <ul className="addr-map__results" role="listbox" aria-label="Kết quả tìm kiếm">
          {results.map((result) => (
            <li key={`${result.latitude},${result.longitude}`}>
              <button type="button" onClick={() => chooseResult(result)}>
                <MapPin size={14} />
                <span>{result.label}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className="addr-map__canvas" style={{ height }}>
        <MapContainer
          center={initialCenter}
          zoom={value ? ZOOM_PINNED : ZOOM_OVERVIEW}
          scrollWheelZoom
          style={{ height: '100%', width: '100%' }}
        >
          <MapTiles />
          <ClickToPin onClick={(point) => void pinAt(point)} />
          <FlyTo point={value} />
          {value && (
            <Marker
              position={[value.latitude, value.longitude]}
              icon={PIN_ICON}
              draggable
              eventHandlers={markerHandlers}
            />
          )}
        </MapContainer>
      </div>

      <p className={`addr-map__hint${message ? ' addr-map__hint--warn' : ''}`} aria-live="polite">
        {busy === 'reverse'
          ? 'Đang tìm địa chỉ tại điểm đã ghim…'
          : message ??
            (value
              ? 'Kéo ghim để chỉnh vị trí. Các ô bên dưới đã được điền sẵn — sửa lại nếu chưa đúng.'
              : 'Bấm lên bản đồ để ghim vị trí giao hàng, hoặc tìm theo tên đường.')}
      </p>
    </div>
  );
};
