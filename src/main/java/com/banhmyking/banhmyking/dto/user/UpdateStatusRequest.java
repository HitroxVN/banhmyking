package com.banhmyking.banhmyking.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request khoá/mở tài khoản")
public record UpdateStatusRequest(
        @NotNull(message = "Trạng thái khoá không được để trống")
        @Schema(description = "true = khoá tài khoản, false = mở khoá", example = "true") Boolean banned) {
}
