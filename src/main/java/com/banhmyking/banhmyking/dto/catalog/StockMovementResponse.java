package com.banhmyking.banhmyking.dto.catalog;

import java.time.LocalDateTime;

import com.banhmyking.banhmyking.enums.InventoryReason;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockMovementResponse {
    private Long id;
    private Integer changeQty;
    private InventoryReason reason;
    private String orderCode;
    private String note;
    private LocalDateTime createdAt;
}
