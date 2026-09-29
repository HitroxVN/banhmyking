import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Bike, Flame, Sandwich, ShieldCheck, Star, Timer } from 'lucide-react';
import { catalogApi } from '../../api/catalogApi';
import { useSiteSettings } from '../../context/useSiteSettings';
import { Button, Skeleton } from '../ui';
import { ProductCard } from '../product/ProductCard';
import { useQuickAdd } from '../../hooks/useQuickAdd';
import type { ProductItem } from '../../types/staff';

/** Số món của dải "nổi bật" trên trang chủ — một hàng ngang, không phân trang */
const STRIP_SIZE = 8;

/** Bốn điều lò bánh luôn làm — nội dung tĩnh, mô tả đúng thứ app đang phục vụ */
const PROMISES = [
  { icon: Flame, title: 'Nướng theo từng đơn', desc: 'Bánh vào lò sau khi bạn chốt đơn, không làm sẵn từ trước.' },
  { icon: Timer, title: 'Giao nội thành 30 phút', desc: 'Đóng gói giữ giòn và giao nóng trong vòng 30 phút.' },
  { icon: Bike, title: 'Miễn phí giao hàng', desc: 'Áp dụng cho mọi đơn hàng từ 200.000đ.' },
  { icon: ShieldCheck, title: 'Thanh toán linh hoạt', desc: 'Tiền mặt khi nhận hàng hoặc chuyển khoản VietQR.' },
];

/**
 * Khối mở đầu trang chủ. Hai nút đều dẫn về thực đơn vì đây là trang giới thiệu —
 * không còn đổi bộ lọc tại chỗ như hồi hero nằm chung với lưới món.
 */
export const HeroSection = () => {
  const { settings } = useSiteSettings();

  const scrollToFeatured = () => {
    document.getElementById('home-featured')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  return (
    <section className="menu__hero">
      {/* Vệt sáng ấm mờ sau nội dung — tạo chiều sâu mà không cần ảnh nền */}
      <span className="menu__hero-glow" aria-hidden="true" />

      <div className="menu__hero-copy">
        {settings.heroBadge && (
          <span className="menu__hero-badge">
            <span className="menu__hero-badge-dot" aria-hidden="true" />
            {settings.heroBadge}
          </span>
        )}
        <h1 className="menu__hero-title">
          {settings.heroTitle}
          <br />
          {settings.heroTitleLead} <em>{settings.heroTitleHighlight}</em>
        </h1>
        <p className="menu__hero-desc">{settings.heroDescription}</p>

        <div className="menu__hero-actions">
          <Link className="ui-btn ui-btn--primary ui-btn--lg" to="/menu">
            <Sandwich size={18} />
            Đặt ngay
          </Link>
          <Button size="lg" variant="secondary" icon={<Star size={18} />} onClick={scrollToFeatured}>
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
        {settings.heroImageUrl ? (
          <span className="menu__hero-art-inner menu__hero-art-inner--photo">
            <img src={settings.heroImageUrl} alt="" />
          </span>
        ) : (
          <span className="menu__hero-art-inner">
            <Sandwich size={104} strokeWidth={1.2} />
          </span>
        )}
        <span className="menu__hero-chip menu__hero-chip--a">
          <Flame size={15} />
          Vỏ giòn
        </span>
        <span className="menu__hero-chip menu__hero-chip--b">
          <Sandwich size={15} />
          Nhân đầy
        </span>
      </div>
    </section>
  );
};

/** Dải món nổi bật của trang chủ — tự hỏi server một lượt, không phụ thuộc bộ lọc nào. */
export const FeaturedStrip = () => {
  const [products, setProducts] = useState<ProductItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const { quickAddingId, quickAdd } = useQuickAdd();

  useEffect(() => {
    let cancelled = false;
    catalogApi
      .getProducts({ featured: true, sort: 'FEATURED', size: STRIP_SIZE })
      .then((res) => {
        if (!cancelled) setProducts(res.content);
      })
      .catch(() => {
        if (!cancelled) setProducts([]);
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Lỗi mạng hoặc lò chưa đánh dấu món nổi bật: ẩn hẳn khối, không để khung rỗng
  if (!isLoading && products.length === 0) return null;

  return (
    <section className="menu__section" id="home-featured">
      <div className="menu__section-head">
        <div>
          <h2 className="menu__section-title">
            <Flame size={22} />
            Món nổi bật
          </h2>
          <p className="menu__section-sub">Khách gọi nhiều nhất tuần này</p>
        </div>
        <Link className="menu__section-link" to="/menu">
          Xem toàn bộ thực đơn
        </Link>
      </div>
      <div className="menu__featured">
        {isLoading
          ? Array.from({ length: 4 }, (_, index) => <Skeleton key={index} variant="card" />)
          : products.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                onQuickAdd={quickAdd}
                isQuickAdding={quickAddingId === product.id}
              />
            ))}
      </div>
    </section>
  );
};

/** Khối "Cam kết của lò bánh" — dùng chung cho trang chủ và trang thực đơn. */
export const PromiseGrid = () => (
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
);
