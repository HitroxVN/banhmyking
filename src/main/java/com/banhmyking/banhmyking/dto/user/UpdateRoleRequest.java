package com.banhmyking.banhmyking.dto.user;

import com.banhmyking.banhmyking.enums.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request đổi vai trò user")
public record UpdateRoleRequest(
        @NotNull(message = "Vai trò không được để trống")
        @Schema(description = "Vai trò mới", example = "STAFF") RoleName role) {
}
