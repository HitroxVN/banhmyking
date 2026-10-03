import { useNavigate } from 'react-router-dom';
import { Home, SearchX } from 'lucide-react';
import { Button } from '../components/ui';
import { BrandLockup } from '../components/layout/BrandLockup';
import { EmptyPlate } from '../components/illustrations/FoodDoodles';
import '../styles/components/auth.css';

/**
 * URL không tồn tại. Trước đây catch-all đá thẳng về "/" nên khách gõ sai link
 * vẫn thấy trang bình thường, không biết mình sai ở đâu.
 */
export const NotFoundPage = () => {
  const navigate = useNavigate();

  return (
    <div className="auth-page-wrapper street">
      <div className="auth-container">
        <div className="brand-header">
          <BrandLockup />
          <p className="brand-tagline">Không tìm thấy trang này</p>
        </div>

        <div className="auth-card notfound-card">
          {/* Đĩa trống + nhãn dán "Ối!" — chỉ để vui mắt */}
          <div className="notfound-art" aria-hidden="true">
            <span className="notfound-code">404</span>
            <EmptyPlate size={170} />
            <span className="notfound-sticker">Ối! Lạc đường rồi</span>
          </div>

          <h2 className="auth-card-title">
            404 — Không có trang này <SearchX size={20} aria-hidden="true" />
          </h2>
          <p className="auth-card-subtitle">
            Đường dẫn bạn mở không tồn tại hoặc đã đổi. Kiểm tra lại link, hoặc quay về trang chủ.
          </p>
          <p className="notfound-quip">Đĩa trống trơn — chắc ổ bánh mì ở đây đã có người ăn mất rồi!</p>

          <Button block icon={<Home size={16} />} onClick={() => navigate('/')}>
            Về trang chủ
          </Button>
        </div>
      </div>
    </div>
  );
};
