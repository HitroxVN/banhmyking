package com.banhmyking.banhmyking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.service.CatalogService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách danh mục thành công", catalogService.getCategories()));
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo danh mục thành công", catalogService.createCategory(request)));
    }

    @PutMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long categoryId, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật danh mục thành công",
                catalogService.updateCategory(categoryId, request)));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long categoryId) {
        catalogService.deleteCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.ok("Xóa danh mục thành công"));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "true") boolean availableOnly) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách sản phẩm thành công",
                catalogService.getProducts(categoryId, availableOnly)));
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy sản phẩm thành công", catalogService.getProduct(productId)));
    }

    @PostMapping("/products")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo sản phẩm thành công", catalogService.createProduct(request)));
    }

    @PutMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long productId, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật sản phẩm thành công",
                catalogService.updateProduct(productId, request)));
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long productId) {
        catalogService.deleteProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok("Xóa sản phẩm thành công"));
    }

    @PostMapping(value = "/products/upload-image", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> uploadProductImage(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Tải ảnh lên thành công", catalogService.uploadProductImage(file)));
    }
}
