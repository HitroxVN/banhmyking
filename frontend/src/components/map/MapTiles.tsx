import { useRef, useState } from 'react';
import { TileLayer } from 'react-leaflet';

/**
 * Nguồn tile theo thứ tự ưu tiên. Một số mạng/DNS ở Việt Nam không phân giải được
 * tile.openstreetmap.org → tự chuyển sang máy chủ tile OSM cộng đồng (cùng dữ liệu, cùng kiểu).
 */
const TILE_SOURCES = [
  'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  'https://tile.openstreetmap.de/{z}/{x}/{y}.png',
];
const ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';
/** Số tile lỗi liên tiếp trước khi coi nguồn hiện tại là không dùng được */
const ERRORS_BEFORE_SWITCH = 3;

/** Nhớ nguồn đã chạy được trong phiên, để bản đồ mở sau không phải lỗi lại từ đầu */
let workingSourceIndex = 0;

/** Lớp nền bản đồ dùng chung cho mọi bản đồ Leaflet trong app */
export const MapTiles = () => {
  const [sourceIndex, setSourceIndex] = useState(workingSourceIndex);
  const errorCount = useRef(0);

  return (
    <TileLayer
      // key đổi → Leaflet tạo lại layer với URL mới
      key={sourceIndex}
      url={TILE_SOURCES[sourceIndex]}
      attribution={ATTRIBUTION}
      eventHandlers={{
        tileload: () => {
          errorCount.current = 0;
        },
        tileerror: () => {
          errorCount.current += 1;
          if (errorCount.current >= ERRORS_BEFORE_SWITCH && sourceIndex < TILE_SOURCES.length - 1) {
            errorCount.current = 0;
            workingSourceIndex = sourceIndex + 1;
            setSourceIndex(sourceIndex + 1);
          }
        },
      }}
    />
  );
};
