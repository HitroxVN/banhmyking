import { useCallback, useEffect, useState } from 'react';
import { MapPinOff, Pencil, Plus, Store as StoreIcon, Trash2 } from 'lucide-react';
import { storeApi } from '../../api/storeApi';
import { Badge, Button, EmptyState, Input, Modal, PageHeader, Skeleton, Textarea, useConfirm, useToast } from '../../components/ui';
import { AddressMapPicker } from '../../components/address/AddressMapPicker';
import { storeStatus } from '../../components/store/storeLabels';
import { formatCurrency } from '../../utils/formatters';
import type { GeocodeResult, GeoPoint } from '../../utils/geocoding';
import type { Store, StorePayload } from '../../types/store';
import '../../styles/components/table.css';
import '../../styles/components/address-map.css';
import '../../styles/components/stores.css';

const EMPTY_FORM: StorePayload = {
  code: '',
  name: '',
  address: '',
  phone: '',
  latitude: null,
  longitude: null,
  openTime: '06:30',
  closeTime: '22:00',
  deliveryRadiusKm: 5,
  freeShipRadiusKm: 3,
  minOrderAmount: 50000,
  active: true,
};

const toForm = (store: Store): StorePayload => ({
  code: store.code,
  name: store.name,
  address: store.address,
  phone: store.phone ?? '',
  latitude: store.latitude ?? null,
  longitude: store.longitude ?? null,
  openTime: store.openTime,
  closeTime: store.closeTime,
  deliveryRadiusKm: store.deliveryRadiusKm,
  freeShipRadiusKm: store.freeShipRadiusKm,
  minOrderAmount: store.minOrderAmount,
  active: store.active,
});

