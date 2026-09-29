package com.banhmyking.banhmyking.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.banhmyking.banhmyking.dto.catalog.CategoryRequest;
import com.banhmyking.banhmyking.dto.catalog.CategoryResponse;
import com.banhmyking.banhmyking.dto.catalog.OptionGroupRequest;
import com.banhmyking.banhmyking.dto.catalog.OptionGroupResponse;
import com.banhmyking.banhmyking.dto.catalog.ProductOptionRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductOptionResponse;
import com.banhmyking.banhmyking.dto.catalog.ProductRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.entity.Category;
import com.banhmyking.banhmyking.entity.OptionGroup;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.ProductImage;
import com.banhmyking.banhmyking.entity.ProductOption;
import com.banhmyking.banhmyking.enums.ProductSort;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.CartItemOptionRepository;
import com.banhmyking.banhmyking.repository.CategoryRepository;
import com.banhmyking.banhmyking.repository.OptionGroupRepository;
import com.banhmyking.banhmyking.repository.ProductImageRepository;
import com.banhmyking.banhmyking.repository.ProductOptionRepository;
import com.banhmyking.banhmyking.repository.ProductRepository;
import com.banhmyking.banhmyking.repository.ReviewRepository;
import com.banhmyking.banhmyking.repository.specification.ProductSpecifications;
import com.banhmyking.banhmyking.service.CatalogService;
import com.banhmyking.banhmyking.service.FileStorageService;
import com.banhmyking.banhmyking.util.PageableFactory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogService {

    private final FileStorageService fileStorageService;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final ProductImageRepository productImageRepository;
    private final CartItemOptionRepository cartItemOptionRepository;
    private final ReviewRepository reviewRepository;

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
    public PageResponse<ProductResponse> getProducts(Long categoryId, boolean availableOnly, String keyword,
                                                     Boolean featured, BigDecimal minPrice, BigDecimal maxPrice,
                                                     ProductSort sort, int page, int size) {
        Page<Product> result = productRepository.findAll(
                ProductSpecifications.search(categoryId, availableOnly, keyword, featured, minPrice, maxPrice),
                PageableFactory.of(page, size, sort.toSort()));

        // Bọc lại PageImpl để enrich cả trang trong 1 lượt — map từng món riêng sẽ thành N+1
        List<ProductResponse> content = toProductResponses(result.getContent());
        return PageResponse.from(new PageImpl<>(content, result.getPageable(), result.getTotalElements()));
    }

    /**
     * Gắn rating + bộ ảnh + lựa chọn cho một lô sản phẩm: mỗi thứ gom đúng 1 query cho cả lô,
     * không gọi theo từng món (N+1). Giữ nguyên thứ tự đầu vào.
     */
    private List<ProductResponse> toProductResponses(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream().map(Product::getId).toList();

        Map<Long, Object[]> summaryByProductId = new HashMap<>();
        for (Object[] row : reviewRepository.summarizeByProductIds(productIds)) {
            summaryByProductId.put((Long) row[0], row);
        }

        Map<Long, List<String>> imagesByProductId = new HashMap<>();
        for (ProductImage image : productImageRepository.findByProductIdInOrderBySortOrderAscIdAsc(productIds)) {
            imagesByProductId.computeIfAbsent(image.getProduct().getId(), key -> new ArrayList<>())
                    .add(image.getImageUrl());
        }

        Map<Long, List<ProductOption>> optionsByProductId = new HashMap<>();
        for (ProductOption option : productOptionRepository.findByProductIdInOrderByIdAsc(productIds)) {
            optionsByProductId.computeIfAbsent(option.getProduct().getId(), key -> new ArrayList<>())
                    .add(option);
        }

        Map<Long, List<OptionGroup>> groupsByProductId = new HashMap<>();
        for (OptionGroup group : optionGroupRepository.findByProductIdInOrderBySortOrderAscIdAsc(productIds)) {
            groupsByProductId.computeIfAbsent(group.getProduct().getId(), key -> new ArrayList<>())
                    .add(group);
        }

        return products.stream().map(product -> {
            Object[] summary = summaryByProductId.get(product.getId());
            Double averageRating = summary != null ? ((Number) summary[1]).doubleValue() : 0.0;
            Long totalReviews = summary != null ? ((Number) summary[2]).longValue() : 0L;
            return toProductResponse(product, averageRating, totalReviews,
                    imagesByProductId.getOrDefault(product.getId(), List.of()),
                    optionsByProductId.getOrDefault(product.getId(), List.of()),
                    groupsByProductId.getOrDefault(product.getId(), List.of()));
        }).toList();
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

    @Override
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public String uploadProductImage(MultipartFile file) {
        return fileStorageService.storeImage(file, FileStorageService.PRODUCT_DIR);
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
        // Tồn chỉ nhận lúc tạo. Sửa tồn sau đó phải qua endpoint kho để còn ghi sổ.
        if (product.getId() == null) {
            product.setStockQuantity(request.getStockQuantity());
        }
        Integer lowStockThreshold = request.getLowStockThreshold();
        if (lowStockThreshold != null) {
            product.setLowStockThreshold(lowStockThreshold);
        }
    }

    private ProductResponse saveProductWithOptions(Product product, ProductRequest request) {
        Product savedProduct = productRepository.save(product);
        syncOptionsAndGroups(savedProduct, request);
        syncImages(savedProduct, request.getImages());
        return toProductResponse(savedProduct);
    }

    /**
     * Bộ ảnh không tham chiếu id ở đâu khác nên xoá hết rồi ghi lại là đủ, khỏi diff.
     * {@code requested == null} (client không gửi field) = giữ nguyên bộ ảnh — khác options,
     * vì các chỗ chỉ sửa 1 field (bật/tắt còn hàng) cũng gọi chung hàm này.
     */
    private void syncImages(Product product, List<String> requested) {
        if (requested == null) {
            return;
        }

        List<String> urls = new ArrayList<>();
        for (String url : requested) {
            String trimmed = url == null ? "" : url.trim();
            if (!trimmed.isEmpty() && !urls.contains(trimmed)) {
                urls.add(trimmed);
            }
        }

        productImageRepository.deleteByProduct_Id(product.getId());
        List<ProductImage> toSave = new ArrayList<>();
        for (int index = 0; index < urls.size(); index++) {
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setImageUrl(urls.get(index));
            image.setSortOrder(index);
            toSave.add(image);
        }
        productImageRepository.saveAll(toSave);
    }

    /** Một lựa chọn đích sau khi gộp nhóm + danh sách phẳng; {@code group == null} = không nhóm. */
    private record TargetOption(String name, BigDecimal extraPrice, OptionGroup group) {
    }

    /**
     * Đồng bộ nhóm + lựa chọn trong MỘT lượt. Hai field của request ({@code optionGroups} và
     * {@code options} phẳng) được gộp thành một danh sách đích rồi diff theo tên — không có hai
     * đường ghi chồng nhau lên {@code product_options}. Bỏ trống cả hai = xoá sạch lựa chọn.
     *
     * <p>Diff theo hướng update-in-place: mục trùng tên giữ NGUYÊN row cũ (giỏ hàng đang tham
     * chiếu {@code product_option_id} không vỡ FK); đổi tên = row mới. Mục biến mất chỉ bị xoá khi
     * không còn dòng giỏ nào tham chiếu, còn thì báo 409 để nhân viên biết chỗ cần dọn.
     *
     * <p>Thứ tự xoá quan trọng: option phải đi trước nhóm. {@code fk_product_options_group} là
     * ON DELETE CASCADE nên xoá nhóm trước sẽ kéo option theo ở tầng DB, và FK của
     * {@code cart_item_options} (không có ON DELETE) nổ constraint thay vì ra 409 tử tế.
     */
    private void syncOptionsAndGroups(Product product, ProductRequest request) {
        // Bỏ trống field = giữ nguyên phần đó; gửi mảng (kể cả rỗng) = thay toàn bộ. Thiếu luật
        // này thì các chỗ chỉ sửa một field (bật/tắt còn hàng) sẽ xoá sạch nhóm + lựa chọn.
        boolean replaceGroups = request.getOptionGroups() != null;
        boolean replaceFlat = request.getOptions() != null;
        if (!replaceGroups && !replaceFlat) {
            return;
        }
        List<OptionGroupRequest> requestedGroups = replaceGroups ? request.getOptionGroups() : List.of();
        List<ProductOptionRequest> flatOptions = replaceFlat ? request.getOptions() : List.of();

        // 1. Nhóm: trùng tên thì giữ row cũ, chỉ sửa cờ/giới hạn/thứ tự (sort_order = vị trí
        //    trong mảng, nên đổi thứ tự nhóm chỉ là đổi thứ tự phần tử ở payload).
        List<OptionGroup> currentGroups =
                optionGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(product.getId());
        Map<String, OptionGroup> staleGroupByName = new LinkedHashMap<>();
        for (OptionGroup group : currentGroups) {
            staleGroupByName.putIfAbsent(group.getName().trim(), group);
        }

        List<OptionGroup> groupsToSave = new ArrayList<>();
        List<OptionGroup> targetGroups = new ArrayList<>();
        for (int index = 0; index < requestedGroups.size(); index++) {
            OptionGroupRequest groupRequest = requestedGroups.get(index);
            String name = groupRequest.getName().trim();
            if (groupRequest.isRequired() && groupRequest.getOptions().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Nhóm bắt buộc '" + name + "' phải có ít nhất một lựa chọn");
            }
            OptionGroup group = staleGroupByName.remove(name);
            if (group == null) {
                group = new OptionGroup();
                group.setProduct(product);
                group.setName(name);
            }
            group.setRequired(groupRequest.isRequired());
            group.setMaxChoices(groupRequest.getMaxChoices());
            group.setSortOrder(index);
            groupsToSave.add(group);
            targetGroups.add(group);
        }
        // saveAll (không chỉ gán field) vì nhóm mới cần có id trước khi lựa chọn trỏ tới.
        // Ghi ở bước 5, sau khi đã kiểm tra xong — xem chú thích ở đó.

        // 2. Danh sách lựa chọn đích: trong nhóm trước, rồi tới danh sách phẳng (group = null).
        List<TargetOption> targets = new ArrayList<>();
        for (int index = 0; index < requestedGroups.size(); index++) {
            OptionGroup group = targetGroups.get(index);
            for (ProductOptionRequest optionRequest : requestedGroups.get(index).getOptions()) {
                targets.add(new TargetOption(optionRequest.getName().trim(), optionRequest.getExtraPrice(), group));
            }
        }
        for (ProductOptionRequest optionRequest : flatOptions) {
            targets.add(new TargetOption(optionRequest.getName().trim(), optionRequest.getExtraPrice(), null));
        }

        // 3. Upsert lựa chọn theo tên.
        List<ProductOption> current = productOptionRepository.findByProductId(product.getId());
        Map<String, ProductOption> staleOptionByName = new LinkedHashMap<>();
        for (ProductOption option : current) {
            staleOptionByName.putIfAbsent(option.getName().trim(), option);
        }

        List<ProductOption> toSave = new ArrayList<>();
        for (TargetOption target : targets) {
            ProductOption option = staleOptionByName.remove(target.name());
            if (option == null) {
                option = new ProductOption();
                option.setProduct(product);
                option.setName(target.name());
            }
            option.setExtraPrice(target.extraPrice());
            option.setGroup(target.group());
            toSave.add(option);
        }

        // 4. Gom mọi lựa chọn sẽ bị xoá (thừa khỏi payload + của nhóm thừa) — Map để một lựa
        //    chọn không bị xử lý hai lần. Chỉ xoá trong phạm vi field vừa gửi lên: phần không
        //    gửi (lựa chọn phẳng khi payload chỉ có nhóm, và ngược lại) phải giữ.
        Map<Long, ProductOption> pendingDelete = new LinkedHashMap<>();
        for (ProductOption stale : staleOptionByName.values()) {
            if (stale.getGroup() != null ? replaceGroups : replaceFlat) {
                pendingDelete.put(stale.getId(), stale);
            }
        }
        if (replaceGroups) {
            for (OptionGroup staleGroup : staleGroupByName.values()) {
                for (ProductOption option : current) {
                    if (option.getGroup() != null && staleGroup.getId().equals(option.getGroup().getId())) {
                        pendingDelete.putIfAbsent(option.getId(), option);
                    }
                }
            }
        }

        // Đếm tham chiếu giỏ hàng TRƯỚC khi xoá bất cứ gì: ném lỗi sớm thì không cần trông cậy
        // vào rollback của transaction để tránh trạng thái nửa vời (đã xoá vài lựa chọn).
        List<String> stillReferenced = new ArrayList<>();
        for (ProductOption stale : pendingDelete.values()) {
            if (cartItemOptionRepository.countByProductOption_IdIn(List.of(stale.getId())) > 0) {
                stillReferenced.add(stale.getName());
            }
        }
        if (!stillReferenced.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Không thể xóa lựa chọn '" + String.join("', '", stillReferenced)
                            + "' vì còn trong giỏ hàng của khách — hãy đổi tên/xóa giỏ liên quan trước");
        }

        // 5. Đã kiểm tra xong mới ghi: lỗi ở trên không để lại row nào mới hay đã xoá, kể cả khi
        //    transaction bọc ngoài không rollback. Nhóm lưu trước để lựa chọn trỏ tới có id.
        optionGroupRepository.saveAll(groupsToSave);
        List<ProductOption> kept = productOptionRepository.saveAll(toSave);
        for (ProductOption stale : pendingDelete.values()) {
            productOptionRepository.delete(stale);
        }
        if (replaceGroups && !staleGroupByName.isEmpty()) {
            optionGroupRepository.deleteAll(List.copyOf(staleGroupByName.values()));
        }

        // Giữ collection trong bộ nhớ khớp với DB cho phần vừa ghi; phần không gửi thì để nguyên.
        if (replaceFlat || replaceGroups) {
            product.setOptions(kept);
        }
        if (replaceGroups) {
            product.setOptionGroups(targetGroups);
        }
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
        Long productId = product.getId();
        Double averageRating = productId != null ? reviewRepository.findAverageRatingByProductId(productId) : null;
        Long totalReviews = productId != null ? reviewRepository.countByProductId(productId) : null;
        List<String> images = productId != null ? imageUrlsOf(productId) : List.of();
        // Query thẳng thay vì đọc collection LAZY của product: sau khi sync, collection trong bộ nhớ
        // có thể chưa phản ánh đúng những dòng vừa thêm/xoá.
        List<ProductOption> options = productId != null
                ? productOptionRepository.findByProductIdOrderByIdAsc(productId) : List.of();
        List<OptionGroup> groups = productId != null
                ? optionGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(productId) : List.of();
        return toProductResponse(product, averageRating, totalReviews, images, options, groups);
    }

    private List<String> imageUrlsOf(Long productId) {
        return productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .map(ProductImage::getImageUrl)
                .toList();
    }

    private ProductResponse toProductResponse(Product product, Double averageRating, Long totalReviews,
            List<String> images, List<ProductOption> options, List<OptionGroup> groups) {
        return ProductResponse.builder()
                .id(product.getId())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .images(images)
                .price(product.getPrice())
                .available(product.isAvailable())
                .featured(product.isFeatured())
                .stockQuantity(product.getStockQuantity())
                .lowStockThreshold(product.getLowStockThreshold())
                .lowStock(product.getStockQuantity() != null
                        && product.getStockQuantity() <= product.getLowStockThreshold())
                .averageRating(averageRating != null ? averageRating : 0.0)
                .totalReviews(totalReviews != null ? totalReviews : 0L)
                .options(options.stream().map(this::toOptionResponse).toList())
                .optionGroups(groups.stream()
                        .map(group -> OptionGroupResponse.builder()
                                .id(group.getId())
                                .name(group.getName())
                                .required(group.isRequired())
                                .maxChoices(group.getMaxChoices())
                                .sortOrder(group.getSortOrder())
                                .options(options.stream()
                                        .filter(option -> option.getGroup() != null
                                                && group.getId().equals(option.getGroup().getId()))
                                        .map(this::toOptionResponse)
                                        .toList())
                                .build())
                        .toList())
                .build();
    }

    private ProductOptionResponse toOptionResponse(ProductOption option) {
        return ProductOptionResponse.builder()
                .id(option.getId())
                .name(option.getName())
                .extraPrice(option.getExtraPrice())
                // Lấy id từ proxy LAZY không kích hoạt thêm query.
                .groupId(option.getGroup() != null ? option.getGroup().getId() : null)
                .build();
    }
}
