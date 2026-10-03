package com.banhmyking.banhmyking.event;

/** Loại thay đổi của đơn — frontend dựa vào đây để quyết định kêu chuông / hiện toast. */
public enum OrderChangeKind {
    CREATED,
    STATUS_CHANGED,
    SHIPPER_ASSIGNED,
    STORE_TRANSFERRED,
    UPDATED
}
