package com.banhmyking.banhmyking.service;

import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface SiteSettingService {

    /**
     * Toàn bộ cấu hình nội dung website, luôn đủ mọi key (key chưa lưu trả về mặc định).
     */
    Map<String, String> getPublicSettings();

    /**
     * Cập nhật một phần: chỉ upsert những key có trong {@code incoming}, key vắng mặt giữ nguyên.
     */
    Map<String, String> updateSettings(Map<String, String> incoming);

    /**
     * Lưu ảnh banner mới, ghi vào key heroImageUrl và xoá file ảnh cũ.
     *
     * @return đường dẫn công khai của ảnh vừa lưu
     */
    String uploadHeroImage(MultipartFile file);
}
