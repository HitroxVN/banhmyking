package com.banhmyking.banhmyking.dto.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferStoreRequest(
        @NotNull(message = "Chưa chọn cơ sở đích") Long storeId,
        @NotBlank(message = "Vui lòng nhập lý do chuyển cơ sở") @Size(max = 300) String reason) {
}
