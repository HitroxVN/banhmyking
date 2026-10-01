package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lưu ảnh người dùng tải lên vào thư mục {@code uploads/} (đã map ra ngoài ở WebMvcConfig).
 * Dùng chung cho ảnh sản phẩm và avatar thay vì mỗi nơi tự copy một đoạn validate + ghi file.
 */
@Slf4j
@Service
public class FileStorageService {

    public static final String PRODUCT_DIR = "products";
    public static final String AVATAR_DIR = "avatars";
    public static final String SITE_DIR = "banners";

    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");

    /**
     * Lưu ảnh vào {@code uploads/<subDir>/} với tên ngẫu nhiên, trả về đường dẫn công khai
     * {@code /uploads/<subDir>/<tên file>}.
     */
    public String storeImage(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn tệp hình ảnh để tải lên");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Tệp tải lên phải là định dạng hình ảnh (JPG, PNG, WEBP, GIF)");
        }

        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dung lượng ảnh không được vượt quá 5MB");
        }

        // Đuôi file lấy từ client → chỉ nhận whitelist. Không thì "x.html" / "x.svg" (content-type image/svg+xml)
        // được phục vụ công khai ở /uploads/** cùng origin API → stored XSS.
        String extension = ".png";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(extension) || contentType.toLowerCase().contains("svg")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Tệp tải lên phải là định dạng hình ảnh (JPG, PNG, WEBP, GIF)");
        }

        try {
            Path uploadDir = Paths.get("uploads", subDir);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
            Files.copy(file.getInputStream(), uploadDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/" + subDir + "/" + fileName;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không thể lưu trữ tệp ảnh: " + ex.getMessage());
        }
    }

    /**
     * Xoá ảnh do app lưu, CHỈ khi URL nằm trong {@code /uploads/<subDir>/}.
     * Ảnh từ nguồn ngoài (seed dùng link unsplash) được giữ nguyên.
     * Xoá lỗi không làm hỏng thao tác đang chạy — chỉ log lại.
     */
    public void deleteImage(String publicUrl, String subDir) {
        String prefix = "/uploads/" + subDir + "/";
        if (publicUrl == null || !publicUrl.startsWith(prefix)) {
            return;
        }

        // Chỉ lấy tên file, chặn mọi mưu toan thoát khỏi thư mục uploads/<subDir>
        String fileName = Paths.get(publicUrl.substring(prefix.length())).getFileName().toString();
        try {
            Files.deleteIfExists(Paths.get("uploads", subDir, fileName));
        } catch (IOException ex) {
            log.warn("Không xoá được ảnh {}: {}", publicUrl, ex.getMessage());
        }
    }
}
