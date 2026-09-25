import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Info, Receipt, Sandwich, ShoppingBag, Trash2 } from 'lucide-react';
import { useCart } from '../context/useCart';
import { deliveryApi } from '../api/deliveryApi';
import { Button, EmptyState, QuantityStepper, Spinner, useConfirm, useToast } from '../components/ui';
import { formatCurrency } from '../utils/formatters';
import type { CartItem } from '../types/cart';
import type { DeliveryFeeResult } from '../types/delivery';
import '../styles/components/order.css';

export const CartPage = () => {
  const { cart, isLoading, isUpdating, error, subtotal, totalQuantity, updateQuantity, removeItem, clearCart } =
    useCart();
  const navigate = useNavigate();
  const confirm = useConfirm();
  const toast = useToast();

  const [fee, setFee] = useState<DeliveryFeeResult | null>(null);
  const [isLoadingFee, setIsLoadingFee] = useState(false);

  // Ngưỡng + phí ship lấy từ backend, không hardcode ở FE
  useEffect(() => {
    if (subtotal <= 0) {
      setFee(null);
      return;
    }

    let cancelled = false;
    setIsLoadingFee(true);
    deliveryApi
      .getFee({ subtotal })
      .then((result) => {
        if (!cancelled) setFee(result);
      })
      .catch(() => {
        if (!cancelled) setFee(null);
      })
      .finally(() => {
        if (!cancelled) setIsLoadingFee(false);
      });

    return () => {
      cancelled = true;
    };
  }, [subtotal]);

  const items = cart?.items ?? [];
  const isEmpty = items.length === 0;
  const shippingFee = fee?.shippingFee ?? 0;
  const threshold = fee?.freeshipThreshold ?? 0;
  const total = subtotal + shippingFee;
  const progress = threshold > 0 ? Math.min(100, Math.round((subtotal / threshold) * 100)) : 0;

  const handleQuantity = async (item: CartItem, quantity: number) => {
    try {
      await updateQuantity(item.id, quantity);
    } catch {
      toast.error('Không cập nhật được số lượng món');
    }
  };

  const handleRemove = async (item: CartItem) => {
    const accepted = await confirm({
      title: 'Xoá món khỏi giỏ',
      message: `Bạn chắc chắn muốn xoá "${item.productName}" khỏi giỏ hàng?`,
      confirmText: 'Xoá món',
      danger: true,
    });
    if (!accepted) return;

    try {
      await removeItem(item.id);
      toast.success(`Đã xoá "${item.productName}" khỏi giỏ`);
    } catch {
      toast.error('Không xoá được món ăn');
    }
  };

  const handleClearAll = async () => {
    const accepted = await confirm({
      title: 'Xoá toàn bộ giỏ hàng',
      message: 'Toàn bộ món trong giỏ sẽ bị xoá. Bạn muốn tiếp tục?',
      confirmText: 'Xoá sạch',
      danger: true,
    });
    if (!accepted) return;

    try {
      await clearCart();
      toast.success('Đã xoá sạch giỏ hàng');
    } catch {
      toast.error('Không xoá được giỏ hàng');
    }
  };

  if (isLoading) {
    return (
      <div className="page-state">
        <Spinner size={30} />
      </div>
    );
  }

  return (
    <>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/">Thực đơn</Link> / Giỏ hàng
          </p>
          <h1 className="page-bar__title">Giỏ hàng của bạn</h1>
        </div>
        {!isEmpty && (
          <Button variant="ghost" size="sm" icon={<Trash2 size={16} />} onClick={handleClearAll} disabled={isUpdating}>
            Xoá tất cả
          </Button>
        )}
      </div>

      {error && (
        <div className="alert-banner alert-error page-alert">
          <div>{error}</div>
        </div>
      )}

      {isEmpty ? (
        <EmptyState
          icon={<ShoppingBag size={30} />}
          title="Giỏ hàng đang trống"
          description="Thêm vài chiếc bánh mì nóng giòn rồi quay lại đây nhé."
          action={<Button onClick={() => navigate('/')}>Xem thực đơn</Button>}
        />
      ) : (
        <div className="order-grid">
          <section className="card">
            <div className="card__head">
              <h2 className="card__title">
                <ShoppingBag size={19} />
                {totalQuantity} món trong giỏ
              </h2>
            </div>
            <div className="card__body">
              {items.map((item) => (
                <div className="cart-row" key={item.id}>
                  <div className="cart-row__media">
                    {item.productImageUrl ? (
                      <img className="cart-row__img" src={item.productImageUrl} alt={item.productName} />
                    ) : (
                      <span className="pcard__placeholder" aria-hidden="true">
                        <Sandwich size={26} />
                      </span>
                    )}
                  </div>

                  <div>
                    <h3 className="cart-row__name">{item.productName}</h3>
                    {item.options.length > 0 && (
                      <p className="cart-row__opts">
                        {item.options.map((option) => (
                          <span className="cart-row__opt" key={option.id}>
                            {option.name}
                          </span>
                        ))}
                      </p>
                    )}
                    <p className="cart-row__unit">Đơn giá {formatCurrency(item.unitPrice)}</p>
                  </div>

                  <div className="cart-row__side">
                    <QuantityStepper
                      value={item.quantity}
                      onChange={(next) => handleQuantity(item, next)}
                      min={1}
                      max={20}
                      size="sm"
                      disabled={isUpdating}
                    />
                    <span className="cart-row__total">{formatCurrency(item.subtotal)}</span>
                    <Button
                      variant="ghost"
                      size="sm"
                      icon={<Trash2 size={15} />}
                      onClick={() => handleRemove(item)}
                      disabled={isUpdating}
                      aria-label={`Xoá ${item.productName}`}
                    />
                  </div>
                </div>
              ))}
            </div>
          </section>

          <aside className="card">
            <div className="card__head">
              <h2 className="card__title">
                <Receipt size={19} />
                Tóm tắt đơn
              </h2>
            </div>
            <div className="card__body">
              {threshold > 0 && (
                <div className="freeship">
                  <p className="freeship__text">
                    {fee?.freeship
                      ? 'Đơn của bạn được miễn phí giao hàng.'
                      : `Mua thêm ${formatCurrency(Math.max(0, threshold - subtotal))} để được miễn phí giao hàng.`}
                  </p>
                  <div className="freeship__bar">
                    <div
                      className={`freeship__fill${fee?.freeship ? ' freeship__fill--done' : ''}`}
                      style={{ width: `${progress}%` }}
                    />
                  </div>
                </div>
              )}

              <div className="summary__row">
                <span>Tạm tính ({totalQuantity} món)</span>
                <span>{formatCurrency(subtotal)}</span>
              </div>
              <div className={`summary__row${fee?.freeship ? ' summary__row--free' : ''}`}>
                <span>Phí giao hàng</span>
                <span>{isLoadingFee ? 'Đang tính…' : fee?.freeship ? 'Miễn phí' : formatCurrency(shippingFee)}</span>
              </div>

              <div className="summary__divider" />

              <div className="summary__total">
                <span className="summary__total-label">Tổng cộng</span>
                <span className="summary__total-price">{formatCurrency(total)}</span>
              </div>

              <p className="summary__row summary__row--note">
                <Info size={14} />
                Phí giao hàng có thể thay đổi theo địa chỉ nhận hàng ở bước thanh toán.
              </p>
            </div>
            <div className="card__foot">
              <Button block size="lg" onClick={() => navigate('/checkout')} disabled={isUpdating}>
                Tiến hành thanh toán
              </Button>
            </div>
          </aside>
        </div>
      )}
    </>
  );
};
