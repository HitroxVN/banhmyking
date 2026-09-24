import { useEffect, useState } from 'react';
import { ShoppingCart, Sandwich } from 'lucide-react';
import { catalogApi } from '../../api/catalogApi';
import { useCart } from '../../context/useCart';
import { Button, Modal, QuantityStepper, Spinner, useToast } from '../ui';
import { formatCurrency } from '../../utils/formatters';
import type { ProductItem, ProductOption } from '../../types/staff';
import '../../styles/components/product-modal.css';

type OptionWithId = ProductOption & { id: number };

export interface ProductModalProps {
  /** Món cần xem chi tiết; null = đóng modal */
  productId: number | null;
  onClose: () => void;
}

export const ProductModal = ({ productId, onClose }: ProductModalProps) => {
  const [product, setProduct] = useState<ProductItem | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [selected, setSelected] = useState<number[]>([]);
  const [quantity, setQuantity] = useState(1);
  const [isAdding, setIsAdding] = useState(false);
  const [imgFailed, setImgFailed] = useState(false);

  const { addItem } = useCart();
  const toast = useToast();
  const open = productId !== null;

  // Nạp lại chi tiết mỗi lần mở để giá / topping / tồn kho luôn mới
  useEffect(() => {
    if (productId === null) return;

    let cancelled = false;
    setIsLoading(true);
    setLoadError(null);
    setProduct(null);
    setSelected([]);
    setQuantity(1);
    setImgFailed(false);

    catalogApi
      .getProduct(productId)
      .then((data) => {
        if (!cancelled) setProduct(data);
      })
      .catch(() => {
        if (!cancelled) setLoadError('Không tải được thông tin món. Vui lòng thử lại.');
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [productId]);

  const options = (product?.options ?? []).filter((option): option is OptionWithId => typeof option.id === 'number');
  const extraPerUnit = options
    .filter((option) => selected.includes(option.id))
    .reduce((sum, option) => sum + option.extraPrice, 0);
  const unitPrice = (product?.price ?? 0) + extraPerUnit;
  const total = unitPrice * quantity;

  const toggleOption = (optionId: number) => {
    setSelected((prev) => (prev.includes(optionId) ? prev.filter((id) => id !== optionId) : [...prev, optionId]));
  };

  const handleAdd = async () => {
    if (!product) return;

    setIsAdding(true);
    try {
      await addItem(product.id, quantity, selected);
      toast.success(`Đã thêm ${quantity} × ${product.name} vào giỏ`);
      onClose();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Thêm món vào giỏ thất bại');
    } finally {
      setIsAdding(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      title={product?.name ?? 'Chi tiết món'}
      footer={
        product ? (
          <div className="pmodal__foot">
            <div>
              <QuantityStepper value={quantity} onChange={setQuantity} min={1} max={20} size="sm" />
              <p className="pmodal__sum">
                Đơn giá {formatCurrency(unitPrice)}
                <strong>{formatCurrency(total)}</strong>
              </p>
            </div>
            <Button
              variant="primary"
              size="lg"
              icon={<ShoppingCart size={18} />}
              loading={isAdding}
              disabled={!product.available}
              onClick={handleAdd}
            >
              {product.available ? `Thêm vào giỏ — ${formatCurrency(total)}` : 'Món đang hết'}
            </Button>
          </div>
        ) : undefined
      }
    >
      {isLoading && (
        <div className="page-state">
          <Spinner size={28} />
        </div>
      )}

      {!isLoading && loadError && <p className="alert-banner alert-error">{loadError}</p>}

      {!isLoading && product && (
        <div className="pmodal">
          <div className="pmodal__media">
            {product.imageUrl && !imgFailed ? (
              <img
                className="pmodal__img"
                src={product.imageUrl}
                alt={product.name}
                onError={() => setImgFailed(true)}
              />
            ) : (
              <span className="pcard__placeholder" aria-hidden="true">
                <Sandwich size={56} />
              </span>
            )}
          </div>

          <div>
            <span className="pmodal__price">{formatCurrency(product.price)}</span>
            {product.description && <p className="pmodal__desc">{product.description}</p>}

            {options.length > 0 && (
              <>
                <div className="pmodal__group-head">
                  <span className="pmodal__group-title">Thêm topping</span>
                  <span className="pmodal__group-hint">Không bắt buộc · chọn nhiều</span>
                </div>
                <div className="pmodal__opts">
                  {options.map((option) => (
                    <label
                      key={option.id}
                      className={`pmodal__opt${selected.includes(option.id) ? ' pmodal__opt--on' : ''}`}
                    >
                      <input
                        type="checkbox"
                        checked={selected.includes(option.id)}
                        onChange={() => toggleOption(option.id)}
                      />
                      <span className="pmodal__opt-name">{option.name}</span>
                      <span className="pmodal__opt-price">+{formatCurrency(option.extraPrice)}</span>
                    </label>
                  ))}
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </Modal>
  );
};
