import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { Sandwich, ShoppingCart, Star, Zap } from 'lucide-react';
import { catalogApi } from '../api/catalogApi';
import { useAuth } from '../context/useAuth';
import { useCart } from '../context/useCart';
import { Button, EmptyState, QuantityStepper, Skeleton, useToast } from '../components/ui';
import { ProductReviews } from '../components/review/ProductReviews';
import { RecentlyViewedStrip } from '../components/product/RecentlyViewedStrip';
import { rememberProduct } from '../utils/recentProducts';
import { formatCurrency } from '../utils/formatters';
import { PriceTag } from '../components/product/PriceTag';
import { formatSaleEnd, priceNow } from '../utils/pricing';
import type { OptionGroup, ProductItem, ProductOption } from '../types/staff';
import '../styles/components/product-detail.css';
import '../styles/components/pricing.css';

type OptionWithId = ProductOption & { id: number };

/** Một nhóm đã lọc sạch lựa chọn thiếu id (không gửi lên backend được). */
interface RenderableGroup extends Omit<OptionGroup, 'options'> {
  options: OptionWithId[];
}

/** Lựa chọn có id của nhóm — dùng để biết id nào thuộc nhóm nào. */
const optionIdsOf = (group: RenderableGroup): number[] => group.options.map((option) => option.id);

/** Nhãn phụ nói rõ luật của nhóm, để khách biết trước khi bị chặn. */
const groupHint = (group: RenderableGroup): string => {
  if (group.maxChoices === 1) return group.required ? 'Bắt buộc · chọn 1' : 'Chọn 1';
  if (group.maxChoices > 1) return group.required ? `Bắt buộc · tối đa ${group.maxChoices}` : `Tối đa ${group.maxChoices}`;
  return group.required ? 'Bắt buộc' : 'Không bắt buộc, có thể chọn nhiều';
};

/**
 * Trang chi tiết món — thay cho modal cũ, nên có URL riêng để chia sẻ và tải lại được.
 *
 * <p>Món có topping thì chọn topping ngay tại đây; món không có topping vẫn thêm nhanh
 * được từ lưới thực đơn như trước.
 */
