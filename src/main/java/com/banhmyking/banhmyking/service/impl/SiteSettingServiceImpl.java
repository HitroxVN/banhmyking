package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.entity.SiteSetting;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.repository.SiteSettingRepository;
import com.banhmyking.banhmyking.service.FileStorageService;
import com.banhmyking.banhmyking.service.SiteSettingKeys;
import com.banhmyking.banhmyking.service.SiteSettingService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SiteSettingServiceImpl implements SiteSettingService {

    private final SiteSettingRepository siteSettingRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getPublicSettings() {
        Map<String, String> merged = new LinkedHashMap<>(SiteSettingKeys.DEFAULTS);

        // Ghi đè lên bản mặc định chứ không thêm key mới: response luôn đúng bằng
        // tập key trong DEFAULTS, một dòng cấu hình cũ còn sót lại không làm phình response.
        siteSettingRepository.findAll().stream()
                .filter(row -> row.getSettingValue() != null)
                .filter(row -> merged.containsKey(row.getSettingKey()))
                .forEach(row -> merged.put(row.getSettingKey(), row.getSettingValue()));

        return merged;
    }

    /** Khoá dạng số: rỗng = chưa đặt; có giá trị thì phải là số trong khoảng hợp lý. */
    private static void validateNumeric(String key, String value) {
        double min;
        double max;
        String label;
        switch (key) {
            case SiteSettingKeys.STORE_LATITUDE -> { min = 8.0; max = 23.5; label = "Vĩ độ cửa hàng"; }
            case SiteSettingKeys.STORE_LONGITUDE -> { min = 102.0; max = 110.0; label = "Kinh độ cửa hàng"; }
            case SiteSettingKeys.DELIVERY_MAX_RADIUS_KM -> { min = 0.5; max = 100.0; label = "Bán kính giao hàng"; }
            default -> { return; }
        }
        if (value.isEmpty()) {
            return;
        }
        double number;
        try {
            number = Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + " phải là số");
        }
        if (number < min || number > max) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    label + " phải nằm trong khoảng " + min + " – " + max);
        }
    }

    @Override
    @Transactional
    public Map<String, String> updateSettings(Map<String, String> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return getPublicSettings();
        }

        // Kiểm tra hết trước khi ghi: một key sai nằm giữa payload không để lại cập nhật nửa vời.
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            String key = entry.getKey();
            if (!SiteSettingKeys.DEFAULTS.containsKey(key)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Khoá cấu hình không hợp lệ: " + key);
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (value.length() > SiteSettingKeys.MAX_VALUE_LENGTH) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Giá trị của \"" + key + "\" quá dài (tối đa "
                                + SiteSettingKeys.MAX_VALUE_LENGTH + " ký tự)");
            }
            validateNumeric(key, value);
            values.put(key, value);
        }

        // Toạ độ quán đi theo cặp: lưu xong mà chỉ có một nửa thì không tính được khoảng cách.
        Map<String, String> after = new LinkedHashMap<>(getPublicSettings());
        after.putAll(values);
        if (after.get(SiteSettingKeys.STORE_LATITUDE).isEmpty()
                != after.get(SiteSettingKeys.STORE_LONGITUDE).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Vị trí cửa hàng cần đủ cả vĩ độ và kinh độ (hoặc để trống cả hai)");
        }

        String previousHeroImage = getPublicSettings().get(SiteSettingKeys.HERO_IMAGE_URL);

        Map<String, SiteSetting> rows = new LinkedHashMap<>();
        siteSettingRepository.findAll().forEach(row -> rows.put(row.getSettingKey(), row));

        for (Map.Entry<String, String> entry : values.entrySet()) {
            SiteSetting row = rows.get(entry.getKey());
            if (row == null) {
                row = SiteSetting.builder().settingKey(entry.getKey()).build();
            }
            row.setSettingValue(entry.getValue());
            siteSettingRepository.save(row);
        }

        // Chỉ xoá file ảnh cũ khi admin thực sự đổi sang ảnh khác; xoá ảnh ngoài /uploads/banners/
        // đã bị FileStorageService chặn sẵn.
        if (values.containsKey(SiteSettingKeys.HERO_IMAGE_URL)
                && !values.get(SiteSettingKeys.HERO_IMAGE_URL).equals(previousHeroImage)) {
            fileStorageService.deleteImage(previousHeroImage, FileStorageService.SITE_DIR);
        }

        return getPublicSettings();
    }

    @Override
    @Transactional
    public String uploadHeroImage(MultipartFile file) {
        String publicUrl = fileStorageService.storeImage(file, FileStorageService.SITE_DIR);
        // updateSettings lo luôn việc xoá ảnh cũ khi URL đổi — không xoá lần hai ở đây.
        updateSettings(Map.of(SiteSettingKeys.HERO_IMAGE_URL, publicUrl));
        return publicUrl;
    }
}
