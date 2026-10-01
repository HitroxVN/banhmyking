package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.store.StoreRequest;
import com.banhmyking.banhmyking.dto.store.StoreResponse;
import com.banhmyking.banhmyking.service.StoreService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stores")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin stores", description = "Quản lý cơ sở trong chuỗi (ADMIN)")
public class AdminStoreController {

    private final StoreService storeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách cơ sở thành công", storeService.listAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy cơ sở thành công", storeService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StoreResponse>> create(@Valid @RequestBody StoreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo cơ sở thành công", storeService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreResponse>> update(@PathVariable Long id, @Valid @RequestBody StoreRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật cơ sở thành công", storeService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        storeService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Xoá cơ sở thành công"));
    }
}
