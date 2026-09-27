package com.banhmyking.banhmyking.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Kiểm bảng dịch exception → HTTP status, đặc biệt tệp upload vượt giới hạn phải là 413
 * chứ không rơi vào catch-all 500.
 */
class GlobalExceptionHandlerTest {

    /** Controller giả chỉ để ném đúng exception cần kiểm. */
    @RestController
    static class ThrowingController {
        @PostMapping("/test/upload")
        void upload() {
            throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
        }

        @PostMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("boom");
        }
    }

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadTooLarge_returns413WithClearMessage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/test/upload").file(file))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("Tệp tải lên vượt quá dung lượng cho phép, vui lòng chọn tệp nhỏ hơn"));
    }

    @Test
    void unexpectedException_stillReturns500() throws Exception {
        mockMvc.perform(multipart("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));
    }
}
