package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.job.StoredCv;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * CV ứng viên là dữ liệu cá nhân (spec D §4 "Lưu CV"): nằm trong {@code <app.private-upload-dir>/cv/},
 * KHÔNG map ra /uploads/**, chỉ đọc qua GET /job-applications/{id}/cv sau khi kiểm quyền.
 * Đuôi file, content-type khai báo và chữ ký nội dung phải khớp nhau (chặn .exe đổi tên thành .pdf).
 */
@Slf4j
@Service
public class CvStorageService {

    public static final long MAX_CV_BYTES = 5L * 1024 * 1024;
    public static final String INVALID_TYPE = "File CV phải là PDF, JPG hoặc PNG";
    static final String NOT_FOUND = "Không tìm thấy file CV";
    private static final Pattern FILE_KEY = Pattern.compile("^[a-f0-9]{32}\\.(pdf|jpg|png)$");
    private static final int MAX_ORIGINAL_NAME = 255;
    private static final int SIGNATURE_BYTES = 8;

    private final Path cvDir;

    @Autowired
    public CvStorageService(@Value("${app.private-upload-dir:private-uploads}") String privateUploadDir) {
        this(privateUploadDir, Paths.get("uploads"));
    }

    /** Khởi động thất bại nếu thư mục CV nằm trong thư mục công khai {@code publicUploadDir} (map ra /uploads/**). */
    CvStorageService(String privateUploadDir, Path publicUploadDir) {
        this.cvDir = Paths.get(privateUploadDir, "cv").toAbsolutePath().normalize();
        Path publicDir = publicUploadDir.toAbsolutePath().normalize();
        if (cvDir.startsWith(publicDir)) {
            throw new IllegalStateException("app.private-upload-dir (" + privateUploadDir
                    + ") không được nằm trong thư mục công khai " + publicDir
                    + ": CV sẽ bị tải được qua /uploads/**. Hãy đặt thư mục riêng, ví dụ private-uploads.");
        }
    }

    /** Xoá CV đã lưu (khi giao dịch lưu hồ sơ thất bại). Khoá sai định dạng hoặc file đã mất thì bỏ qua. */
    public void delete(String fileKey) {
        if (fileKey == null || !FILE_KEY.matcher(fileKey).matches()) {
            return;
        }
        Path path = cvDir.resolve(fileKey).normalize();
        if (!path.startsWith(cvDir)) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Không xoá được CV mồ côi {}: {}", fileKey, ex.getMessage());
        }
    }

    /** @return null khi ứng viên không đính kèm CV (không bắt buộc). */
    public StoredCv store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_CV_BYTES) {
            throw invalid("File CV không được vượt quá 5MB");
        }
        CvKind kind = CvKind.fromFilename(file.getOriginalFilename());
        if (kind == null || !kind.acceptsDeclaredType(file.getContentType()) || !kind.matchesSignature(readHead(file))) {
            throw invalid(INVALID_TYPE);
        }
        String fileKey = UUID.randomUUID().toString().replace("-", "") + "." + kind.extension;
        Path target = cvDir.resolve(fileKey).normalize();
        if (!target.startsWith(cvDir)) {
            throw invalid(INVALID_TYPE);
        }
        try {
            Files.createDirectories(cvDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException ex) {
            log.error("Không lưu được CV {}: {}", fileKey, ex.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không thể lưu file CV, vui lòng thử lại");
        }
        return new StoredCv(fileKey, safeOriginalName(file.getOriginalFilename(), kind), kind.contentType);
    }

    public Resource load(String fileKey) {
        if (fileKey == null || !FILE_KEY.matcher(fileKey).matches()) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        Path path = cvDir.resolve(fileKey).normalize();
        if (!path.startsWith(cvDir) || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return new FileSystemResource(path);
    }

    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SIGNATURE_BYTES);
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    /** Chỉ giữ tên file (bỏ đường dẫn "C:\fakepath\…"), bỏ ký tự điều khiển và dấu nháy kép. */
    private static String safeOriginalName(String original, CvKind kind) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty()) {
            name = "cv." + kind.extension;
        }
        return name.length() > MAX_ORIGINAL_NAME ? name.substring(name.length() - MAX_ORIGINAL_NAME) : name;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    enum CvKind {
        PDF("pdf", "application/pdf", Set.of("pdf"), Set.of("application/pdf"),
                new byte[] {'%', 'P', 'D', 'F'}),
        PNG("png", "image/png", Set.of("png"), Set.of("image/png"),
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
        JPEG("jpg", "image/jpeg", Set.of("jpg", "jpeg"), Set.of("image/jpeg", "image/jpg", "image/pjpeg"),
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        final String extension;
        final String contentType;
        private final Set<String> extensions;
        private final Set<String> declaredTypes;
        private final byte[] signature;

        CvKind(String extension, String contentType, Set<String> extensions, Set<String> declaredTypes,
               byte[] signature) {
            this.extension = extension;
            this.contentType = contentType;
            this.extensions = extensions;
            this.declaredTypes = declaredTypes;
            this.signature = signature;
        }

        static CvKind fromFilename(String filename) {
            if (filename == null || filename.lastIndexOf('.') < 0) {
                return null;
            }
            String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            for (CvKind kind : values()) {
                if (kind.extensions.contains(ext)) {
                    return kind;
                }
            }
            return null;
        }

        boolean acceptsDeclaredType(String declared) {
            if (declared == null) {
                return false;
            }
            String base = declared.split(";")[0].trim().toLowerCase(Locale.ROOT);
            return declaredTypes.contains(base);
        }

        boolean matchesSignature(byte[] head) {
            return head.length >= signature.length
                    && Arrays.equals(Arrays.copyOf(head, signature.length), signature);
        }
    }
}
