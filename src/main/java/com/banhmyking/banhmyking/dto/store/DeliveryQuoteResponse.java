package com.banhmyking.banhmyking.dto.store;

import java.util.List;

public record DeliveryQuoteResponse(Long recommendedStoreId, List<StoreQuoteOption> options) {
}
