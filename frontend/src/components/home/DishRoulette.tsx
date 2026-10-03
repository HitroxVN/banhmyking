import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Eye, Plus, Shuffle } from 'lucide-react';
import { catalogApi } from '../../api/catalogApi';
import { useQuickAdd } from '../../hooks/useQuickAdd';
import type { ProductItem } from '../../types/staff';
import { priceNow } from '../../utils/pricing';
import { Button } from '../ui';
import { PriceTag } from '../product/PriceTag';
import { EmptyPlate } from '../illustrations/FoodDoodles';
import { BanhMiArt } from '../illustrations/BanhMiArt';
import '../../styles/components/roulette.css';

/** Bao nhiêu món đem ra bốc — đủ đa dạng mà chỉ tốn một lần gọi API */
const POOL_SIZE = 40;
/** Thời gian "xóc thăm" trước khi lật thẻ */
const SHUFFLE_MS = 1100;
const SHUFFLE_TICK_MS = 90;

const prefersReducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

/**
 * "Hôm nay ăn gì?" — bốc ngẫu nhiên một món còn bán rồi lật thẻ cho xem.
 *
 * <p>Chỉ dùng API thực đơn sẵn có: lần bấm đầu tải một trang món còn bán, các lần sau bốc lại
 * trong danh sách đó (tránh trùng món vừa bốc). Lỗi mạng thì báo nhẹ, không chặn trang chủ.
 */
export const DishRoulette = () => {
  const [pool, setPool] = useState<ProductItem[] | null>(null);
  const [picked, setPicked] = useState<ProductItem | null>(null);
  const [rollingName, setRollingName] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const [imgFailed, setImgFailed] = useState(false);
  const timers = useRef<number[]>([]);
  const { quickAddingId, quickAdd } = useQuickAdd();

  useEffect(
    () => () => {
      timers.current.forEach((id) => window.clearTimeout(id));
    },
    [],
  );

  const isRolling = rollingName !== null;

  const loadPool = async (): Promise<ProductItem[]> => {
    if (pool) return pool;
    const res = await catalogApi.getProducts({ availableOnly: true, size: POOL_SIZE });
    const list = res.content.filter((product) => product.available !== false);
    setPool(list);
    return list;
  };

  const roll = async () => {
    if (isRolling) return;
    setFailed(false);

    let list: ProductItem[];
    try {
      list = await loadPool();
    } catch {
      setFailed(true);
      return;
    }
    if (list.length === 0) {
      setFailed(true);
      return;
    }

    const candidates = picked && list.length > 1 ? list.filter((product) => product.id !== picked.id) : list;
    const next = candidates[Math.floor(Math.random() * candidates.length)];
    setPicked(null);
    setImgFailed(false);

    if (prefersReducedMotion()) {
      setPicked(next);
      return;
    }

    // Tên món nhảy liên tục như đang xóc ống thăm, rồi dừng và lật thẻ
    setRollingName(list[0].name);
    const ticker = window.setInterval(() => {
      setRollingName(list[Math.floor(Math.random() * list.length)].name);
    }, SHUFFLE_TICK_MS);
    const stop = window.setTimeout(() => {
      window.clearInterval(ticker);
      setRollingName(null);
      setPicked(next);
    }, SHUFFLE_MS);
    timers.current.push(ticker, stop);
  };

  const hasOptions = (picked?.options?.length ?? 0) > 0;
  const detailPath = picked ? `/products/${picked.id}` : '/menu';

  return (
    <section className="roulette" aria-labelledby="roulette-title">
      <div className="roulette__copy">
        <p className="roulette__kicker">Phân vân quá?</p>
        <h2 id="roulette-title" className="roulette__title">
          Hôm nay ăn gì?
        </h2>
        <p className="roulette__desc">
          Để lò bánh bốc giùm bạn một món. Không ưng thì bốc lại, bốc bao nhiêu lần cũng được!
        </p>
        <Button size="lg" icon={<Shuffle size={18} />} onClick={roll} disabled={isRolling}>
          {isRolling ? 'Đang xóc thăm...' : picked ? 'Bốc món khác' : 'Bốc thăm món'}
        </Button>
        {failed && <p className="roulette__error">Chưa bốc được món nào, bạn thử lại sau chút nhé.</p>}
      </div>

      <div
        className={`roulette__card${picked ? ' roulette__card--open' : ''}${isRolling ? ' roulette__card--rolling' : ''}`}
      >
        <div className="roulette__inner">
          <div className="roulette__face roulette__face--front" aria-hidden={picked ? 'true' : undefined}>
            <EmptyPlate size={170} />
            <p className="roulette__rolling" aria-live="polite">
              {rollingName ?? 'Bấm bốc thăm nào!'}
            </p>
          </div>

          <div className="roulette__face roulette__face--back" aria-live="polite">
            {picked && (
              <>
                <Link className="roulette__media" to={detailPath} tabIndex={-1} aria-hidden="true">
                  {picked.imageUrl && !imgFailed ? (
                    <img src={picked.imageUrl} alt="" onError={() => setImgFailed(true)} />
                  ) : (
                    <BanhMiArt className="roulette__art" />
                  )}
                  <span className="roulette__stamp">Chốt món này!</span>
                </Link>
                <div className="roulette__info">
                  <h3 className="roulette__name">{picked.name}</h3>
                  <PriceTag className="roulette__price" price={priceNow(picked)} compareAt={picked.compareAtPrice} />
                  {picked.description && <p className="roulette__pdesc">{picked.description}</p>}
                  <div className="roulette__actions">
                    {hasOptions ? (
                      <Link className="ui-btn ui-btn--primary ui-btn--md" to={detailPath}>
                        <Plus size={16} />
                        Chọn topping
                      </Link>
                    ) : (
                      <Button
                        icon={<Plus size={16} />}
                        loading={quickAddingId === picked.id}
                        onClick={(event) => quickAdd(picked, event.currentTarget)}
                      >
                        Thêm vào giỏ
                      </Button>
                    )}
                    <Link className="ui-btn ui-btn--secondary ui-btn--md" to={detailPath}>
                      <Eye size={16} />
                      Xem món
                    </Link>
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};
