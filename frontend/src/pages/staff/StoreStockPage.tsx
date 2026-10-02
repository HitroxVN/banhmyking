import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { PackageCheck, Search } from 'lucide-react';
import { storeInventoryApi } from '../../api/storeInventoryApi';
import { Badge, Button, EmptyState, Input, PageHeader, Skeleton, useToast } from '../../components/ui';
import { StockAdjustModal } from '../../components/staff/StockAdjustModal';
import { useStoreScope } from '../../context/useStoreScope';
import { formatCurrency } from '../../utils/formatters';
import type { StoreStockItem } from '../../types/store';
import '../../styles/components/staff-menu.css';

/** Tình trạng món tại cơ sở: bật/tắt hết món, nhập/điều chỉnh tồn (spec §5) */
export const StoreStockPage = () => {
  const { storeId, storeName } = useStoreScope();
  const toast = useToast();
  const [items, setItems] = useState<StoreStockItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [stockItem, setStockItem] = useState<StoreStockItem | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  // Số thứ tự lần tải — phản hồi của cơ sở cũ về muộn sau khi đã đổi cơ sở thì bỏ qua.
  const loadSeq = useRef(0);

  // silent: tải lại nền sau khi bật/tắt hoặc chỉnh tồn — giữ lưới và vị trí cuộn, không hiện skeleton
  const load = useCallback(async (silent = false) => {
    if (storeId == null) return;
    const seq = ++loadSeq.current;
    if (!silent) setIsLoading(true);
    try {
      const data = await storeInventoryApi.list(storeId);
      if (seq === loadSeq.current) setItems(data);
    } catch (err) {
      if (seq === loadSeq.current) {
        toast.error(err instanceof Error ? err.message : 'Không tải được tình trạng món');
      }
    } finally {
      if (seq === loadSeq.current) setIsLoading(false);
    }
  }, [storeId, toast]);

  // ADMIN đổi cơ sở: đóng modal điều chỉnh tồn và xoá lưới của cơ sở cũ, để không thể ghi tồn
  // của món cơ sở cũ vào cơ sở mới (storeId của modal lấy theo cơ sở hiện tại). Reset ngay trong
  // render (mẫu "điều chỉnh state khi prop đổi" của React) để không có khung hình nào còn modal cũ.
  const [shownStoreId, setShownStoreId] = useState(storeId);
  if (shownStoreId !== storeId) {
    setShownStoreId(storeId);
    setStockItem(null);
    setItems([]);
    setIsLoading(true);
  }

  useEffect(() => {
    void load();
  }, [load]);

  const visible = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    return q ? items.filter((i) => i.productName.toLowerCase().includes(q)) : items;
  }, [items, keyword]);

  const replace = (updated: StoreStockItem) =>
    setItems((prev) => prev.map((i) => (i.productId === updated.productId ? updated : i)));

  const toggle = async (item: StoreStockItem) => {
    if (storeId == null) return;
    setBusyId(item.productId);
    try {
      const updated = await storeInventoryApi.setAvailability(storeId, item.productId, !item.available);
      replace(updated);
      toast.success(`${updated.productName}: ${updated.available ? 'mở bán lại' : 'đã báo hết món'}`);
      // Báo hết / mở lại một món lẻ đổi trạng thái "Tạm hết do" của combo chứa nó → tải lại danh sách
      if (updated.productType !== 'COMBO' && items.some((i) => i.productType === 'COMBO')) {
        void load(true);
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Cập nhật thất bại');
    } finally {
      setBusyId(null);
    }
  };

  if (storeId == null) {
    return (
      <EmptyState
        icon={<PackageCheck size={30} />}
        title="Chưa chọn cơ sở"
        description="Chọn cơ sở ở thanh bên để xem tình trạng món."
      />
    );
  }

  return (
    <>
      <PageHeader
        title="Tình trạng món"
        subtitle={`${storeName ?? 'Cơ sở'} — bật/tắt hết món và quản lý tồn kho tại cơ sở. Món, giá do quản trị viên sửa ở thực đơn chung.`}
      />
      <div className="smenu__actions">
        <Input
          type="search"
          icon={<Search size={16} />}
          placeholder="Tìm món theo tên..."
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
        />
      </div>
      {isLoading ? (
        <Skeleton variant="card" />
      ) : (
        <div className="smenu__grid">
          {visible.map((item) => (
            <article
              key={item.productId}
              className={`card smenu__card${
                item.available && item.onChainMenu && (item.blockedBy ?? []).length === 0 ? '' : ' smenu__card--out'
              }`}
            >
              <div className="card__body">
                <h3 className="smenu__name">{item.productName}</h3>
                <span className="smenu__price">{formatCurrency(item.price)}</span>
                {!item.onChainMenu && <Badge tone="neutral">Đã ngừng bán toàn chuỗi</Badge>}
                {item.productType === 'COMBO' ? (
                  // Combo không có tồn riêng (spec §4.1): chỉ bật/tắt; thiếu thành phần thì báo lý do
                  <div className="smenu__stock-row">
                    <Badge tone="info">Combo</Badge>
                    {(item.blockedBy ?? []).length > 0 && (
                      <span className="smenu__stock smenu__stock--low">
                        Tạm hết do: {(item.blockedBy ?? []).join(', ')}
                      </span>
                    )}
                  </div>
                ) : (
                  <div className="smenu__stock-row">
                    <span className={`smenu__stock${item.lowStock ? ' smenu__stock--low' : ''}`}>
                      {item.stockQuantity == null ? 'Chưa quản tồn' : `Tồn kho: ${item.stockQuantity}`}
                    </span>
                    <Button size="sm" variant="ghost" onClick={() => setStockItem(item)}>
                      Nhập / điều chỉnh
                    </Button>
                  </div>
                )}
                <Button
                  variant={item.available ? 'secondary' : 'danger'}
                  disabled={!item.onChainMenu}
                  loading={busyId === item.productId}
                  onClick={() => void toggle(item)}
                >
                  {item.available ? 'Đang bán — báo hết món' : 'Đang hết — mở bán lại'}
                </Button>
              </div>
            </article>
          ))}
        </div>
      )}
      {stockItem && (
        <StockAdjustModal
          storeId={storeId}
          item={stockItem}
          onClose={() => setStockItem(null)}
          onAdjusted={(updated) => {
            replace(updated);
            setStockItem(null);
            // Tồn món lẻ đổi → "Tạm hết do" của combo có thể đổi theo
            if (items.some((i) => i.productType === 'COMBO')) {
              void load(true);
            }
          }}
        />
      )}
    </>
  );
};
