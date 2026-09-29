package com.banhmyking.banhmyking.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.banhmyking.banhmyking.dto.catalog.CategoryRequest;
import com.banhmyking.banhmyking.dto.catalog.CategoryResponse;
import com.banhmyking.banhmyking.dto.catalog.ProductRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductResponse;
import com.banhmyking.banhmyking.dto.catalog.StockChangeRequest;
import com.banhmyking.banhmyking.dto.catalog.StockMovementResponse;
import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.enums.ProductSort;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.CatalogService;
import com.banhmyking.banhmyking.service.InventoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "APIs danh mục và sản phẩm (đọc công khai, ghi cần STAFF/ADMIN)")
public class CatalogController {

    private final CatalogService catalogService;
    private final InventoryService inventoryService;

    @GetMapping("/categories")
    @Operation(summary = "Danh sách danh mục",
            description = "Trả toàn bộ danh mục chưa xoá mềm.")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách danh mục thành công", catalogService.getCategories()));
    }

    @PostMapping("/categories")
    @Operation(summary = "Tạo danh mục",
            description = "Cần quyền STAFF hoặc ADMIN.")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo danh mục thành công", catalogService.createCategory(request)));
    }

    @PutMapping("/categories/{categoryId}")
    @Operation(summary = "Cập nhật danh mục",
            description = "Cần quyền STAFF hoặc ADMIN.")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long categoryId, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật danh mục thành công",
                catalogService.updateCategory(categoryId, request)));
    }

    @DeleteMapping("/categories/{categoryId}")
    @Operation(summary = "Xoá danh mục",
            description = "Xoá mềm (is_deleted = true). Cần quyền STAFF hoặc ADMIN.")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long categoryId) {
        catalogService.deleteCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.ok("Xóa danh mục thành công"));
    }

    @GetMapping("/products")
    @Operation(summary = "Danh sách sản phẩm",
            description = "Phân trang + tìm kiếm/lọc/sắp xếp phía server. Từ khoá tìm trên tên và mô tả, "
                    + "không phân biệt hoa-thường và không phân biệt dấu.")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProducts(
            @Parameter(description = "Lọc theo danh mục", example = "1")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Chỉ lấy sản phẩm đang bán", example = "true")
            @RequestParam(defaultValue = "true") boolean availableOnly,
            @Parameter(description = "Từ khoá tìm theo tên/mô tả (bỏ dấu vẫn khớp)")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Chỉ lấy món nổi bật", example = "true")
            @RequestParam(required = false) Boolean featured,
            @Parameter(description = "Giá thấp nhất (bỏ trống = không lọc)")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Giá cao nhất (bỏ trống = không lọc)")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Cách sắp xếp")
            @RequestParam(defaultValue = "FEATURED") ProductSort sort,
            @Parameter(description = "Trang (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số món mỗi trang (tối đa 50)") @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách sản phẩm thành công",
                catalogService.getProducts(categoryId, availableOnly, keyword, featured,
                        minPrice, maxPrice, sort, page, size)));
    }

    @GetMapping("/products/{productId}")
    @Operation(summary = "Chi tiết sản phẩm",
            description = "Trả sản phẩm kèm danh sách option (size/topping).")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy sản phẩm thành công", catalogService.getProduct(productId)));
    }

    @PostMapping("/products")
    @Operation(summary = "Tạo sản phẩm",
            description = "Tạo kèm danh sách option (size/topping) trong cùng request. Cần quyền STAFF hoặc ADMIN.")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo sản phẩm thành công", catalogService.createProduct(request)));
    }

    @PutMapping("/products/{productId}")
    @Operation(summary = "Cập nhật sản phẩm",
            description = "Thay cả danh sách option. Cần quyền STAFF hoặc ADMIN.")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long productId, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật sản phẩm thành công",
                catalogService.updateProduct(productId, request)));
    }

    @DeleteMapping("/products/{productId}")
    @Operation(summary = "Xoá sản phẩm",
            description = "Xoá mềm (is_deleted = true). Cần quyền STAFF hoặc ADMIN.")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long productId) {
        catalogService.deleteProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok("Xóa sản phẩm thành công"));
    }

    @PostMapping("/products/{productId}/stock")
    @Operation(summary = "Nhập hoặc điều chỉnh tồn kho",
            description = "changeQty dương = nhập thêm, âm = giảm bớt. Sản phẩm chưa quản tồn thì "
                    + "số dương đầu tiên đặt luôn tồn ban đầu. Cần quyền STAFF hoặc ADMIN.")
    public ResponseEntity<ApiResponse<ProductResponse>> adjustStock(
            @PathVariable Long productId,
            @Valid @RequestBody StockChangeRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        inventoryService.adjustStock(productId, request, SecurityUtils.requireUserId(principal));
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật tồn kho thành công", catalogService.getProduct(productId)));
    }

    @GetMapping("/products/{productId}/stock-movements")
    @Operation(summary = "Sổ kho của sản phẩm",
            description = "Các lần nhập/giảm tồn, mới nhất trước. Cần quyền STAFF hoặc ADMIN.")
    public ResponseEntity<ApiResponse<PageResponse<StockMovementResponse>>> getStockMovements(
            @PathVariable Long productId,
            @Parameter(description = "Trang, bắt đầu từ 0", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số dòng mỗi trang, tối đa 50", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy sổ kho thành công",
                inventoryService.getMovements(productId, page, size)));
    }

    @PostMapping(value = "/products/upload-image", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải ảnh sản phẩm lên",
            description = "multipart/form-data, tối đa 5MB, chỉ nhận content-type image/*. Cần quyền STAFF hoặc ADMIN.")
    public ResponseEntity<ApiResponse<String>> uploadProductImage(
            @Parameter(description = "Tệp ảnh cần tải lên", required = true)
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Tải ảnh lên thành công", catalogService.uploadProductImage(file)));
    }
}
