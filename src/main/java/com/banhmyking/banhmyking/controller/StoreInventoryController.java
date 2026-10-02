package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.catalog.StockChangeRequest;
import com.banhmyking.banhmyking.dto.catalog.StockMovementResponse;
import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.store.AvailabilityRequest;
import com.banhmyking.banhmyking.dto.store.StoreStockResponse;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.exception.NotFoundMessages;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.security.StoreAccessGuard;
import com.banhmyking.banhmyking.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store-inventory/{storeId}/products")
@RequiredArgsConstructor
@Tag(name = "Store inventory", description = "Hết món và tồn kho theo cơ sở (STAFF/MANAGER cơ sở mình, ADMIN mọi cơ sở)")
public class StoreInventoryController {

    private final InventoryService inventoryService;
    private final StoreAccessGuard storeAccessGuard;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;

    @GetMapping
    @Operation(summary = "Tình trạng mọi món tại cơ sở")
    public ResponseEntity<ApiResponse<List<StoreStockResponse>>> list(
            @PathVariable Long storeId, @AuthenticationPrincipal UserDetails principal) {
        authorize(principal, storeId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy tình trạng món thành công", inventoryService.listStoreStock(storeId)));
    }

    @PatchMapping("/{productId}/availability")
    @Operation(summary = "Bật/tắt hết món tại cơ sở")
    public ResponseEntity<ApiResponse<StoreStockResponse>> setAvailability(
            @PathVariable Long storeId, @PathVariable Long productId,
            @Valid @RequestBody AvailabilityRequest request, @AuthenticationPrincipal UserDetails principal) {
        authorize(principal, storeId);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật tình trạng món thành công",
                inventoryService.setAvailability(storeId, productId, request.available())));
    }

    @PostMapping("/{productId}/stock")
    @Operation(summary = "Nhập / điều chỉnh tồn tại cơ sở")
    public ResponseEntity<ApiResponse<StoreStockResponse>> adjustStock(
            @PathVariable Long storeId, @PathVariable Long productId,
            @Valid @RequestBody StockChangeRequest request, @AuthenticationPrincipal UserDetails principal) {
        User actor = authorize(principal, storeId);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật tồn kho thành công",
                inventoryService.adjustStock(storeId, productId, request, actor.getId())));
    }

    @GetMapping("/{productId}/movements")
    @Operation(summary = "Sổ kho của món tại cơ sở")
    public ResponseEntity<ApiResponse<PageResponse<StockMovementResponse>>> movements(
            @PathVariable Long storeId, @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails principal) {
        authorize(principal, storeId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy sổ kho thành công",
                inventoryService.getMovements(storeId, productId, page, size)));
    }

    private User authorize(UserDetails principal, Long storeId) {
        Long userId = SecurityUtils.requireUserId(principal);
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(NotFoundMessages.userById(userId)));
        storeAccessGuard.requireStoreAccess(actor, storeId);
        // ADMIN không bị guard khoá cơ sở — storeId sai/đã xoá mềm phải ra 404, không để rơi xuống
        // INSERT store_products / inventory_movements rồi nổ FK thành 500.
        if (!storeRepository.existsByIdAndDeletedFalse(storeId)) {
            throw new ResourceNotFoundException("Không tìm thấy cơ sở với ID: " + storeId);
        }
        return actor;
    }
}
