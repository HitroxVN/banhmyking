import { Link, useNavigate } from 'react-router-dom';
import { Info, Receipt, Sandwich, ShoppingBag, Trash2 } from 'lucide-react';
import { useCart } from '../context/useCart';
import { Button, EmptyState, QuantityStepper, Spinner, useConfirm, useToast } from '../components/ui';
import { formatCurrency } from '../utils/formatters';
import type { CartItem } from '../types/cart';
import { PriceTag } from '../components/product/PriceTag';
import { ComboContents } from '../components/product/ComboContents';
import { cartUnitCompareAt } from '../utils/pricing';
import '../styles/components/order.css';

export const CartPage = () => {
  const {
    cart,
    isLoading,
    isUpdating,
    error,
    subtotal,
    savingsAmount,
    totalQuantity,
    updateQuantity,
    removeItem,
    clearCart,
  } = useCart();
  const navigate = useNavigate();
  const confirm = useConfirm();
  const toast = useToast();

  const items = cart?.items ?? [];
  const isEmpty = items.length === 0;

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
            <Link to="/menu">Thực đơn</Link> / Giỏ hàng
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
          action={<Button onClick={() => navigate('/menu')}>Xem thực đơn</Button>}
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
                    {item.productType === 'COMBO' && (
                      <ComboContents
                        items={(item.comboItems ?? []).map((c) => ({ name: c.name, quantity: c.quantity }))}
                      />
                    )}
                    <p className="cart-row__unit">
                      Đơn giá <PriceTag price={item.unitPrice} compareAt={cartUnitCompareAt(item)} />
                    </p>
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
              <div className="summary__row">
                <span>Tạm tính ({totalQuantity} món)</span>
                <span>{formatCurrency(subtotal)}</span>
              </div>
              {savingsAmount > 0 && (
                <div className="summary__row summary__row--free">
                  <span>Bạn tiết kiệm được</span>
                  <span>{formatCurrency(savingsAmount)}</span>
                </div>
              )}
              <div className="summary__row">
                <span>Phí giao hàng</span>
                <span>Tính ở bước thanh toán</span>
              </div>

              <div className="summary__divider" />

              <div className="summary__total">
                <span className="summary__total-label">Tạm tính</span>
                <span className="summary__total-price">{formatCurrency(subtotal)}</span>
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
