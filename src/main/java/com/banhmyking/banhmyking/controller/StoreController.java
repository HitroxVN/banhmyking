package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.store.PublicStoreResponse;
import com.banhmyking.banhmyking.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
@Tag(name = "Stores (public)", description = "Hệ thống cửa hàng cho khách")
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    @Operation(summary = "Danh sách cơ sở đang hoạt động")
    public ResponseEntity<ApiResponse<List<PublicStoreResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách cơ sở thành công", storeService.listPublic()));
    }
}
