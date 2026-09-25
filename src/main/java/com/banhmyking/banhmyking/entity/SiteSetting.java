package com.banhmyking.banhmyking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một dòng = một giá trị nội dung của website (tên web, SĐT, ảnh banner...).
 * Lưu dạng key/value để thêm giá trị mới chỉ cần thêm hằng số ở
 * {@code SiteSettingKeys}, không phải ALTER TABLE.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "site_settings")
public class SiteSetting extends BaseEntity {

    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    /** TEXT chứ không VARCHAR(n): giá trị dài bao nhiêu cũng không phải migrate lại bảng. */
    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;
}
