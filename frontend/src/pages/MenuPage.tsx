import { useEffect, useState, type KeyboardEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Percent, SearchX, SlidersHorizontal } from 'lucide-react';
import { catalogApi } from '../api/catalogApi';
import type { ProductSortValue } from '../api/catalogApi';
import { Button, ChipGroup, EmptyState, Pagination, Select, Skeleton } from '../components/ui';
import { ProductCard } from '../components/product/ProductCard';
import { PromiseGrid } from '../components/home/HomeSections';
import { useQuickAdd } from '../hooks/useQuickAdd';
import type { CategoryItem, ProductItem } from '../types/staff';
import '../styles/components/menu.css';
import '../styles/components/pricing.css';

const PAGE_SIZE = 8;
const DEFAULT_SORT: ProductSortValue = 'FEATURED';

/** Ô nhập giá: rỗng hoặc không phải số dương đều coi như không lọc */
const parsePrice = (raw: string | null): number | null => {
  if (!raw) return null;
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : null;
};

const SORT_OPTIONS: { value: ProductSortValue; label: string }[] = [
  { value: 'FEATURED', label: 'Nổi bật trước' },
  { value: 'PRICE_ASC', label: 'Giá thấp → cao' },
  { value: 'PRICE_DESC', label: 'Giá cao → thấp' },
  { value: 'NAME', label: 'Tên A → Z' },
  { value: 'NEWEST', label: 'Mới nhất' },
];

/**
 * Trang thực đơn — mở cho cả khách chưa đăng nhập, nên chỉ tải dữ liệu công khai.
 *
 * <p>Hero và dải "món nổi bật" đã chuyển sang trang chủ (`/`); ở đây chỉ còn bộ lọc và
 * lưới món để tránh hai nơi cùng làm một việc.
 */
