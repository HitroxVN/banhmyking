import { Sandwich } from 'lucide-react';
import { useSiteSettings } from '../../context/useSiteSettings';

/**
 * Khối nhận diện ở đầu các trang xác thực (đăng nhập, đăng ký, quên/đặt lại mật khẩu,
 * xác thực email). Tên web lấy từ cấu hình admin.
 *
 * Dòng mô tả bên dưới (`.brand-tagline`) là nội dung riêng của từng trang nên để tại chỗ,
 * không gộp vào đây.
 */
export const BrandLockup = () => {
  const { settings } = useSiteSettings();

  return (
    <>
      <div className="brand-logo-badge" title={settings.siteName}>
        <Sandwich size={26} />
      </div>
      {/* Tên thương hiệu hiển thị in hoa như trước giờ, admin nhập chữ thường hay hoa đều được */}
      <h1 className="brand-title">{settings.siteName.toUpperCase()}</h1>
    </>
  );
};
