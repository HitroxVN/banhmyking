import { useEffect, useState } from 'react';
import { History } from 'lucide-react';
import { catalogApi } from '../../api/catalogApi';
import { ProductCard } from './ProductCard';
import { useQuickAdd } from '../../hooks/useQuickAdd';
import { readRecentProducts } from '../../utils/recentProducts';
import type { ProductItem } from '../../types/staff';
import '../../styles/components/menu.css';

/** Số món hiện lại — một hàng ngang trên desktop, không phân trang */
const SHOW_COUNT = 4;

export interface RecentlyViewedStripProps {
  /** Món đang mở — không gợi ý lại chính nó */
  excludeProductId?: number;
}

/**
 * "Bạn đã xem gần đây" — id món nằm trong localStorage, còn thông tin món hỏi lại server
 * để giá và tồn kho luôn đúng.
 */
export const RecentlyViewedStrip = ({ excludeProductId }: RecentlyViewedStripProps) => {
  const [products, setProducts] = useState<ProductItem[]>([]);
  const { quickAddingId, quickAdd } = useQuickAdd();

  useEffect(() => {
    const ids = readRecentProducts()
      .filter((id) => id !== excludeProductId)
      .slice(0, SHOW_COUNT);

    let cancelled = false;
    // Không có endpoint lấy nhiều món theo id, nên hỏi song song và bỏ qua món đã ngừng bán
    Promise.all(ids.map((id) => catalogApi.getProduct(id).catch(() => null))).then((items) => {
      if (!cancelled) setProducts(items.filter((item): item is ProductItem => item !== null));
    });

    return () => {
      cancelled = true;
    };
  }, [excludeProductId]);

  if (products.length === 0) return null;

  return (
    <section className="menu__section">
      <div className="menu__section-head">
        <div>
          <h2 className="menu__section-title">
            <History size={22} />
            Bạn đã xem gần đây
          </h2>
          <p className="menu__section-sub">Mở lại nhanh những món vừa xem</p>
        </div>
      </div>
      <div className="menu__featured">
        {products.map((product) => (
          <ProductCard
            key={product.id}
            product={product}
            onQuickAdd={quickAdd}
            isQuickAdding={quickAddingId === product.id}
          />
        ))}
      </div>
    </section>
  );
};