export const ProductDetailPage = () => {
  const { productId: rawProductId } = useParams();
  const productId = Number(rawProductId);
  const isIdValid = Number.isInteger(productId) && productId > 0;

  const [product, setProduct] = useState<ProductItem | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [selected, setSelected] = useState<number[]>([]);
  const [quantity, setQuantity] = useState(1);
  const [isAdding, setIsAdding] = useState(false);
  const [imgFailed, setImgFailed] = useState(false);
  const [activeImage, setActiveImage] = useState(0);

  const { addItem } = useCart();
  const { isAuthenticated } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    // Id sai thì không gọi API, trạng thái lỗi suy ra ngay khi render
    if (!isIdValid) return;

    let cancelled = false;
    setIsLoading(true);
    setLoadError(null);
    setProduct(null);
    setSelected([]);
    setQuantity(1);
    setImgFailed(false);
    setActiveImage(0);

    catalogApi
      .getProduct(productId)
      .then((data) => {
        if (cancelled) return;
        setProduct(data);
        rememberProduct(data.id);
      })
      .catch(() => {
        if (!cancelled) setLoadError('Không tìm thấy món này. Có thể món đã ngừng bán.');
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [productId, isIdValid, reloadKey]);

  const options = (product?.options ?? []).filter((option): option is OptionWithId => typeof option.id === 'number');
  const optionGroups: RenderableGroup[] = (product?.optionGroups ?? [])
    .map((group) => ({
      ...group,
      options: group.options.filter((option): option is OptionWithId => typeof option.id === 'number'),
    }))
    .filter((group) => group.options.length > 0);
  // Lựa chọn phẳng cũ (group_id NULL) không thuộc nhóm nào nên không chịu luật bắt buộc/giới hạn.
  const flatOptions = options.filter((option) => option.groupId == null);
  const extraPerUnit = options
    .filter((option) => selected.includes(option.id))
    .reduce((sum, option) => sum + option.extraPrice, 0);
  // Giá đang bán do server tính (giá KM / giá combo); topping cộng thêm như cũ
  const unitPrice = (product ? priceNow(product) : 0) + extraPerUnit;
  const isCombo = product?.productType === 'COMBO';
  const total = unitPrice * quantity;

  // Ảnh đại diện đứng đầu, rồi tới bộ ảnh (bỏ ảnh trùng với ảnh đại diện)
  const gallery = useMemo(() => {
    const cover = product?.imageUrl?.trim() || '';
    const extras = (product?.images ?? []).filter((url) => url && url !== cover);
    return cover ? [cover, ...extras] : extras;
  }, [product?.imageUrl, product?.images]);
  const activeUrl = gallery[activeImage] ?? null;

  // Tồn kho theo từng cơ sở — trang này không biết cơ sở phục vụ nên chỉ xét trạng thái bán toàn chuỗi
  const maxQty = 20;
  const canBuy = Boolean(product?.available);
  const averageRating = product?.averageRating ?? 0;
  const totalReviews = product?.totalReviews ?? 0;

  /**
   * Bỏ chọn luôn được phép. Chọn thêm thì tuỳ nhóm: maxChoices = 1 thay thế lựa chọn cũ cùng
   * nhóm (hành vi radio), còn lại chặn khi đã đủ trần — chặn ngay ở UI để khách thấy phản hồi
   * tức thì, backend vẫn kiểm lại.
   */
  const toggleOption = (optionId: number, group?: RenderableGroup) => {
    if (selected.includes(optionId)) {
      setSelected(selected.filter((id) => id !== optionId));
      return;
    }
    if (!group) {
      setSelected([...selected, optionId]);
      return;
    }

    const groupIds = optionIdsOf(group);
    if (group.maxChoices === 1) {
      setSelected([...selected.filter((id) => !groupIds.includes(id)), optionId]);
      return;
    }
    const chosenInGroup = selected.filter((id) => groupIds.includes(id)).length;
    if (group.maxChoices > 0 && chosenInGroup >= group.maxChoices) {
      toast.info(`Nhóm "${group.name}" chỉ được chọn tối đa ${group.maxChoices} lựa chọn`);
      return;
    }
    setSelected([...selected, optionId]);
  };

  /** Trang này ai cũng xem được, nhưng giỏ hàng gắn với tài khoản — chưa đăng nhập thì mời đăng nhập */
  const requireLogin = () => {
    if (isAuthenticated) return true;
    toast.info('Đăng nhập để thêm món vào giỏ nhé');
    navigate('/login', { state: { from: location } });
    return false;
  };

  const addToCart = async (thenGoToCart: boolean) => {
    if (!product || !canBuy || !requireLogin()) return;

    // Chặn tại chỗ để khách biết thiếu gì, thay vì để backend trả lỗi chung chung.
    const missing = optionGroups.find(
      (group) => group.required && !group.options.some((option) => selected.includes(option.id)),
    );
    if (missing) {
      toast.error(`Vui lòng chọn ${missing.name} trước khi thêm vào giỏ`);
      return;
    }

    setIsAdding(true);
    try {
      await addItem(product.id, quantity, selected);
      if (thenGoToCart) {
        navigate('/cart');
        return;
      }
      toast.success(`Đã thêm ${quantity} × ${product.name} vào giỏ`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Thêm món vào giỏ thất bại');
    } finally {
      setIsAdding(false);
    }
  };

  if (isIdValid && isLoading) {
    return (
      <div className="pdetail-page">
        <Skeleton variant="row" count={2} />
        <div className="pdetail">
          <Skeleton variant="card" />
          <Skeleton variant="row" count={5} />
        </div>
      </div>
    );
  }

  if (loadError || !product) {
    return (
      <div className="pdetail-page">
        <EmptyState
          icon={<Sandwich size={30} />}
          title="Không xem được món này"
          description={loadError ?? (isIdValid ? 'Món không tồn tại hoặc đã ngừng bán.' : 'Đường dẫn không hợp lệ.')}
          action={
            <div className="pdetail__actions">
              <Button variant="secondary" onClick={() => setReloadKey((key) => key + 1)}>
                Thử lại
              </Button>
              <Button onClick={() => navigate('/menu')}>Về thực đơn</Button>
            </div>
          }
        />
      </div>
    );
  }

  return (
    <div className="pdetail-page">
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/">Trang chủ</Link> / <Link to="/menu">Thực đơn</Link> / {product.name}
          </p>
        </div>
      </div>

      <div className="pdetail">
        <div className="pdetail__gallery">
          <div className="pdetail__media">
            {activeUrl && !imgFailed ? (
              <img className="pdetail__img" src={activeUrl} alt={product.name} onError={() => setImgFailed(true)} />
            ) : (
              <span className="pcard__placeholder" aria-hidden="true">
                <Sandwich size={56} />
              </span>
            )}
          </div>

          {gallery.length > 1 && (
            <div className="pdetail__thumbs">
              {gallery.map((url, index) => (
                <button
                  key={url}
                  type="button"
                  className={`pdetail__thumb${index === activeImage ? ' pdetail__thumb--on' : ''}`}
                  aria-label={`Xem ảnh ${index + 1} của ${product.name}`}
                  onClick={() => {
                    setActiveImage(index);
                    setImgFailed(false);
                  }}
                >
                  <img src={url} alt="" />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="pdetail__info">
          <div className="pdetail__head">
            <h1 className="pdetail__name">{product.name}</h1>
            <div className="pdetail__meta">
              {product.categoryName && <span className="pdetail__cat">{product.categoryName}</span>}
              {totalReviews > 0 && (
                <span className="pdetail__rating">
                  <Star size={14} />
                  {averageRating.toFixed(1)}
                  <span>({totalReviews} đánh giá)</span>
                </span>
              )}
            </div>
          </div>

          <p className="pdetail__price">
            <PriceTag price={priceNow(product)} compareAt={product.compareAtPrice} />
            {(product.discountPercent ?? 0) > 0 && <span className="sale-flag">−{product.discountPercent}%</span>}
          </p>
          {product.onSale && product.saleEndsAt && (
            <p className="pdetail__sale-end">KM đến {formatSaleEnd(product.saleEndsAt)}</p>
          )}
          {isCombo && (product.comboItems ?? []).length > 0 && (
            <div className="pdetail__opts-group">
              <div className="pdetail__group-head">
                <span className="pdetail__group-title">Combo gồm</span>
                {product.compareAtPrice != null && (
                  <span className="pdetail__group-hint">Mua lẻ {formatCurrency(product.compareAtPrice)}</span>
                )}
              </div>
              <ul className="pdetail__combo">
                {(product.comboItems ?? []).map((item) => (
                  <li key={item.productId}>
                    {item.quantity} × <Link to={`/products/${item.productId}`}>{item.name}</Link>{' '}
                    <span className="pdetail__group-hint">({formatCurrency(item.price)}/phần)</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {product.available === false && <p className="pdetail__stock pdetail__stock--low">Hết hàng</p>}

          {product.description && <p className="pdetail__desc">{product.description}</p>}

          {!isCombo && optionGroups.map((group) => (
            <div key={group.id ?? group.name} className="pdetail__opts-group">
              <div className="pdetail__group-head">
                <span className="pdetail__group-title">{group.name}</span>
                <span className="pdetail__group-hint">{groupHint(group)}</span>
              </div>
              <div className="pdetail__opts">
                {group.options.map((option) => (
                  <label
                    key={option.id}
                    className={`pdetail__opt${selected.includes(option.id) ? ' pdetail__opt--on' : ''}`}
                  >
                    <input
                      type={group.maxChoices === 1 ? 'radio' : 'checkbox'}
                      name={group.maxChoices === 1 ? `option-group-${group.id ?? group.name}` : undefined}
                      checked={selected.includes(option.id)}
                      onChange={() => toggleOption(option.id, group)}
                    />
                    <span className="pdetail__opt-name">{option.name}</span>
                    <span className="pdetail__opt-price">+{formatCurrency(option.extraPrice)}</span>
                  </label>
                ))}
              </div>
            </div>
          ))}

          {!isCombo && flatOptions.length > 0 && (
            <div className="pdetail__opts-group">
              <div className="pdetail__group-head">
                <span className="pdetail__group-title">Chọn topping</span>
                <span className="pdetail__group-hint">Không bắt buộc, có thể chọn nhiều</span>
              </div>
              <div className="pdetail__opts">
                {flatOptions.map((option) => (
                  <label
                    key={option.id}
                    className={`pdetail__opt${selected.includes(option.id) ? ' pdetail__opt--on' : ''}`}
                  >
                    <input
                      type="checkbox"
                      checked={selected.includes(option.id)}
                      onChange={() => toggleOption(option.id)}
                    />
                    <span className="pdetail__opt-name">{option.name}</span>
                    <span className="pdetail__opt-price">+{formatCurrency(option.extraPrice)}</span>
                  </label>
                ))}
              </div>
            </div>
          )}

          <div className="pdetail__foot">
            <div>
              <QuantityStepper value={quantity} onChange={setQuantity} min={1} max={maxQty} size="sm" />
              <p className="pdetail__sum">
                Đơn giá {formatCurrency(unitPrice)}
                <strong>{formatCurrency(total)}</strong>
              </p>
            </div>
          </div>

          <div className="pdetail__actions">
            <Button
              variant="primary"
              size="lg"
              icon={<ShoppingCart size={18} />}
              loading={isAdding}
              disabled={!canBuy}
              onClick={() => addToCart(false)}
            >
              {canBuy ? `Thêm vào giỏ — ${formatCurrency(total)}` : 'Món đang hết'}
            </Button>
            <Button
              variant="secondary"
              size="lg"
              icon={<Zap size={18} />}
              disabled={!canBuy || isAdding}
              onClick={() => addToCart(true)}
            >
              Mua ngay
            </Button>
          </div>
          <p className="pdetail__note">Thanh toán khi nhận hàng hoặc chuyển khoản VietQR ở bước sau.</p>
        </div>
      </div>

      <ProductReviews productId={product.id} />

      <RecentlyViewedStrip excludeProductId={product.id} />
    </div>
  );
};
