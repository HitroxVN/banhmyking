import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Mail, MapPin, Phone, Sandwich } from 'lucide-react';
import { useSiteSettings } from '../../context/useSiteSettings';
import { EmptyState } from '../../components/ui';
import '../../styles/components/static-pages.css';

/**
 * Vỏ chung cho các trang nội dung tĩnh (giới thiệu, điều khoản, bảo mật, FAQ).
 *
 * <p>Nội dung nằm ngay trong file này chứ không tách mỗi trang một file: đây là văn bản
 * marketing/điều khoản, sửa cùng nhau và không có logic gì để tách.
 */
const StaticPage = ({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) => (
  <div className="static">
    <div className="page-bar">
      <div>
        <p className="page-bar__crumb">
          <Link to="/">Trang chủ</Link> / {title}
        </p>
        <h1 className="page-bar__title">{title}</h1>
        <p className="static__sub">{subtitle}</p>
      </div>
    </div>

    <article className="card static__prose">{children}</article>
  </div>
);

export const AboutPage = () => (
  <StaticPage title="Giới thiệu" subtitle="Lò bánh mì nướng theo từng đơn, giao nóng trong nội thành.">
    <p>
      Bánh Mỳ King bắt đầu từ một chiếc lò nhỏ: bánh chỉ vào lò sau khi có đơn, nhân kẹp tại chỗ và đóng gói
      giữ giòn trên đường giao. Chúng tôi không làm sẵn hàng loạt để bánh nguội trên kệ.
    </p>

    <h2>Chúng tôi làm gì mỗi ngày</h2>
    <ul className="static__list">
      <li>Nhào bột và nướng theo từng đợt đơn, không tích trữ bánh qua ngày.</li>
      <li>Kẹp nhân theo đúng lựa chọn của khách, kể cả phần topping thêm.</li>
      <li>Giao trong nội thành, đóng gói túi giấy thoát hơi để vỏ bánh còn giòn.</li>
      <li>Món hết hàng sẽ tự động khoá đặt để không nhận đơn không giao được.</li>
    </ul>

    <h2>Đặt món thế nào</h2>
    <p>
      Bạn xem thực đơn công khai, chọn món và thêm vào giỏ. Cần đăng nhập khi chốt đơn để cửa hàng biết
      giao cho ai và bạn theo dõi được trạng thái đơn. Mã giảm giá nhận được sẽ nằm trong mục Ưu đãi.
    </p>

    <p>
      Có câu hỏi khác? Xem <Link to="/faq">Câu hỏi thường gặp</Link> hoặc{' '}
      <Link to="/contact">liên hệ cửa hàng</Link>.
    </p>
  </StaticPage>
);

export const ContactPage = () => {
  const { settings } = useSiteSettings();
  const hasContact = Boolean(settings.contactPhone || settings.contactEmail || settings.contactAddress);

  return (
    <StaticPage title="Liên hệ" subtitle="Gọi điện cho cửa hàng khi cần gấp, hoặc gửi email cho các việc khác.">
      {hasContact ? (
        <div className="static__contact">
          {settings.contactPhone && (
            <a className="static__contact-item" href={`tel:${settings.contactPhone.replace(/\s/g, '')}`}>
              <span className="static__contact-icon">
                <Phone size={18} />
              </span>
              <span className="static__contact-text">
                <strong>Điện thoại</strong>
                <span>{settings.contactPhone}</span>
              </span>
            </a>
          )}

          {settings.contactEmail && (
            <a className="static__contact-item" href={`mailto:${settings.contactEmail}`}>
              <span className="static__contact-icon">
                <Mail size={18} />
              </span>
              <span className="static__contact-text">
                <strong>Email</strong>
                <span>{settings.contactEmail}</span>
              </span>
            </a>
          )}

          {settings.contactAddress && (
            <span className="static__contact-item">
              <span className="static__contact-icon">
                <MapPin size={18} />
              </span>
              <span className="static__contact-text">
                <strong>Địa chỉ</strong>
                <span>{settings.contactAddress}</span>
              </span>
            </span>
          )}
        </div>
      ) : (
        <EmptyState
          icon={<Sandwich size={28} />}
          title="Chưa có thông tin liên hệ"
          description="Quản trị viên chưa điền số điện thoại, email hay địa chỉ của cửa hàng."
        />
      )}

      <h2>Cần trao đổi về một đơn cụ thể?</h2>
      <p>
        Mở <Link to="/orders">Đơn của tôi</Link>, chọn đơn đang giao và đọc trạng thái mới nhất trước khi gọi —
        cửa hàng sẽ hỏi mã đơn (dạng <strong>BMK-…</strong>) để tra nhanh hơn.
      </p>
    </StaticPage>
  );
};

export const FaqPage = () => (
  <StaticPage title="Câu hỏi thường gặp" subtitle="Những thắc mắc hay gặp khi đặt bánh.">
    <div className="static__qa">
      <h3>Tôi có cần tài khoản để xem thực đơn không?</h3>
      <p>Không. Thực đơn, giá và đánh giá của khách đều xem được khi chưa đăng nhập. Chỉ lúc chốt đơn mới cần đăng nhập.</p>

      <h3>Phí giao hàng tính thế nào?</h3>
      <p>Phí tính theo khoảng cách từ cửa hàng tới địa chỉ nhận, trừ khi đơn đủ ngưỡng miễn phí giao. Số tiền cụ thể hiện ở bước thanh toán trước khi bạn xác nhận.</p>

      <h3>Tôi trả tiền bằng cách nào?</h3>
      <p>Tiền mặt khi nhận hàng, hoặc chuyển khoản qua mã VietQR hiện ngay trên trang thanh toán. Đơn chuyển khoản chỉ vào bếp sau khi cửa hàng ghi nhận thanh toán.</p>

      <h3>Huỷ đơn được không?</h3>
      <p>Đơn đang chờ xử lý thì huỷ được ngay trong mục Đơn của tôi. Khi bếp đã bắt đầu làm, bạn cần gọi cửa hàng — nếu đơn không thể giao, cửa hàng sẽ liên hệ để huỷ và xử lý lại tiền.</p>

      <h3>Món tôi muốn đang hết hàng?</h3>
      <p>Món quản tồn sẽ tự khoá nút thêm vào giỏ và hiện số lượng còn lại. Tồn kho cập nhật theo từng đơn nên bạn thử lại sau ít phút.</p>

      <h3>Mã giảm giá nằm ở đâu?</h3>
      <p>Trong mục <Link to="/promotions">Ưu đãi của tôi</Link>: mã còn dùng được và mã đã dùng kèm đơn tương ứng. Nếu ví trống, mã sẽ hiện khi cửa hàng phát hành thêm.</p>

      <h3>Tôi chưa nhận được email xác thực?</h3>
      <p>Kiểm tra hộp thư rác trước, rồi dùng nút gửi lại link ngay trên trang đăng nhập khi hệ thống báo tài khoản chưa xác thực.</p>
    </div>
  </StaticPage>
);

export const TermsPage = () => (
  <StaticPage
    title="Điều khoản sử dụng"
    subtitle="Áp dụng khi bạn đặt hàng trên website này. Đây là dự án đặt món quy mô nhỏ, không phải văn bản pháp lý đã qua thẩm định."
  >
    <h2>Đặt hàng</h2>
    <p>
      Đơn chỉ được ghi nhận khi hệ thống trả về mã đơn. Bạn có trách nhiệm kiểm tra địa chỉ, số điện thoại và
      nội dung đơn trước khi xác nhận — cửa hàng giao theo đúng thông tin đó.
    </p>

    <h2>Giá và thanh toán</h2>
    <p>
      Giá hiển thị là giá tại thời điểm đặt và được chốt theo đơn. Phí giao hàng và số tiền giảm hiện thành
      từng dòng trước khi bạn thanh toán.
    </p>

    <h2>Huỷ đơn</h2>
    <p>
      Đơn ở trạng thái chờ xử lý có thể huỷ từ mục Đơn của tôi. Sau khi bếp đã làm, việc huỷ do cửa hàng quyết
      định theo tình trạng thực tế của đơn.
    </p>

    <h2>Tài khoản</h2>
    <p>
      Giữ mật khẩu cho riêng mình; mọi đơn phát sinh từ tài khoản của bạn được coi là do bạn đặt. Báo ngay cho
      cửa hàng nếu thấy đơn lạ trong mục Đơn của tôi.
    </p>

    <h2>Nội dung trên website</h2>
    <p>
      Hình ảnh món có thể là ảnh minh hoạ. Khối lượng và topping thực tế theo mô tả trong thực đơn.
    </p>
  </StaticPage>
);

export const PrivacyPage = () => (
  <StaticPage
    title="Chính sách bảo mật"
    subtitle="Chúng tôi chỉ lưu những gì cần để giao được đơn hàng của bạn."
  >
    <h2>Thu thập những gì</h2>
    <ul className="static__list">
      <li>Thông tin tài khoản: họ tên, email, số điện thoại, ảnh đại diện nếu bạn tải lên.</li>
      <li>Thông tin giao hàng: tên người nhận, số điện thoại và địa chỉ của từng đơn.</li>
      <li>Dữ liệu đơn hàng: món đã đặt, số tiền, trạng thái và phương thức thanh toán.</li>
    </ul>

    <h2>Dùng để làm gì</h2>
    <p>
      Để xử lý đơn, điều phối giao hàng, hiển thị lại lịch sử đơn và trả lời khi bạn cần hỗ trợ. Chúng tôi không
      bán dữ liệu của bạn cho bên thứ ba.
    </p>

    <h2>Ai xem được</h2>
    <p>
      Nhân viên bếp và tài xế chỉ thấy phần thông tin cần cho đơn đang xử lý (tên, số điện thoại, địa chỉ giao).
      Quản trị viên xem được toàn bộ để vận hành hệ thống.
    </p>

    <h2>Lưu trong bao lâu</h2>
    <p>
      Dữ liệu đơn hàng được giữ để tra cứu lịch sử và đối soát. Bạn có thể yêu cầu chỉnh sửa thông tin cá nhân
      trong mục Hồ sơ, hoặc liên hệ cửa hàng để được hỗ trợ thêm.
    </p>

    <h2>Mật khẩu</h2>
    <p>
      Mật khẩu được lưu dưới dạng băm, hệ thống không đọc được mật khẩu gốc của bạn. Link đặt lại mật khẩu chỉ
      dùng được một lần và có thời hạn.
    </p>
  </StaticPage>
);
