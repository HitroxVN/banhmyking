package com.banhmyking.banhmyking.dto.store;

import jakarta.validation.constraints.NotNull;

public record AcceptingOrdersRequest(@NotNull(message = "Thiếu trạng thái nhận đơn") Boolean accepting) {
}
