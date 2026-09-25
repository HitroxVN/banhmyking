import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Bike, Flame, Sandwich, SearchX, ShieldCheck, Star, Timer } from 'lucide-react';
import { catalogApi } from '../api/catalogApi';
import { useCart } from '../context/useCart';
import { Button, ChipGroup, EmptyState, Pagination, Skeleton, useToast } from '../components/ui';
import { ProductCard } from '../components/product/ProductCard';
import { ProductModal } from '../components/product/ProductModal';
import type { CategoryItem, ProductItem } from '../types/staff';
import '../styles/components/menu.css';

const PAGE_SIZE = 8;

/** Bốn điều lò bánh luôn làm — nội dung tĩnh, mô tả đúng thứ app đang phục vụ */
const PROMISES = [
  { icon: Flame, title: 'Nướng theo từng đơn', desc: 'Bánh vào lò sau khi bạn chốt đơn, không làm sẵn từ trước.' },
  { icon: Timer, title: 'Giao nội thành 30 phút', desc: 'Đóng gói giữ giòn và giao nóng trong vòng 30 phút.' },
  { icon: Bike, title: 'Miễn phí giao hàng', desc: 'Áp dụng cho mọi đơn hàng từ 200.000đ.' },
  { icon: ShieldCheck, title: 'Thanh toán linh hoạt', desc: 'Tiền mặt khi nhận hàng hoặc chuyển khoản VietQR.' },
];

/** Bỏ dấu tiếng Việt để tìm "banh mi" vẫn ra "Bánh mì" */
const normalize = (text: string): string =>
  text
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .trim();

