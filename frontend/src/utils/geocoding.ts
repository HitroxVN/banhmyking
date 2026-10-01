/**
 * Tra địa chỉ ↔ toạ độ qua Nominatim (OpenStreetMap) — miễn phí, không cần API key.
 *
 * Chính sách Nominatim: tối đa ~1 request/giây và không dùng cho tải lớn. Đủ cho quán nhỏ / môi trường dev;
 * lên production nên chuyển sang dịch vụ geocoding trả phí (chỉ cần thay file này).
 * Dữ liệu OSM ở Việt Nam có thể vẫn ghi phường/quận theo địa giới cũ → kết quả chỉ là GỢI Ý,
 * người dùng luôn sửa tay được.
 */

const NOMINATIM_URL = 'https://nominatim.openstreetmap.org';
/** Khoảng cách tối thiểu giữa 2 request (ms) theo chính sách Nominatim */
const MIN_GAP_MS = 1100;

export interface GeoPoint {
  latitude: number;
  longitude: number;
}

/** Các ô địa chỉ theo địa giới 2 cấp (sau sắp xếp 2025) */
export interface AddressParts {
  street: string;
  ward: string;
  province: string;
  fullAddress: string;
}

export interface GeocodeResult extends GeoPoint, AddressParts {
  /** Nhãn hiển thị trong danh sách kết quả tìm kiếm */
  label: string;
}

interface NominatimAddress {
  house_number?: string;
  road?: string;
  neighbourhood?: string;
  hamlet?: string;
  quarter?: string;
  suburb?: string;
  village?: string;
  town?: string;
  city_district?: string;
  city?: string;
  state?: string;
  province?: string;
}

interface NominatimPlace {
  lat: string;
  lon: string;
  display_name: string;
  address?: NominatimAddress;
}

// Xếp hàng mọi request để không vượt 1 request/giây kể cả khi người dùng bấm liên tục.
let queue: Promise<unknown> = Promise.resolve();
let lastRequestAt = 0;

const throttledFetch = <T>(url: string, signal?: AbortSignal): Promise<T> => {
  const task = queue.then(async () => {
    const wait = lastRequestAt + MIN_GAP_MS - Date.now();
    if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
    lastRequestAt = Date.now();
    const response = await fetch(url, { signal, headers: { 'Accept-Language': 'vi' } });
    if (!response.ok) throw new Error(`Dịch vụ bản đồ lỗi (${response.status})`);
    return (await response.json()) as T;
  });
  // Lỗi của request này không được chặn các request xếp sau
  queue = task.catch(() => undefined);
  return task;
};

/** Cấp phường/xã: ưu tiên tên có tiền tố hành chính rõ ràng, không thì lấy cấp gần nhất */
const WARD_PREFIX = /^(phường|xã|thị trấn|đặc khu)\b/i;

const pickWard = (a: NominatimAddress): string => {
  const candidates = [a.quarter, a.suburb, a.village, a.town, a.city_district].filter(
    (value): value is string => Boolean(value)
  );
  return candidates.find((value) => WARD_PREFIX.test(value)) ?? candidates[0] ?? '';
};

/** Ghép các ô thành địa chỉ đầy đủ — dùng chung cho kết quả bản đồ và khi người dùng sửa tay từng ô */
export const composeFullAddress = ({ street, ward, province }: Omit<AddressParts, 'fullAddress'>): string =>
  [street, ward, province]
    .map((part) => part.trim())
    .filter(Boolean)
    .join(', ');

const toResult = (place: NominatimPlace): GeocodeResult => {
  const a = place.address ?? {};
  const street = [a.house_number, a.road].filter(Boolean).join(' ') || a.neighbourhood || a.hamlet || '';
  const ward = pickWard(a);
  const province = a.city ?? a.state ?? a.province ?? '';
  return {
    latitude: Number(place.lat),
    longitude: Number(place.lon),
    street,
    ward,
    province,
    fullAddress: composeFullAddress({ street, ward, province }) || place.display_name,
    label: place.display_name,
  };
};

/** Toạ độ → địa chỉ (khi ghim trên bản đồ / dùng GPS) */
export const reverseGeocode = async ({ latitude, longitude }: GeoPoint, signal?: AbortSignal) => {
  const params = new URLSearchParams({
    format: 'jsonv2',
    lat: String(latitude),
    lon: String(longitude),
    addressdetails: '1',
    zoom: '18',
    'accept-language': 'vi',
  });
  const place = await throttledFetch<NominatimPlace & { error?: string }>(
    `${NOMINATIM_URL}/reverse?${params}`,
    signal
  );
  if (place.error) return null;
  // Giữ đúng toạ độ người dùng ghim, không nhảy sang toạ độ của toà nhà gần nhất
  return { ...toResult(place), latitude, longitude };
};

/** Chữ → danh sách vị trí gợi ý (ô tìm kiếm trên bản đồ), chỉ trong Việt Nam */
export const searchAddress = async (query: string, signal?: AbortSignal): Promise<GeocodeResult[]> => {
  const params = new URLSearchParams({
    format: 'jsonv2',
    q: query,
    countrycodes: 'vn',
    addressdetails: '1',
    limit: '5',
    'accept-language': 'vi',
  });
  const places = await throttledFetch<NominatimPlace[]>(`${NOMINATIM_URL}/search?${params}`, signal);
  return places.map(toResult);
};
