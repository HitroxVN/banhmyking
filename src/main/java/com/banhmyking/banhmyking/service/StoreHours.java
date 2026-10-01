package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.entity.Store;
import java.time.LocalTime;

/** Giờ mở cửa: cùng giờ mọi ngày, không hỗ trợ ca qua nửa đêm (spec §3.5). */
public final class StoreHours {

    private StoreHours() {
    }

    public static boolean isOpen(Store store, LocalTime now) {
        return !now.isBefore(store.getOpenTime()) && now.isBefore(store.getCloseTime());
    }
}
