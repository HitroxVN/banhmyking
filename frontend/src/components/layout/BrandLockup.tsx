import { Sandwich } from 'lucide-react';
import { useSiteSettings } from '../../context/useSiteSettings';
import { MiniBanhMi } from '../illustrations/BanhMiArt';
import { ChiliDoodle, CucumberDoodle, LeafDoodle } from '../illustrations/FoodDoodles';

/**
 * Khối nhận diện ở đầu các trang xác thực (đăng nhập, đăng ký, quên/đặt lại mật khẩu,
 * xác thực email). Tên web lấy từ cấu hình admin.
 *
 * Dòng mô tả bên dưới (`.brand-tagline`) là nội dung riêng của từng trang nên để tại chỗ,
 * không gộp vào đây.
 *
 * Kèm luôn mấy hình rau / ớt / bánh mì trôi quanh thẻ (chỉ trang trí, ẩn trên màn hẹp)
 * để trang nào dùng lockup cũng có, khỏi lặp markup ở từng trang.
 */
export const BrandLockup = () => {
  const { settings } = useSiteSettings();

  return (
    <>
      <div className="auth-doodles" aria-hidden="true">
        <LeafDoodle size={46} className="auth-doodle auth-doodle--leaf" />
        <ChiliDoodle size={38} className="auth-doodle auth-doodle--chili" />
        <CucumberDoodle size={40} className="auth-doodle auth-doodle--cucumber" />
        <span className="auth-doodle auth-doodle--banhmi">
          <MiniBanhMi size={84} />
        </span>
      </div>
      <div className="brand-logo-badge" title={settings.siteName}>
        <Sandwich size={30} />
      </div>
      {/* Tên thương hiệu hiển thị in hoa như trước giờ, admin nhập chữ thường hay hoa đều được */}
      <h1 className="brand-title">{settings.siteName.toUpperCase()}</h1>
    </>
  );
};
