package com.banhmyking.banhmyking.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
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
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.CartItemOptionRepository;
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
    private final CartItemOptionRepository cartItemOptionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findByDeletedFalseOrderBySortOrderAscNameAsc().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = new Category();
        applyCategory(category, request);
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        Category category = findCategory(categoryId);
        applyCategory(category, request);
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
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
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = new Product();
        applyProduct(product, request);
        return saveProductWithOptions(product, request);
    }

    @Override
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Transactional
    public ProductResponse updateProduct(Long productId, ProductRequest request) {
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
        applyProduct(product, request);
        return saveProductWithOptions(product, request);
    }

    @Override
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
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
    }

    private ProductResponse saveProductWithOptions(Product product, ProductRequest request) {
        Product savedProduct = productRepository.save(product);
        // client gửi "options": null (không gửi) → trước đây NPE 500; coalesce về rỗng
        // (= xóa hết option hiện có, đồng bộ theo đúng payload).
        syncOptions(savedProduct, request.getOptions() == null ? List.of() : request.getOptions());
        return toProductResponse(savedProduct);
    }

    /**
     * Đồng bộ option theo hướng update-in-place: option trùng (theo tên) giữa request và DB
     * được GIỮ NGUYÊN row cũ (chỉ sửa giá) — giỏ hàng đang tham chiếu product_option_id
     * không bị vỡ FK. Option đổi tên = tạo row mới; option biến mất = chỉ xóa khi không còn
     * dòng giỏ hàng nào tham chiếu, còn thì báo 409 cho admin biết chỗ cần dọn.
     */
    private void syncOptions(Product product, List<ProductOptionRequest> requested) {
        List<ProductOption> current = productOptionRepository.findByProductId(product.getId());
        Map<String, ProductOption> currentByName = new LinkedHashMap<>();
        for (ProductOption option : current) {
            currentByName.putIfAbsent(option.getName().trim(), option);
        }

        // gom rồi saveAll một lần thay vì save() per-row (N+1 write).
        List<ProductOption> toSave = new ArrayList<>();
        for (ProductOptionRequest optionRequest : requested) {
            String name = optionRequest.getName().trim();
            ProductOption option = currentByName.remove(name);
            if (option == null) {
                option = new ProductOption();
                option.setProduct(product);
                option.setName(name);
            }
            option.setExtraPrice(optionRequest.getExtraPrice());
            toSave.add(option);
        }
        List<ProductOption> kept = productOptionRepository.saveAll(toSave);

        List<String> stillReferenced = new ArrayList<>();
        for (ProductOption stale : currentByName.values()) {
            if (cartItemOptionRepository.countByProductOption_IdIn(List.of(stale.getId())) > 0) {
                stillReferenced.add(stale.getName());
            } else {
                productOptionRepository.delete(stale);
            }
        }
        if (!stillReferenced.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Không thể xóa lựa chọn '" + String.join("', '", stillReferenced)
                            + "' vì còn trong giỏ hàng của khách — hãy đổi tên/xóa giỏ liên quan trước");
        }
        product.setOptions(kept);
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
