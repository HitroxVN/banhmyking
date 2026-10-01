package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.store.PublicStoreResponse;
import com.banhmyking.banhmyking.dto.store.StoreRequest;
import com.banhmyking.banhmyking.dto.store.StoreResponse;
import com.banhmyking.banhmyking.entity.Store;
import java.util.List;

public interface StoreService {
    List<StoreResponse> listAll();
    List<PublicStoreResponse> listPublic();
    StoreResponse get(Long id);
    StoreResponse create(StoreRequest request);
    StoreResponse update(Long id, StoreRequest request);
    void delete(Long id);
    StoreResponse setAcceptingOrders(Long actorId, Long storeId, boolean accepting);
    /** Cơ sở chưa xoá và đang hoạt động; không có → ResourceNotFoundException. */
    Store requireActiveStore(Long id);
}
