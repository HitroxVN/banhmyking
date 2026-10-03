package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banhmyking.banhmyking.dto.job.StoredCv;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class CvStorageServiceTest {

    static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0};

    @TempDir Path tempDir;

    private CvStorageService service;

    @BeforeEach
    void setUp() {
        service = new CvStorageService(tempDir.toString());
    }

    @Test
    void storesPdfPngJpegUnderCvFolderWithRandomKeyAndDetectedType() throws Exception {
        StoredCv pdf = service.store(new MockMultipartFile("cv", "CV Nguyễn Văn A.pdf", "application/pdf", PDF));
        assertThat(pdf.fileKey()).matches("[a-f0-9]{32}\\.pdf");
        assertThat(pdf.originalName()).isEqualTo("CV Nguyễn Văn A.pdf");
        assertThat(pdf.contentType()).isEqualTo("application/pdf");
        assertThat(Files.readAllBytes(tempDir.resolve("cv").resolve(pdf.fileKey()))).isEqualTo(PDF);

        StoredCv png = service.store(new MockMultipartFile("cv", "anh.PNG", "image/png", PNG));
        assertThat(png.fileKey()).endsWith(".png");
        assertThat(png.contentType()).isEqualTo("image/png");

        StoredCv jpg = service.store(new MockMultipartFile("cv", "C:\\fakepath\\the.jpeg", "image/jpeg", JPEG));
        assertThat(jpg.fileKey()).endsWith(".jpg");
        assertThat(jpg.contentType()).isEqualTo("image/jpeg");
        assertThat(jpg.originalName()).isEqualTo("the.jpeg");
    }

    @Test
    void missingOrEmptyFileMeansNoCv() {
        assertThat(service.store(null)).isNull();
        assertThat(service.store(new MockMultipartFile("cv", "", "application/pdf", new byte[0]))).isNull();
    }

    @Test
    void renamedExecutableIsRejectedAndNothingIsWritten() {
        assertThatThrownBy(() -> service.store(new MockMultipartFile("cv", "cv.pdf", "application/pdf", EXE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File CV phải là PDF, JPG hoặc PNG");
        assertThat(Files.exists(tempDir.resolve("cv"))).isFalse();
    }

    @Test
    void extensionDeclaredTypeAndSignatureMustAgree() {
        assertThatThrownBy(() -> service.store(new MockMultipartFile("cv", "cv.png", "image/png", PDF)))
                .hasMessage("File CV phải là PDF, JPG hoặc PNG");
        assertThatThrownBy(() -> service.store(new MockMultipartFile("cv", "cv.pdf", "image/png", PDF)))
                .hasMessage("File CV phải là PDF, JPG hoặc PNG");
        assertThatThrownBy(() -> service.store(new MockMultipartFile("cv", "cv.docx", "application/pdf", PDF)))
                .hasMessage("File CV phải là PDF, JPG hoặc PNG");
        assertThatThrownBy(() -> service.store(new MockMultipartFile("cv", "cv", "application/pdf", PDF)))
                .hasMessage("File CV phải là PDF, JPG hoặc PNG");
    }

    @Test
    void deleteRemovesStoredFileAndIgnoresBadOrMissingKeys() {
        StoredCv pdf = service.store(new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF));
        Path stored = tempDir.resolve("cv").resolve(pdf.fileKey());
        assertThat(stored).exists();

        service.delete(pdf.fileKey());
        assertThat(stored).doesNotExist();

        service.delete(pdf.fileKey());
        service.delete("../../application.properties");
        service.delete(null);
    }

    @Test
    void refusesToStartWhenPrivateDirIsInsidePublicUploadsDir() {
        Path publicDir = tempDir.resolve("uploads");

        assertThatThrownBy(() -> new CvStorageService(publicDir.resolve("private").toString(), publicDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.private-upload-dir")
                .hasMessageContaining("/uploads/**");
        assertThatThrownBy(() -> new CvStorageService(publicDir.toString(), publicDir))
                .isInstanceOf(IllegalStateException.class);
        // thư mục anh em với uploads/ là hợp lệ
        new CvStorageService(tempDir.resolve("private-uploads").toString(), publicDir);
    }

    @Test
    void fileOverFiveMegabytesIsRejected() {
        byte[] big = Arrays.copyOf(PDF, (int) CvStorageService.MAX_CV_BYTES + 1);
        assertThatThrownBy(() -> service.store(new MockMultipartFile("cv", "cv.pdf", "application/pdf", big)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File CV không được vượt quá 5MB");
    }

    @Test
    void loadReturnsStoredBytesAndRejectsBadOrMissingKeys() throws Exception {
        StoredCv pdf = service.store(new MockMultipartFile("cv", "cv.pdf", "application/pdf; charset=binary", PDF));

        assertThat(service.load(pdf.fileKey()).getContentAsByteArray()).isEqualTo(PDF);
        assertThatThrownBy(() -> service.load("../../application.properties"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy file CV");
        assertThatThrownBy(() -> service.load("0123456789abcdef0123456789abcdef.pdf"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.load(null)).isInstanceOf(ResourceNotFoundException.class);
    }
}
