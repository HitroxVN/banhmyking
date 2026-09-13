package com.banhmyking.banhmyking.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.banhmyking.banhmyking.dto.catalog.CategoryRequest;
import com.banhmyking.banhmyking.dto.catalog.CategoryResponse;
import com.banhmyking.banhmyking.dto.catalog.ProductOptionRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductOptionResponse;
import com.banhmyking.banhmyking.dto.catalog.ProductRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductResponse;
import com.banhmyking.banhmyking.entity.Category;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.ProductOption;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.CategoryRepository;
import com.banhmyking.banhmyking.repository.ProductOptionRepository;
import com.banhmyking.banhmyking.repository.ProductRepository;
import com.banhmyking.banhmyking.service.CatalogService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findByDeletedFalseOrderBySortOrderAscNameAsc().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = new Category();
        applyCategory(category, request);
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        Category category = findCategory(categoryId);
        applyCategory(category, request);
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = findCategory(categoryId);
        category.setDeleted(true);
        categoryRepository.save(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts(Long categoryId, boolean availableOnly) {
        List<Product> products;
        if (categoryId != null && availableOnly) {
            products = productRepository.findByCategoryIdAndAvailableTrueAndDeletedFalseOrderByFeaturedDescNameAsc(categoryId);
        } else if (categoryId != null) {
            products = productRepository.findByCategoryIdAndDeletedFalseOrderByFeaturedDescNameAsc(categoryId);
        } else if (availableOnly) {
            products = productRepository.findByAvailableTrueAndDeletedFalseOrderByFeaturedDescNameAsc();
        } else {
            products = productRepository.findByDeletedFalseOrderByFeaturedDescNameAsc();
        }
        return products.stream().map(this::toProductResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        return toProductResponse(productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId)));
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = new Product();
        applyProduct(product, request);
        return saveProductWithOptions(product, request);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long productId, ProductRequest request) {
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
        applyProduct(product, request);
        return saveProductWithOptions(product, request);
    }

    @Override
    @Transactional
    public void deleteProduct(Long productId) {
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
        product.setDeleted(true);
        product.setAvailable(false);
        productRepository.save(product);
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findByIdAndDeletedFalse(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với ID: " + categoryId));
    }

    private void applyCategory(Category category, CategoryRequest request) {
        category.setName(request.getName().trim());
        category.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        Integer sortOrder = request.getSortOrder();
        category.setSortOrder(sortOrder == null ? Integer.valueOf(0) : sortOrder);
    }

    private void applyProduct(Product product, ProductRequest request) {
        product.setCategory(findCategory(request.getCategoryId()));
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        product.setImageUrl(request.getImageUrl() == null ? null : request.getImageUrl().trim());
        product.setPrice(request.getPrice());
        product.setAvailable(request.isAvailable());
        product.setFeatured(request.isFeatured());

        if (product.getId() != null) {
            productOptionRepository.deleteAll(productOptionRepository.findByProductId(product.getId()));
        }
        product.getOptions().clear();
    }

    private ProductResponse saveProductWithOptions(Product product, ProductRequest request) {
        Product savedProduct = productRepository.save(product);
        for (ProductOptionRequest optionRequest : request.getOptions()) {
            ProductOption option = new ProductOption();
            option.setProduct(savedProduct);
            option.setName(optionRequest.getName().trim());
            option.setExtraPrice(optionRequest.getExtraPrice());
            productOptionRepository.save(option);
        }
        savedProduct.setOptions(productOptionRepository.findByProductId(savedProduct.getId()));
        return toProductResponse(savedProduct);
    }

    private CategoryResponse toCategoryResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .sortOrder(category.getSortOrder())
                .build();
    }

    private ProductResponse toProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .available(product.isAvailable())
                .featured(product.isFeatured())
                .options(product.getOptions().stream().map(option -> ProductOptionResponse.builder()
                        .id(option.getId())
                        .name(option.getName())
                        .extraPrice(option.getExtraPrice())
                        .build()).toList())
                .build();
    }
}
