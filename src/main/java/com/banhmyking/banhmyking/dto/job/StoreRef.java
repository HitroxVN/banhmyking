package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.entity.Store;

/** Cơ sở rút gọn hiển thị trên tin tuyển dụng / hồ sơ. */
public record StoreRef(Long id, String code, String name) {
    public static StoreRef from(Store store) {
        return new StoreRef(store.getId(), store.getCode(), store.getName());
    }
}
