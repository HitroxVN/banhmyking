import { Link } from 'react-router-dom';
import { Mail, Phone, Sandwich } from 'lucide-react';
import { useSiteSettings } from '../context/useSiteSettings';
import { FeaturedStrip, HeroSection, PromiseGrid } from '../components/home/HomeSections';
import '../styles/components/landing.css';

/**
 * Trang chủ công khai — khách chưa đăng nhập vẫn xem được.
 *
 * <p>Cố ý không nhồi lưới thực đơn ở đây: lưới có lọc/phân trang nằm ở `/menu`, còn trang
 * này chỉ giới thiệu rồi đẩy khách sang đó.
 */
export const LandingPage = () => {
  const { settings } = useSiteSettings();

  return (
    <div>
      <HeroSection />
      <FeaturedStrip />
      <PromiseGrid />

      <section className="land-cta">
        <div className="land-cta__copy">
          <h2 className="land-cta__title">Đói bụng rồi?</h2>
          <p className="land-cta__desc">
            Chọn món, thêm topping tuỳ thích và đặt trong vài bước. Không cần tài khoản để xem thực đơn.
          </p>
        </div>

        <div className="land-cta__actions">
          <Link className="ui-btn ui-btn--primary ui-btn--lg" to="/menu">
            <Sandwich size={18} />
            Xem thực đơn
          </Link>
          {settings.contactPhone && (
            <a className="ui-btn ui-btn--secondary ui-btn--lg" href={`tel:${settings.contactPhone}`}>
              <Phone size={18} />
              {settings.contactPhone}
            </a>
          )}
          {settings.contactEmail && (
            <a className="ui-btn ui-btn--ghost ui-btn--lg" href={`mailto:${settings.contactEmail}`}>
              <Mail size={18} />
              Gửi email
            </a>
          )}
        </div>
      </section>
    </div>
  );
};
