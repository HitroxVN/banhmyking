package com.banhmyking.banhmyking.service;

import java.math.BigDecimal;
import java.util.List;

import com.banhmyking.banhmyking.dto.catalog.CategoryRequest;
import com.banhmyking.banhmyking.dto.catalog.CategoryResponse;
import com.banhmyking.banhmyking.dto.catalog.ProductRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.enums.ProductSort;

public interface CatalogService {
    List<CategoryResponse> getCategories();
    CategoryResponse createCategory(CategoryRequest request);
    CategoryResponse updateCategory(Long categoryId, CategoryRequest request);
    void deleteCategory(Long categoryId);

    /**
     * Tìm/lọc/sắp xếp thực đơn ở phía server. {@code keyword}, {@code featured} và khoảng giá
     * bỏ trống = không lọc theo tiêu chí đó.
     */
    PageResponse<ProductResponse> getProducts(Long categoryId, boolean availableOnly, String keyword,
                                              Boolean featured, BigDecimal minPrice, BigDecimal maxPrice,
                                              ProductSort sort, int page, int size);

    ProductResponse getProduct(Long productId);
    ProductResponse createProduct(ProductRequest request);
    ProductResponse updateProduct(Long productId, ProductRequest request);
    void deleteProduct(Long productId);
    String uploadProductImage(org.springframework.web.multipart.MultipartFile file);
}

