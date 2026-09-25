package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banhmyking.banhmyking.exception.BusinessException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTest {

    private final FileStorageService storage = new FileStorageService();

    @Test
    void storeImage_nonImageContentType_isRejected() {
        MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", "x".getBytes());

        assertThatThrownBy(() -> storage.storeImage(pdf, FileStorageService.AVATAR_DIR))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void storeImage_emptyFile_isRejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storage.storeImage(empty, FileStorageService.AVATAR_DIR))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void storeImage_savesIntoSubDirAndReturnsPublicUrl() throws Exception {
        MockMultipartFile png = new MockMultipartFile("file", "a.PNG", "image/png", "bytes".getBytes());

        String url = storage.storeImage(png, FileStorageService.AVATAR_DIR);

        assertThat(url).startsWith("/uploads/avatars/").endsWith(".png");
        Path saved = Paths.get("uploads", FileStorageService.AVATAR_DIR, url.substring("/uploads/avatars/".length()));
        assertThat(Files.exists(saved)).isTrue();
        Files.deleteIfExists(saved);
    }

    @Test
    void deleteImage_externalUrl_isLeftAlone() {
        // Ảnh seed từ unsplash không thuộc quyền quản lý của app
        storage.deleteImage("https://images.unsplash.com/photo-1626804475297-41608ea09aeb", FileStorageService.AVATAR_DIR);
    }

    @Test
    void deleteImage_pathTraversal_cannotEscapeUploadDir() throws Exception {
        // File nằm NGOÀI uploads/avatars phải được giữ nguyên
        Path outside = Paths.get("uploads", "legit.txt");
        Files.createDirectories(outside.getParent());
        Files.writeString(outside, "keep me");

        storage.deleteImage("/uploads/avatars/../legit.txt", FileStorageService.AVATAR_DIR);

        assertThat(Files.exists(outside)).isTrue();
        Files.deleteIfExists(outside);
    }
}