export const MenuPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = searchParams.get('keyword') ?? '';
  const categoryParam = searchParams.get('categoryId');
  const categoryId = categoryParam ? Number(categoryParam) : null;
  const pageParam = Number(searchParams.get('page') ?? '1');
  const page = Number.isFinite(pageParam) && pageParam > 0 ? pageParam : 1;

  const [categories, setCategories] = useState<CategoryItem[]>([]);
  const [products, setProducts] = useState<ProductItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [quickAddingId, setQuickAddingId] = useState<number | null>(null);

  const { addItem } = useCart();
  const toast = useToast();

  useEffect(() => {
    catalogApi
      .getCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
  }, [reloadKey]);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    catalogApi
      .getProducts(categoryId ?? undefined, true)
      .then((list) => {
        if (!cancelled) setProducts(list);
      })
      .catch(() => {
        if (!cancelled) setError('Không tải được thực đơn. Vui lòng kiểm tra kết nối và thử lại.');
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [categoryId, reloadKey]);

  const filtered = useMemo(() => {
    if (!keyword) return products;
    const needle = normalize(keyword);
    return products.filter(
      (product) => normalize(product.name).includes(needle) || normalize(product.description ?? '').includes(needle)
    );
  }, [products, keyword]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const pageItems = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
  const featured = products.filter((product) => product.featured);
  const showFeatured = !keyword && categoryId === null && currentPage === 1;

  const updateParams = (next: { keyword?: string; categoryId?: number | null; page?: number }) => {
    const params = new URLSearchParams();
    const nextKeyword = next.keyword !== undefined ? next.keyword : keyword;
    const nextCategory = next.categoryId !== undefined ? next.categoryId : categoryId;
    const nextPage = next.page ?? 1;

    if (nextKeyword) params.set('keyword', nextKeyword);
    if (nextCategory !== null && nextCategory !== undefined) params.set('categoryId', String(nextCategory));
    if (nextPage > 1) params.set('page', String(nextPage));

    setSearchParams(params, { preventScrollReset: true });
  };

  /** Link "Xem toàn bộ" ở mục nổi bật — chỉ cuộn xuống lưới, không đổi bộ lọc */
  const scrollToFullList = () => {
    document.getElementById('menu-list')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const handleQuickAdd = async (product: ProductItem) => {
    setQuickAddingId(product.id);
    try {
      await addItem(product.id, 1);
      toast.success(`Đã thêm ${product.name} vào giỏ`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Thêm món vào giỏ thất bại');
    } finally {
      setQuickAddingId(null);
    }
  };

  const hasFilter = Boolean(keyword) || categoryId !== null;

  return (
    <div>
      <section className="menu__hero">
        <div className="menu__hero-copy">
          <span className="menu__hero-badge">
            <span className="menu__hero-badge-dot" aria-hidden="true" />
            Nướng theo từng đơn · giao nội thành 30 phút
          </span>
          <h1 className="menu__hero-title">
            Bánh mì nóng giòn,
            <br />
            giao tới tay trong <em>30 phút</em>
          </h1>
          <p className="menu__hero-desc">
            Nướng theo từng đơn, kẹp nhân đầy đặn, đóng gói giữ giòn. Chọn món, thêm topping tuỳ thích và thanh toán
            chỉ trong vài bước.
          </p>

          <div className="menu__hero-actions">
            <Button
              size="lg"
              icon={<Sandwich size={18} />}
              onClick={() => {
                const firstCategory = categories[0];
                updateParams({ categoryId: firstCategory ? firstCategory.id : null });
                document.getElementById('menu-list')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
              }}
            >
              Đặt ngay
            </Button>
            <Button
              size="lg"
              variant="secondary"
              icon={<Star size={18} />}
              onClick={() => {
                updateParams({ categoryId: null, keyword: '' });
                document.getElementById('menu-featured')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
              }}
            >
              Xem món nổi bật
            </Button>
          </div>

          <div className="menu__hero-facts">
            <span className="menu__hero-fact">
              <span className="menu__hero-fact-icon">
                <Timer size={18} />
              </span>
              <span className="menu__hero-fact-text">
                <strong>30 phút</strong>
                <span>Giao trong nội thành</span>
              </span>
            </span>
            <span className="menu__hero-fact">
              <span className="menu__hero-fact-icon">
                <Bike size={18} />
              </span>
              <span className="menu__hero-fact-text">
                <strong>Miễn phí</strong>
                <span>Đơn từ 200.000đ</span>
              </span>
            </span>
            <span className="menu__hero-fact">
              <span className="menu__hero-fact-icon">
                <ShieldCheck size={18} />
              </span>
              <span className="menu__hero-fact-text">
                <strong>Tươi mới</strong>
                <span>Nướng theo đơn</span>
              </span>
            </span>
          </div>
        </div>

        <div className="menu__hero-art" aria-hidden="true">
          <span className="menu__hero-art-inner">
            <Sandwich size={104} strokeWidth={1.2} />
          </span>
          <span className="menu__hero-chip menu__hero-chip--a">Vỏ giòn</span>
          <span className="menu__hero-chip menu__hero-chip--b">Nhân đầy</span>
        </div>
      </section>

      {showFeatured && featured.length > 0 && (
        <section className="menu__section" id="menu-featured">
          <div className="menu__section-head">
            <div>
              <h2 className="menu__section-title">
                <Flame size={22} />
                Món nổi bật
              </h2>
              <p className="menu__section-sub">Khách gọi nhiều nhất tuần này</p>
            </div>
            <button type="button" className="menu__section-link" onClick={scrollToFullList}>
              Xem toàn bộ {filtered.length} món
            </button>
          </div>
          <div className="menu__featured">
            {featured.map((product) => (
              <ProductCard
                key={`featured-${product.id}`}
                product={product}
                onOpen={(item) => setDetailId(item.id)}
                onQuickAdd={handleQuickAdd}
                isQuickAdding={quickAddingId === product.id}
              />
            ))}
          </div>
        </section>
      )}

      <section className="menu__section" id="menu-list">
        <div className="menu__section-head">
          <div>
            <h2 className="menu__section-title">Thực đơn</h2>
            <p className="menu__section-sub">{categories.length} danh mục</p>
          </div>
        </div>

        {/* Bám ngay dưới navbar khi cuộn để đổi danh mục không phải cuộn ngược lên */}
        <div className="menu__filterbar">
          <ChipGroup
            ariaLabel="Lọc theo danh mục"
            value={categoryId}
            onChange={(next) => updateParams({ categoryId: next })}
            options={[
              { value: null, label: 'Tất cả' },
              ...categories.map((category) => ({ value: category.id, label: category.name })),
            ]}
          />
        </div>

        {isLoading && (
          <div className="menu__grid">
            {Array.from({ length: PAGE_SIZE }, (_, index) => (
              <Skeleton key={index} variant="card" />
            ))}
          </div>
        )}

        {!isLoading && error && (
          <EmptyState
            icon={<SearchX size={30} />}
            title="Chưa tải được thực đơn"
            description={error}
            action={
              <Button onClick={() => setReloadKey((key) => key + 1)}>Thử lại</Button>
            }
          />
        )}

        {!isLoading && !error && filtered.length === 0 && (
          <EmptyState
            icon={<SearchX size={30} />}
            title="Không tìm thấy món nào"
            description={keyword ? `Không có kết quả cho "${keyword}". Thử từ khoá khác nhé.` : 'Danh mục này hiện chưa có món nào.'}
            action={
              hasFilter ? (
                <Button variant="secondary" onClick={() => updateParams({ keyword: '', categoryId: null })}>
                  Xoá bộ lọc
                </Button>
              ) : undefined
            }
          />
        )}

        {!isLoading && !error && filtered.length > 0 && (
          <>
            <div className="menu__meta">
              <span>
                {keyword ? `${filtered.length} kết quả cho "${keyword}"` : `${filtered.length} món đang bán`}
              </span>
              {totalPages > 1 && (
                <span>
                  Trang {currentPage}/{totalPages}
                </span>
              )}
            </div>

            <div className="menu__grid">
              {pageItems.map((product) => (
                <ProductCard
                  key={product.id}
                  product={product}
                  onOpen={(item) => setDetailId(item.id)}
                  onQuickAdd={handleQuickAdd}
                  isQuickAdding={quickAddingId === product.id}
                />
              ))}
            </div>

            {totalPages > 1 && (
              <Pagination page={currentPage} totalPages={totalPages} onChange={(next) => updateParams({ page: next })} />
            )}
          </>
        )}
      </section>

      <section className="menu__promise">
        <div className="menu__section-head">
          <div>
            <h2 className="menu__section-title">Cam kết của lò bánh</h2>
            <p className="menu__section-sub">Bốn điều Bánh Mỳ King luôn làm cho mỗi đơn hàng</p>
          </div>
        </div>

        <div className="menu__promise-grid">
          {PROMISES.map(({ icon: Icon, title, desc }) => (
            <article className="menu__promise-card" key={title}>
              <span className="menu__promise-icon">
                <Icon size={22} />
              </span>
              <h3 className="menu__promise-title">{title}</h3>
              <p className="menu__promise-desc">{desc}</p>
            </article>
          ))}
        </div>
      </section>

      <ProductModal productId={detailId} onClose={() => setDetailId(null)} />
    </div>
  );
};
