import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { SearchX, Ticket } from 'lucide-react';
import { promotionApi } from '../api/promotionApi';
import { Button, EmptyState, Skeleton } from '../components/ui';
import { formatCurrency, formatDate } from '../utils/formatters';
import { describePromotionValue } from '../utils/promotion';
import type { WalletPromotion } from '../types/promotion';
import '../styles/components/wallet.css';

export const PromotionsPage = () => {
  const navigate = useNavigate();
  const [wallet, setWallet] = useState<WalletPromotion[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    promotionApi
      .getWallet()
      .then((items) => {
        if (!cancelled) setWallet(items);
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Không tải được ví mã.');
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [reloadKey]);

  const available = wallet.filter((item) => !item.used);
  const used = wallet.filter((item) => item.used);

  return (
    <div>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">Thực đơn / Ưu đãi</p>
          <h1 className="page-bar__title">Ví mã giảm giá</h1>
        </div>
        <Button variant="secondary" icon={<Ticket size={17} />} onClick={() => navigate('/menu')}>
          Đặt món mới
        </Button>
      </div>

      {isLoading && (
        <div className="wallet__list">
          <Skeleton variant="row" count={4} />
        </div>
      )}

      {!isLoading && error && (
        <EmptyState
          icon={<SearchX size={30} />}
          title="Chưa tải được ví mã"
          description={error}
          action={<Button onClick={() => setReloadKey((key) => key + 1)}>Thử lại</Button>}
        />
      )}

      {!isLoading && !error && wallet.length === 0 && (
        <EmptyState
          icon={<Ticket size={30} />}
          title="Ví của bạn đang trống"
          description="Hiện chưa có mã giảm giá nào dùng được. Ưu đãi mới sẽ hiện ở đây."
          action={<Button onClick={() => navigate('/menu')}>Xem thực đơn</Button>}
        />
      )}

      {!isLoading && !error && wallet.length > 0 && (
        <>
          <section className="wallet__section">
            <h2 className="wallet__section-title">
              Dùng được ngay <span className="wallet__count">{available.length}</span>
            </h2>

            {available.length === 0 ? (
              <p className="wallet__hint">Bạn đã dùng hết các mã đang phát hành.</p>
            ) : (
              <div className="wallet__list">
                {available.map((promo) => (
                  <article className="wallet-card" key={promo.code}>
                    <span className="wallet-card__stub" aria-hidden="true">
                      <Ticket size={24} />
                    </span>
                    <div className="wallet-card__body">
                      <div className="wallet-card__head">
                        <span className="wallet-card__code">{promo.code}</span>
                        <span className="wallet-card__value">{describePromotionValue(promo)}</span>
                      </div>
                      {promo.description && <p className="wallet-card__desc">{promo.description}</p>}
                      <p className="wallet-card__meta">
                        {promo.minOrderAmount
                          ? `Đơn từ ${formatCurrency(promo.minOrderAmount)}`
                          : 'Không yêu cầu đơn tối thiểu'}
                        {promo.endsAt && ` · HSD ${formatDate(promo.endsAt)}`}
                      </p>
                      <Button size="sm" onClick={() => navigate('/cart')}>
                        Dùng ngay
                      </Button>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>

          <section className="wallet__section">
            <h2 className="wallet__section-title">
              Đã dùng <span className="wallet__count">{used.length}</span>
            </h2>

            {used.length === 0 ? (
              <p className="wallet__hint">Bạn chưa dùng mã nào.</p>
            ) : (
              <div className="wallet__list">
                {used.map((promo) => (
                  <article className="wallet-card wallet-card--used" key={promo.code}>
                    <span className="wallet-card__stub" aria-hidden="true">
                      <Ticket size={24} />
                    </span>
                    <div className="wallet-card__body">
                      <div className="wallet-card__head">
                        <span className="wallet-card__code">{promo.code}</span>
                        <span className="wallet-card__value">{describePromotionValue(promo)}</span>
                      </div>
                      <p className="wallet-card__meta">
                        {promo.discountApplied != null && `Đã giảm ${formatCurrency(promo.discountApplied)}`}
                        {promo.usedAt && ` · ${formatDate(promo.usedAt)}`}
                      </p>
                      {promo.orderCode && (
                        <Link className="wallet-card__order" to={`/orders/${promo.orderCode}`}>
                          Xem đơn {promo.orderCode}
                        </Link>
                      )}
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
};
