package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Kiểm tra dịch vụ còn sống")
public class HealthController {

    @GetMapping
    @Operation(summary = "Ping dịch vụ",
            description = "Trả về thời điểm hiện tại của server. Không kiểm tra kết nối database.")
    public ApiResponse<LocalDateTime> health() {
        return new ApiResponse<>(true, "OK", LocalDateTime.now(), LocalDateTime.now());
    }
}
