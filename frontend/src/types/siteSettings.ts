/**
 * Nội dung website do admin sửa. Tên field khớp đúng key bên backend
 * (`SiteSettingKeys`) — backend trả map key/value thẳng ra JSON nên không có bước đổi tên.
 */
export interface SiteSettings {
  /** Tên thương hiệu — header, footer, trang đăng nhập, sidebar quản trị, tiêu đề tab */
  siteName: string;
  /** Dòng phụ dưới tên thương hiệu */
  tagline: string;
  contactPhone: string;
  contactEmail: string;
  contactAddress: string;
  /** 2 dòng chạy trên thanh thông báo ở đầu trang */
  announcementPrimary: string;
  announcementSecondary: string;
  heroBadge: string;
  /** Dòng 1 của tiêu đề hero */
  heroTitle: string;
  /** Phần đầu dòng 2, màu chữ thường */
  heroTitleLead: string;
  /** Phần cuối dòng 2, được tô màu nhấn */
  heroTitleHighlight: string;
  heroDescription: string;
  /** Đường dẫn ảnh banner; rỗng = hiện icon bánh mì mặc định */
  heroImageUrl: string;
  footerDescription: string;
  /** Toạ độ cửa hàng (chuỗi số, rỗng = chưa ghim) — gốc để server tính khoảng cách giao */
  storeLatitude: string;
  storeLongitude: string;
  /** Bán kính giao hàng tối đa (km); rỗng = không giới hạn */
  deliveryMaxRadiusKm: string;
}

/**
 * Bản dùng ngay lúc chưa tải xong cấu hình (và khi API lỗi), để lần vẽ đầu tiên
 * không bị trắng tên web. Không phải nguồn sự thật thứ hai — server luôn thắng.
 */
export const DEFAULT_SITE_SETTINGS: SiteSettings = {
  siteName: 'Bánh Mỳ King',
  tagline: 'Vỏ giòn · nhân đầy',
  contactPhone: '',
  contactEmail: '',
  contactAddress: '',
  announcementPrimary: 'Nướng theo từng đơn — giao nội thành trong 30 phút',
  announcementSecondary: 'Miễn phí giao hàng cho đơn từ 200.000đ',
  heroBadge: 'Nướng theo từng đơn · giao nội thành 30 phút',
  heroTitle: 'Bánh mì nóng giòn,',
  heroTitleLead: 'giao tới tay trong',
  heroTitleHighlight: '30 phút',
  heroDescription:
    'Nướng theo từng đơn, kẹp nhân đầy đặn, đóng gói giữ giòn. Chọn món, thêm topping tuỳ thích và thanh toán chỉ trong vài bước.',
  heroImageUrl: '',
  footerDescription:
    'Bánh mì nướng theo từng đơn, kẹp nhân đầy đặn, đóng gói giữ giòn và giao nóng tới tay bạn.',
  storeLatitude: '',
  storeLongitude: '',
  deliveryMaxRadiusKm: '10',
};
