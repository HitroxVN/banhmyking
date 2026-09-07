package com.banhmyking.banhmyking.enums;

import java.util.Set;

/**
 * State machine đơn hàng. Chỉ được chuyển theo transition hợp lệ.
 * FAILED chỉ từ DELIVERING.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    READY_FOR_PICKUP,
    DELIVERING,
    DELIVERED,
    CANCELLED,
    FAILED;

    private static final Set<OrderStatus> CANCELABLE_BY_CUSTOMER = Set.of(PENDING, CONFIRMED);
    private static final Set<OrderStatus> CANCELABLE_BY_STAFF = Set.of(PENDING, CONFIRMED, PREPARING, READY_FOR_PICKUP);

    /** Transition hợp lệ — kiểm tra bằng OrderStatusValidator, không cho nhảy bậc. */
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == PREPARING || next == CANCELLED;
            case PREPARING -> next == READY_FOR_PICKUP || next == CANCELLED;
            case READY_FOR_PICKUP -> next == DELIVERING || next == CANCELLED;
            case DELIVERING -> next == DELIVERED || next == FAILED;
            default -> false; // DELIVERED/CANCELLED/FAILED là terminal
        };
    }

    public boolean cancelableByCustomer() {
        return CANCELABLE_BY_CUSTOMER.contains(this);
    }

    public boolean cancelableByStaff() {
        return CANCELABLE_BY_STAFF.contains(this);
    }
}
