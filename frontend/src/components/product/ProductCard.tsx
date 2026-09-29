import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Flame, Plus, Sandwich, Star } from 'lucide-react';
import type { ProductItem } from '../../types/staff';
import { formatCurrency } from '../../utils/formatters';
import '../../styles/components/product-card.css';

export interface ProductCardProps {
  product: ProductItem;
  /** Bấm nút + (món không có topping) → thêm thẳng vào giỏ với số lượng 1 */
  onQuickAdd: (product: ProductItem) => void;
  isQuickAdding?: boolean;
}

export const ProductCard = ({ product, onQuickAdd, isQuickAdding = false }: ProductCardProps) => {
  const [imgFailed, setImgFailed] = useState(false);

  const hasOptions = (product.options?.length ?? 0) > 0;
  // null = món không quản tồn: giữ nguyên hành vi cũ, chỉ chặn khi hết sạch hàng
  const stock = product.stockQuantity ?? null;
  const disabled = !product.available || (stock !== null && stock <= 0);
  const averageRating = product.averageRating ?? 0;
  const totalReviews = product.totalReviews ?? 0;
  const detailPath = `/products/${product.id}`;

  return (
    <article className={`pcard${disabled ? ' pcard--out' : ''}`}>
      <Link className="pcard__media" to={detailPath} aria-label={`Xem chi tiết ${product.name}`}>
        {product.imageUrl && !imgFailed ? (
          <img
            className="pcard__img"
            src={product.imageUrl}
            alt={product.name}
            loading="lazy"
            onError={() => setImgFailed(true)}
          />
        ) : (
          <span className="pcard__placeholder" aria-hidden="true">
            <Sandwich size={42} />
          </span>
        )}

        {disabled && <span className="pcard__veil">Hết món</span>}
        {!disabled && product.featured && (
          <span className="pcard__flag pcard__flag--hot">
            <Flame size={12} />
            Nổi bật
          </span>
        )}

        {/* Món chưa có đánh giá thì không hiện — tránh chip "0.0 (0)" vô nghĩa */}
        {!disabled && totalReviews > 0 && (
          <span className="pcard__rating">
            <Star size={12} />
            {averageRating.toFixed(1)}
            <span className="pcard__rating-count">({totalReviews})</span>
          </span>
        )}
      </Link>

      <div className="pcard__body">
        <h3 className="pcard__name" title={product.name}>
          <Link className="pcard__name-link" to={detailPath}>
            {product.name}
          </Link>
        </h3>
        {product.description && <p className="pcard__desc">{product.description}</p>}

        {/* Món có topping: nút + dẫn sang trang chi tiết để khách không vô tình bỏ quên lựa chọn */}
        {hasOptions && !disabled && <span className="pcard__hint">Chọn topping</span>}

        <div className="pcard__foot">
          <span className="pcard__price">{formatCurrency(product.price)}</span>
          {stock !== null && (
            <span className={`pcard__stock${product.lowStock || stock <= 0 ? ' pcard__stock--low' : ''}`}>
              {stock <= 0 ? 'Hết hàng' : product.lowStock ? `Sắp hết: ${stock}` : `Còn ${stock}`}
            </span>
          )}
          {hasOptions && !disabled ? (
            <Link
              className="pcard__add"
              to={detailPath}
              title="Chọn topping"
              aria-label={`Chọn topping cho ${product.name}`}
            >
              <Plus size={18} />
            </Link>
          ) : (
            <button
              type="button"
              className="pcard__add"
              onClick={() => onQuickAdd(product)}
              disabled={disabled || isQuickAdding}
              title="Thêm vào giỏ"
              aria-label={`Thêm ${product.name} vào giỏ`}
            >
              <Plus size={18} />
            </button>
          )}
        </div>
      </div>
    </article>
  );
};
