import { useEffect, useMemo, useState } from 'react';
import { Clock, Crosshair, MapPin, Navigation, Phone, Store as StoreIcon } from 'lucide-react';
import { storeApi } from '../api/storeApi';
import { Badge, Button, EmptyState, Spinner } from '../components/ui';
import { StoresMap } from '../components/store/StoresMap';
import { directionsUrl, haversineKm, storeStatus } from '../components/store/storeLabels';
import type { PublicStore } from '../types/store';
import '../styles/components/address-map.css';
import '../styles/components/stores.css';

/** Hệ thống cửa hàng — bản đồ + danh sách, sắp theo khoảng cách khi khách bật vị trí */
export const StoresPage = () => {
  const [stores, setStores] = useState<PublicStore[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [me, setMe] = useState<{ latitude: number; longitude: number } | null>(null);
  const [locating, setLocating] = useState(false);

  useEffect(() => {
    let alive = true;
    storeApi
      .listPublic()
      .then((data) => {
        if (alive) setStores(data);
      })
      .catch(() => {
        if (alive) setError('Không tải được danh sách cửa hàng. Vui lòng thử lại.');
      })
      .finally(() => {
        if (alive) setIsLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  const sorted = useMemo(() => {
    if (!me) return stores;
    const distance = (s: PublicStore) =>
      s.latitude != null && s.longitude != null
        ? haversineKm(me.latitude, me.longitude, s.latitude, s.longitude)
        : Number.POSITIVE_INFINITY;
    return [...stores].sort((a, b) => distance(a) - distance(b));
  }, [stores, me]);

  const locate = () => {
    if (!('geolocation' in navigator)) return;
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setMe({ latitude: pos.coords.latitude, longitude: pos.coords.longitude });
        setLocating(false);
      },
      () => setLocating(false),
      { enableHighAccuracy: true, timeout: 10000 }
    );
  };

  if (isLoading) {
    return (
      <div className="page-state">
        <Spinner size={28} />
      </div>
    );
  }

  if (error || stores.length === 0) {
    return (
      <EmptyState
        icon={<StoreIcon size={30} />}
        title="Chưa có cửa hàng"
        description={error ?? 'Hệ thống cửa hàng đang được cập nhật.'}
      />
    );
  }

  return (
    <div className="stores">
      <div className="page-bar">
        <div>
          <h1 className="page-bar__title">Hệ thống cửa hàng</h1>
          <p className="stores__sub">{stores.length} cơ sở · giao nhanh trong bán kính phục vụ của từng cơ sở</p>
        </div>
        <Button variant="secondary" icon={<Crosshair size={16} />} loading={locating} onClick={locate}>
          Tìm cơ sở gần tôi
        </Button>
      </div>

      <StoresMap stores={stores} />

      <ul className="stores__list">
        {sorted.map((store) => {
          const status = storeStatus(store);
          const km =
            me && store.latitude != null && store.longitude != null
              ? haversineKm(me.latitude, me.longitude, store.latitude, store.longitude)
              : null;
          return (
            <li key={store.id} className="card stores__item">
              <div className="stores__head">
                <h2 className="stores__name">{store.name}</h2>
                <Badge tone={status.tone}>{status.label}</Badge>
              </div>
              <p className="stores__line">
                <MapPin size={15} /> {store.address}
                {km != null && <span className="stores__km"> · cách bạn ~{km.toFixed(1)} km</span>}
              </p>
              <p className="stores__line">
                <Clock size={15} /> {store.openTime} – {store.closeTime} hằng ngày
              </p>
              {store.phone && (
                <a className="stores__line" href={`tel:${store.phone.replace(/\s/g, '')}`}>
                  <Phone size={15} /> {store.phone}
                </a>
              )}
              <a
                className="ui-btn ui-btn--ghost ui-btn--sm stores__dir"
                href={directionsUrl(store.latitude, store.longitude, store.address)}
                target="_blank"
                rel="noopener noreferrer"
              >
                <Navigation size={15} /> Chỉ đường
              </a>
            </li>
          );
        })}
      </ul>
    </div>
  );
};
