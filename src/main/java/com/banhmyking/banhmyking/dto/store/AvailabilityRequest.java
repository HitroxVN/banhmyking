package com.banhmyking.banhmyking.dto.store;

import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(@NotNull(message = "Thiếu trạng thái còn/hết món") Boolean available) {
}
