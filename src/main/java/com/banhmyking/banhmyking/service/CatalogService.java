package com.banhmyking.banhmyking.service;

import java.util.List;

import com.banhmyking.banhmyking.dto.catalog.CategoryRequest;
import com.banhmyking.banhmyking.dto.catalog.CategoryResponse;
import com.banhmyking.banhmyking.dto.catalog.ProductRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductResponse;

public interface CatalogService {
    List<CategoryResponse> getCategories();
    CategoryResponse createCategory(CategoryRequest request);
    CategoryResponse updateCategory(Long categoryId, CategoryRequest request);
    void deleteCategory(Long categoryId);
    List<ProductResponse> getProducts(Long categoryId, boolean availableOnly);
    ProductResponse getProduct(Long productId);
    ProductResponse createProduct(ProductRequest request);
    ProductResponse updateProduct(Long productId, ProductRequest request);
    void deleteProduct(Long productId);
    String uploadProductImage(org.springframework.web.multipart.MultipartFile file);
}

