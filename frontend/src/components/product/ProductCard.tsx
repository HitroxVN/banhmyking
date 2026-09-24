import { useState } from 'react';
import { Flame, Plus, Sandwich } from 'lucide-react';
import type { ProductItem } from '../../types/staff';
import { formatCurrency } from '../../utils/formatters';
import '../../styles/components/product-card.css';

export interface ProductCardProps {
  product: ProductItem;
  /** Bấm ảnh / tên món → mở modal chọn topping */
  onOpen: (product: ProductItem) => void;
  /** Bấm nút + → thêm thẳng vào giỏ với số lượng 1 */
  onQuickAdd: (product: ProductItem) => void;
  isQuickAdding?: boolean;
}

export const ProductCard = ({ product, onOpen, onQuickAdd, isQuickAdding = false }: ProductCardProps) => {
  const [imgFailed, setImgFailed] = useState(false);

  const hasOptions = (product.options?.length ?? 0) > 0;
  const disabled = !product.available;

  // Món có topping thì nút + mở modal để khách không vô tình bỏ quên lựa chọn
  const handleAdd = () => {
    if (hasOptions) onOpen(product);
    else onQuickAdd(product);
  };

  return (
    <article className={`pcard${disabled ? ' pcard--out' : ''}`}>
      <div className="pcard__media" onClick={() => onOpen(product)} role="button" tabIndex={0}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') onOpen(product);
        }}
        aria-label={`Xem chi tiết ${product.name}`}
      >
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
      </div>

      <div className="pcard__body">
        <h3 className="pcard__name" title={product.name}>
          {product.name}
        </h3>
        {product.description && <p className="pcard__desc">{product.description}</p>}

        <div className="pcard__foot">
          <span className="pcard__price">{formatCurrency(product.price)}</span>
          <button
            type="button"
            className="pcard__add"
            onClick={handleAdd}
            disabled={disabled || isQuickAdding}
            title={hasOptions ? 'Chọn topping' : 'Thêm vào giỏ'}
            aria-label={`Thêm ${product.name} vào giỏ`}
          >
            <Plus size={18} />
          </button>
        </div>
      </div>
    </article>
  );
};