export const MenuPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = searchParams.get('keyword') ?? '';
  const categoryParam = searchParams.get('categoryId');
  const categoryId = categoryParam ? Number(categoryParam) : null;
  const pageParam = Number(searchParams.get('page') ?? '1');
  const page = Number.isFinite(pageParam) && pageParam > 0 ? pageParam : 1;
  const sortParam = searchParams.get('sort');
  const sort = SORT_OPTIONS.some((option) => option.value === sortParam)
    ? (sortParam as ProductSortValue)
    : DEFAULT_SORT;
  const minPriceParam = searchParams.get('minPrice') ?? '';
  const maxPriceParam = searchParams.get('maxPrice') ?? '';
  const minPrice = parsePrice(minPriceParam);
  const maxPrice = parsePrice(maxPriceParam);
  // Lọc "Đang khuyến mãi" ở server (onSale=true): món lẻ có giá KM đang hiệu lực
  const onSale = searchParams.get('onSale') === '1';

  const [categories, setCategories] = useState<CategoryItem[]>([]);
  const [products, setProducts] = useState<ProductItem[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  // Ô nhập giá giữ bản nháp riêng: chỉ đẩy lên URL khi rời ô/nhấn Enter, tránh gọi API mỗi ký tự
  const [minPriceDraft, setMinPriceDraft] = useState(minPriceParam);
  const [maxPriceDraft, setMaxPriceDraft] = useState(maxPriceParam);

  const { quickAddingId, quickAdd } = useQuickAdd();

  // URL đổi từ chỗ khác (xoá lọc, back/forward) thì ô nhập phải theo kịp
  useEffect(() => {
    setMinPriceDraft(minPriceParam);
    setMaxPriceDraft(maxPriceParam);
  }, [minPriceParam, maxPriceParam]);

  useEffect(() => {
    catalogApi
      .getCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
  }, [reloadKey]);

  // Tìm/lọc/sắp xếp/phân trang đều do server làm — ở đây chỉ giữ đúng một trang
  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    catalogApi
      .getProducts({
        keyword: keyword || undefined,
        categoryId: categoryId ?? undefined,
        minPrice: minPrice ?? undefined,
        maxPrice: maxPrice ?? undefined,
        onSale: onSale || undefined,
        sort,
        page: page - 1,
        size: PAGE_SIZE,
      })
      .then((res) => {
        if (cancelled) return;
        setProducts(res.content);
        setTotalElements(res.totalElements);
        setTotalPages(res.totalPages);
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
  }, [keyword, categoryId, minPrice, maxPrice, onSale, sort, page, reloadKey]);

  // URL có thể còn ?page=5 từ lần xem trước — vượt tổng số trang thì kéo về trang cuối còn dữ liệu
  useEffect(() => {
    if (isLoading) return;
    const maxPage = Math.max(1, totalPages);
    if (page <= maxPage) return;

    const params = new URLSearchParams(searchParams);
    if (maxPage > 1) {
      params.set('page', String(maxPage));
    } else {
      params.delete('page');
    }
    setSearchParams(params, { preventScrollReset: true });
  }, [page, totalPages, isLoading, searchParams, setSearchParams]);

  /** Trang hiển thị đã kẹp vào khoảng hợp lệ — URL sai lệch chỉ còn một khung hình */
  const currentPage = Math.min(page, Math.max(1, totalPages));

  const updateParams = (next: {
    keyword?: string;
    categoryId?: number | null;
    minPrice?: number | null;
    maxPrice?: number | null;
    onSale?: boolean;
    sort?: ProductSortValue;
    page?: number;
  }) => {
    const params = new URLSearchParams();
    const nextKeyword = next.keyword !== undefined ? next.keyword : keyword;
    const nextCategory = next.categoryId !== undefined ? next.categoryId : categoryId;
    const nextMinPrice = next.minPrice !== undefined ? next.minPrice : minPrice;
    const nextMaxPrice = next.maxPrice !== undefined ? next.maxPrice : maxPrice;
    const nextOnSale = next.onSale !== undefined ? next.onSale : onSale;
    const nextSort = next.sort ?? sort;
    const nextPage = next.page ?? 1;

    if (nextKeyword) params.set('keyword', nextKeyword);
    if (nextCategory !== null && nextCategory !== undefined) params.set('categoryId', String(nextCategory));
    if (nextMinPrice) params.set('minPrice', String(nextMinPrice));
    if (nextMaxPrice) params.set('maxPrice', String(nextMaxPrice));
    if (nextOnSale) params.set('onSale', '1');
    if (nextSort !== DEFAULT_SORT) params.set('sort', nextSort);
    if (nextPage > 1) params.set('page', String(nextPage));

    setSearchParams(params, { preventScrollReset: true });
  };

  /** Đọc ô nhập và chỉ đẩy lên URL khi giá trị thật sự khác — bấm ra ngoài không nên gọi lại API */
  const commitPrice = () => {
    if (minPriceDraft === minPriceParam && maxPriceDraft === maxPriceParam) return;
    updateParams({ minPrice: parsePrice(minPriceDraft), maxPrice: parsePrice(maxPriceDraft) });
  };

  const onPriceKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      commitPrice();
    }
  };

  const hasPriceFilter = minPrice !== null || maxPrice !== null;
  const hasFilter = Boolean(keyword) || categoryId !== null || hasPriceFilter || onSale;

  const clearFilters = () =>
    updateParams({ keyword: '', categoryId: null, minPrice: null, maxPrice: null, onSale: false });

  return (
    <div className="menu">
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/">Trang chủ</Link> / Thực đơn
          </p>
          <h1 className="page-bar__title">Thực đơn</h1>
        </div>
      </div>

      <section className="menu__section" id="menu-list">
        <div className="menu__section-head">
          <div>
            <h2 className="menu__section-title">Chọn món của bạn</h2>
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
          <button
            type="button"
            className={`ui-chip menu__sale-chip${onSale ? ' ui-chip--active' : ''}`}
            aria-pressed={onSale}
            onClick={() => updateParams({ onSale: !onSale })}
          >
            <Percent size={14} /> Đang khuyến mãi
          </button>
          {/* Khoảng giá: chỉ áp dụng khi rời ô hoặc nhấn Enter */}
          <div className="menu__price">
            <input
              className="menu__price-input"
              type="number"
              inputMode="numeric"
              min={0}
              step={1000}
              placeholder="Giá từ"
              aria-label="Giá thấp nhất"
              value={minPriceDraft}
              onChange={(event) => setMinPriceDraft(event.target.value)}
              onBlur={commitPrice}
              onKeyDown={onPriceKeyDown}
            />
            <span className="menu__price-dash" aria-hidden="true">
              –
            </span>
            <input
              className="menu__price-input"
              type="number"
              inputMode="numeric"
              min={0}
              step={1000}
              placeholder="đến"
              aria-label="Giá cao nhất"
              value={maxPriceDraft}
              onChange={(event) => setMaxPriceDraft(event.target.value)}
              onBlur={commitPrice}
              onKeyDown={onPriceKeyDown}
            />
          </div>

          <Select
            aria-label="Sắp xếp thực đơn"
            className="menu__sort"
            value={sort}
            onChange={(event) => updateParams({ sort: event.target.value as ProductSortValue })}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>

          {hasFilter && (
            <button type="button" className="menu__clear" onClick={clearFilters}>
              Xoá lọc
            </button>
          )}
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
            action={<Button onClick={() => setReloadKey((key) => key + 1)}>Thử lại</Button>}
          />
        )}

        {!isLoading && !error && totalElements === 0 && (
          <EmptyState
            icon={hasFilter ? <SearchX size={30} /> : <SlidersHorizontal size={30} />}
            title="Không tìm thấy món nào"
            description={
              keyword
                ? `Không có kết quả cho "${keyword}". Thử từ khoá khác nhé.`
                : hasPriceFilter
                  ? 'Không có món nào trong khoảng giá này. Thử nới rộng khoảng giá.'
                  : 'Danh mục này hiện chưa có món nào.'
            }
            action={
              hasFilter ? (
                <Button variant="secondary" onClick={clearFilters}>
                  Xoá bộ lọc
                </Button>
              ) : undefined
            }
          />
        )}

        {!isLoading && !error && totalElements > 0 && (
          <>
            <div className="menu__meta">
              <span>
                {keyword ? `${totalElements} kết quả cho "${keyword}"` : `${totalElements} món đang bán`}
              </span>
              {totalPages > 1 && (
                <span>
                  Trang {currentPage}/{totalPages}
                </span>
              )}
            </div>

            <div className="menu__grid">
              {products.map((product) => (
                <ProductCard
                  key={product.id}
                  product={product}
                  onQuickAdd={quickAdd}
                  isQuickAdding={quickAddingId === product.id}
                />
              ))}
            </div>

            {totalPages > 1 && (
              <Pagination
                page={currentPage}
                totalPages={Math.max(1, totalPages)}
                onChange={(next) => updateParams({ page: next })}
              />
            )}
          </>
        )}
      </section>

      <PromiseGrid />
    </div>
  );
};
