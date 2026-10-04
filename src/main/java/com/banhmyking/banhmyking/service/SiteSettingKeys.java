package com.banhmyking.banhmyking.service;

import java.util.Map;

/**
 * Danh sách giá trị nội dung website mà admin sửa được — ĐÂY là file cần sửa khi muốn
 * thêm một giá trị mới: thêm 1 hằng số + 1 dòng trong {@link #DEFAULTS}, không cần migration.
 *
 * Key đặt camelCase trùng đúng tên field mà frontend dùng, nên Jackson trả map thẳng ra
 * JSON không qua bước đổi tên — tránh lệch tên giữa hai đầu.
 */
public final class SiteSettingKeys {

    public static final String SITE_NAME = "siteName";
    public static final String TAGLINE = "tagline";
    public static final String CONTACT_PHONE = "contactPhone";
    public static final String CONTACT_EMAIL = "contactEmail";
    public static final String CONTACT_ADDRESS = "contactAddress";
    public static final String ANNOUNCEMENT_PRIMARY = "announcementPrimary";
    public static final String ANNOUNCEMENT_SECONDARY = "announcementSecondary";
    public static final String HERO_BADGE = "heroBadge";
    public static final String HERO_TITLE = "heroTitle";
    /** Phần đầu dòng 2 của tiêu đề, màu chữ thường. */
    public static final String HERO_TITLE_LEAD = "heroTitleLead";
    /** Phần cuối dòng 2, được tô màu nhấn. */
    public static final String HERO_TITLE_HIGHLIGHT = "heroTitleHighlight";
    public static final String HERO_DESCRIPTION = "heroDescription";
    public static final String HERO_IMAGE_URL = "heroImageUrl";
    public static final String FOOTER_DESCRIPTION = "footerDescription";
    /** Toạ độ quán — gốc để server tính khoảng cách giao. Rỗng = chưa ghim, phí ship tính theo khu vực. */
    public static final String STORE_LATITUDE = "storeLatitude";
    public static final String STORE_LONGITUDE = "storeLongitude";
    /** Bán kính phục vụ (km, theo quãng đường ước tính). Rỗng = không giới hạn. */
    public static final String DELIVERY_MAX_RADIUS_KM = "deliveryMaxRadiusKm";

    /** Value dài hơn mức này bị từ chối — chặn một lần dán nhầm cả file vào ô cấu hình. */
    public static final int MAX_VALUE_LENGTH = 2000;

    /**
     * Key → giá trị mặc định (nguyên văn text đang hardcode ở frontend).
     * Cũng chính là allow-list: key không có trong đây bị từ chối, để một lỗi typo
     * không đẻ ra dòng cấu hình không ai đọc.
     *
     * Ba thông tin liên hệ mặc định để rỗng — footer chỉ hiện cột "Liên hệ" khi admin điền.
     */
    public static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry(SITE_NAME, "Bánh Mỳ King"),
            Map.entry(TAGLINE, "Vỏ giòn · nhân đầy"),
            Map.entry(CONTACT_PHONE, ""),
            Map.entry(CONTACT_EMAIL, ""),
            Map.entry(CONTACT_ADDRESS, ""),
            Map.entry(ANNOUNCEMENT_PRIMARY, "Nướng theo từng đơn — giao nội thành trong 30 phút"),
            Map.entry(ANNOUNCEMENT_SECONDARY, "Miễn phí giao hàng cho đơn từ 200.000đ"),
            Map.entry(HERO_BADGE, "Nướng theo từng đơn · giao nội thành 30 phút"),
            Map.entry(HERO_TITLE, "Bánh mì nóng giòn,"),
            Map.entry(HERO_TITLE_LEAD, "giao tới tay trong"),
            Map.entry(HERO_TITLE_HIGHLIGHT, "30 phút"),
            Map.entry(HERO_DESCRIPTION,
                    "Nướng theo từng đơn, kẹp nhân đầy đặn, đóng gói giữ giòn. "
                            + "Chọn món, thêm topping tuỳ thích và thanh toán chỉ trong vài bước."),
            Map.entry(HERO_IMAGE_URL, ""),
            Map.entry(FOOTER_DESCRIPTION,
                    "Bánh mì nướng theo từng đơn, kẹp nhân đầy đặn, đóng gói giữ giòn "
                            + "và giao nóng tới tay bạn."),
            Map.entry(STORE_LATITUDE, ""),
            Map.entry(STORE_LONGITUDE, ""),
            Map.entry(DELIVERY_MAX_RADIUS_KM, "10"));

    private SiteSettingKeys() {
    }
}