/** ADMIN — danh sách và thêm/sửa/xoá cơ sở trong chuỗi */
export const AdminStoresPage = () => {
  const toast = useToast();
  const confirm = useConfirm();
  const [stores, setStores] = useState<Store[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [editing, setEditing] = useState<{ id: number | null; form: StorePayload } | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      setStores(await storeApi.adminList());
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không tải được danh sách cơ sở');
    } finally {
      setIsLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    void load();
  }, [load]);

  const setField = <K extends keyof StorePayload>(key: K, value: StorePayload[K]) =>
    setEditing((prev) => (prev ? { ...prev, form: { ...prev.form, [key]: value } } : prev));

  const handlePick = (point: GeoPoint, address: GeocodeResult | null) =>
    setEditing((prev) =>
      prev
        ? {
            ...prev,
            form: {
              ...prev.form,
              latitude: Number(point.latitude.toFixed(6)),
              longitude: Number(point.longitude.toFixed(6)),
              ...(address ? { address: address.fullAddress } : {}),
            },
          }
        : prev
    );

  const handleSave = async () => {
    if (!editing) return;
    const { id, form } = editing;
    if (!form.code.trim() || !form.name.trim() || form.address.trim().length < 5) {
      toast.error('Vui lòng nhập mã, tên và địa chỉ cơ sở');
      return;
    }
    const inRange = (v: number, min: number, max: number) => Number.isFinite(v) && v >= min && v <= max;
    if (!inRange(form.deliveryRadiusKm, 0.5, 100)) {
      toast.error('Bán kính giao phải từ 0,5 đến 100 km');
      return;
    }
    if (!inRange(form.freeShipRadiusKm, 0, 100)) {
      toast.error('Bán kính freeship phải từ 0 đến 100 km');
      return;
    }
    if (!Number.isFinite(form.minOrderAmount) || form.minOrderAmount < 0) {
      toast.error('Đơn tối thiểu không được âm');
      return;
    }
    if (!form.openTime || !form.closeTime) {
      toast.error('Vui lòng nhập giờ mở và đóng cửa');
      return;
    }
    if (form.openTime >= form.closeTime) {
      toast.error('Giờ mở cửa phải trước giờ đóng cửa');
      return;
    }
    setIsSaving(true);
    try {
      const payload = { ...form, phone: form.phone?.trim() || null };
      if (id) {
        await storeApi.adminUpdate(id, payload);
        toast.success('Đã cập nhật cơ sở');
      } else {
        await storeApi.adminCreate(payload);
        toast.success('Đã thêm cơ sở');
      }
      setEditing(null);
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Lưu cơ sở thất bại');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (store: Store) => {
    const accepted = await confirm({
      title: 'Xoá cơ sở',
      message: `Xoá ${store.name}? Cơ sở còn nhân viên hoặc còn đơn chưa xong sẽ không xoá được.`,
      confirmText: 'Xoá',
      danger: true,
    });
    if (!accepted) return;
    try {
      await storeApi.adminDelete(store.id);
      toast.success('Đã xoá cơ sở');
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Xoá cơ sở thất bại');
    }
  };

  const form = editing?.form;
  const pinned: GeoPoint | null =
    form && form.latitude != null && form.longitude != null
      ? { latitude: form.latitude, longitude: form.longitude }
      : null;

  return (
    <>
      <PageHeader
        title="Cơ sở"
        subtitle="Các cửa hàng trong chuỗi: vị trí, giờ mở cửa, bán kính giao, freeship và đơn tối thiểu."
        actions={
          <Button icon={<Plus size={17} />} onClick={() => setEditing({ id: null, form: EMPTY_FORM })}>
            Thêm cơ sở
          </Button>
        }
      />

      {isLoading ? (
        <Skeleton variant="row" />
      ) : stores.length === 0 ? (
        <EmptyState icon={<StoreIcon size={30} />} title="Chưa có cơ sở" description="Bấm Thêm cơ sở để bắt đầu." />
      ) : (
        <section className="card">
          <div className="table-wrap">
            <table className="ui-table">
              <thead>
                <tr>
                  <th>Mã</th>
                  <th>Tên / địa chỉ</th>
                  <th>Giờ</th>
                  <th>Giao hàng</th>
                  <th>Nhân sự</th>
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {stores.map((store) => {
                  const status = store.active ? storeStatus(store) : { label: 'Ngừng hoạt động', tone: 'danger' as const };
                  return (
                    <tr key={store.id}>
                      <td>{store.code}</td>
                      <td>
                        <strong>{store.name}</strong>
                        <br />
                        <small>{store.address}</small>
                        {store.latitude == null && (
                          <>
                            <br />
                            <Badge tone="warning">Chưa ghim vị trí</Badge>
                          </>
                        )}
                      </td>
                      <td>
                        {store.openTime}–{store.closeTime}
                      </td>
                      <td>
                        {store.deliveryRadiusKm} km · freeship {store.freeShipRadiusKm} km
                        <br />
                        <small>Đơn từ {formatCurrency(store.minOrderAmount)}</small>
                      </td>
                      <td>{store.staffCount}</td>
                      <td>
                        <Badge tone={status.tone}>{status.label}</Badge>
                      </td>
                      <td>
                        <Button size="sm" variant="ghost" icon={<Pencil size={15} />} onClick={() => setEditing({ id: store.id, form: toForm(store) })}>
                          Sửa
                        </Button>
                        <Button size="sm" variant="ghost" icon={<Trash2 size={15} />} onClick={() => void handleDelete(store)}>
                          Xoá
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        size="lg"
        title={editing?.id ? 'Sửa cơ sở' : 'Thêm cơ sở'}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)}>
              Huỷ
            </Button>
            <Button loading={isSaving} onClick={() => void handleSave()}>
              Lưu cơ sở
            </Button>
          </>
        }
      >
        {form && (
          <div className="addr-fields">
            <div className="addr-fields__row">
              <Input label="Mã cơ sở" required maxLength={20} value={form.code} onChange={(e) => setField('code', e.target.value)} placeholder="CS02" />
              <Input label="Tên cơ sở" required maxLength={100} value={form.name} onChange={(e) => setField('name', e.target.value)} placeholder="Cơ sở Cầu Giấy" />
            </div>
            <AddressMapPicker value={pinned} onPick={handlePick} height={260} />
            {pinned && (
              <Button type="button" size="sm" variant="ghost" icon={<MapPinOff size={15} />} onClick={() => { setField('latitude', null); setField('longitude', null); }}>
                Bỏ ghim (cơ sở sẽ phục vụ mọi địa chỉ, phí theo khu vực)
              </Button>
            )}
            <Textarea label="Địa chỉ" required rows={2} maxLength={500} value={form.address} onChange={(e) => setField('address', e.target.value)} hint="Tự điền khi ghim trên bản đồ — sửa lại nếu chưa đúng." />
            <div className="addr-fields__row">
              <Input label="Số điện thoại" maxLength={20} value={form.phone ?? ''} onChange={(e) => setField('phone', e.target.value)} />
              <label className="ui-check">
                <input type="checkbox" checked={form.active} onChange={(e) => setField('active', e.target.checked)} />
                Đang hoạt động
              </label>
            </div>
            <div className="addr-fields__row">
              <Input label="Giờ mở cửa" type="time" value={form.openTime} onChange={(e) => setField('openTime', e.target.value)} />
              <Input label="Giờ đóng cửa" type="time" value={form.closeTime} onChange={(e) => setField('closeTime', e.target.value)} />
            </div>
            <div className="addr-fields__row">
              <Input label="Bán kính giao (km)" type="number" min={0.5} max={100} step={0.5} value={form.deliveryRadiusKm} onChange={(e) => setField('deliveryRadiusKm', Number(e.target.value))} />
              <Input label="Freeship trong bán kính (km)" type="number" min={0} max={100} step={0.5} value={form.freeShipRadiusKm} onChange={(e) => setField('freeShipRadiusKm', Number(e.target.value))} hint="0 = không freeship theo khoảng cách" />
            </div>
            <Input label="Đơn tối thiểu (đ)" type="number" min={0} step={1000} value={form.minOrderAmount} onChange={(e) => setField('minOrderAmount', Number(e.target.value))} />
          </div>
        )}
      </Modal>
    </>
  );
};
