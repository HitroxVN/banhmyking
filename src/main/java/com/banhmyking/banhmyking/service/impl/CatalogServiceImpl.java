package com.banhmyking.banhmyking.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.banhmyking.banhmyking.dto.catalog.ComboItemRequest;
import com.banhmyking.banhmyking.dto.catalog.ComboItemResponse;
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
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import com.banhmyking.banhmyking.entity.OptionGroup;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.ProductImage;
import com.banhmyking.banhmyking.entity.ProductOption;
import com.banhmyking.banhmyking.enums.ProductSort;
import com.banhmyking.banhmyking.enums.ProductType;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.CartItemOptionRepository;
import com.banhmyking.banhmyking.repository.CategoryRepository;
import com.banhmyking.banhmyking.repository.ComboItemRepository;
import com.banhmyking.banhmyking.repository.OptionGroupRepository;
import com.banhmyking.banhmyking.repository.ProductImageRepository;
import com.banhmyking.banhmyking.repository.ProductOptionRepository;
import com.banhmyking.banhmyking.repository.ProductRepository;
import com.banhmyking.banhmyking.repository.ReviewRepository;
import com.banhmyking.banhmyking.repository.specification.ProductSpecifications;
import com.banhmyking.banhmyking.service.CatalogService;
import com.banhmyking.banhmyking.service.ComboExpander;
import com.banhmyking.banhmyking.service.ProductPricing;
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
    private final ProductPricing productPricing;
    private final ComboItemRepository comboItemRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findByDeletedFalseOrderBySortOrderAscNameAsc().stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = new Category();
        applyCategory(category, request);
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        Category category = findCategory(categoryId);
        applyCategory(category, request);
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
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
                                                     Boolean onSale, ProductType type,
                                                     ProductSort sort, int page, int size) {
        Page<Product> result = productRepository.findAll(
                ProductSpecifications.search(categoryId, availableOnly, keyword, featured, minPrice, maxPrice,
                        onSale, type, productPricing.now()),
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
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = new Product();
        product.setProductType(request.getProductType() != null ? request.getProductType() : ProductType.SINGLE);
        applyProduct(product, request);
        return saveProductWithOptions(product, request, null);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse updateProduct(Long productId, ProductRequest request) {
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
        if (request.getProductType() != null && request.getProductType() != product.getProductType()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Không thể đổi loại sản phẩm đã tạo (món lẻ ↔ combo)");
        }
        // Giá cũ phải chụp TRƯỚC applyProduct (nó ghi đè giá) — để biết giá combo có đổi không.
        BigDecimal previousPrice = product.getPrice();
        applyProduct(product, request);
        return saveProductWithOptions(product, request, previousPrice);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteProduct(Long productId) {
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
        if (!product.isCombo()) {
            // Tắt món thì combo tự "Tạm hết"; còn xoá thì chặn để combo không mất thành phần (spec §5).
            List<String> combos = comboItemRepository.findActiveComboNamesContaining(productId);
            if (!combos.isEmpty()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Món đang nằm trong combo: " + String.join(", ", combos)
                                + " — hãy sửa hoặc xoá combo trước");
            }
        }
        product.setDeleted(true);
        product.setAvailable(false);
        productRepository.save(product);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
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
        if (product.isCombo()) {
            rejectSaleAndOptionsOnCombo(request);
        } else {
            if (request.getComboItems() != null && !request.getComboItems().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ combo mới có món thành phần");
            }
            applySale(product, request);
        }
    }

    /**
     * Kiểm hết rồi mới ghi: combo sai luật thì không có row nào được lưu.
     * {@code previousPrice} = giá trước khi sửa (null khi tạo).
     */
    private ProductResponse saveProductWithOptions(Product product, ProductRequest request,
                                                   BigDecimal previousPrice) {
        List<ComboLine> comboLines = product.isCombo() ? resolveComboLines(product, request, previousPrice) : null;
        Product savedProduct = productRepository.save(product);
        if (comboLines != null) {
            syncComboItems(savedProduct, comboLines);
        }
        syncOptionsAndGroups(savedProduct, request);
        syncImages(savedProduct, request.getImages());
        return toProductResponse(savedProduct);
    }

    /** Spec §3: salePrice > 0 và < price; ends > starts khi có cả hai. Bỏ trống salePrice = hết KM. */
    private void applySale(Product product, ProductRequest request) {
        BigDecimal salePrice = request.getSalePrice();
        if (salePrice == null) {
            product.setSalePrice(null);
            product.setSaleStartsAt(null);
            product.setSaleEndsAt(null);
            return;
        }
        if (salePrice.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giá khuyến mãi phải lớn hơn 0");
        }
        if (salePrice.compareTo(request.getPrice()) >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giá khuyến mãi phải nhỏ hơn giá gốc");
        }
        if (request.getSaleStartsAt() != null && request.getSaleEndsAt() != null
                && !request.getSaleEndsAt().isAfter(request.getSaleStartsAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Thời điểm kết thúc khuyến mãi phải sau thời điểm bắt đầu");
        }
        product.setSalePrice(salePrice);
        product.setSaleStartsAt(request.getSaleStartsAt());
        product.setSaleEndsAt(request.getSaleEndsAt());
    }

    private void rejectSaleAndOptionsOnCombo(ProductRequest request) {
        if (request.getSalePrice() != null || request.getSaleStartsAt() != null || request.getSaleEndsAt() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Combo không dùng giá khuyến mãi — hãy đặt thẳng giá combo");
        }
        boolean hasOptions = request.getOptions() != null && !request.getOptions().isEmpty();
        boolean hasGroups = request.getOptionGroups() != null && !request.getOptionGroups().isEmpty();
        if (hasOptions || hasGroups) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Combo không có topping hay nhóm lựa chọn");
        }
    }

    /** Một dòng thành phần đã kiểm tra. */
    private record ComboLine(Product component, int quantity) {
    }

    /**
     * Luật combo (spec §3) kiểm TRƯỚC khi ghi. Sửa combo mà {@code comboItems == null} = giữ thành phần
     * cũ và trả null để không đồng bộ lại; kiểm giá combo < tổng giá lẻ chỉ khi giá đổi (một lần
     * bật/tắt không đổi giá không bị chặn dù thành phần đã tăng/giảm giá).
     */
    private List<ComboLine> resolveComboLines(Product combo, ProductRequest request, BigDecimal previousPrice) {
        List<ComboItemRequest> requested = request.getComboItems();
        if (requested == null && combo.getId() != null) {
            boolean priceUnchanged = previousPrice != null && combo.getPrice() != null
                    && previousPrice.compareTo(combo.getPrice()) == 0;
            if (!priceUnchanged) {
                assertComboCheaper(combo.getPrice(), productPricing.originalPrice(combo));
            }
            return null;
        }
        if (requested == null || requested.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Combo phải có ít nhất một món thành phần");
        }
        Map<Long, Integer> quantityById = new LinkedHashMap<>();
        int portions = 0;
        for (ComboItemRequest line : requested) {
            if (line.getProductId() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thiếu món thành phần");
            }
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            if (quantity < 1 || quantity > 20) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số lượng mỗi món trong combo từ 1 đến 20");
            }
            if (quantityById.putIfAbsent(line.getProductId(), quantity) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Món thành phần bị trùng trong combo — hãy gộp số lượng vào một dòng");
            }
            portions += quantity;
        }
        if (portions < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Combo phải có tổng ít nhất 2 phần món");
        }

        Map<Long, Product> found = productRepository.findAllById(quantityById.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        List<ComboLine> lines = new ArrayList<>();
        Map<Product, Integer> components = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : quantityById.entrySet()) {
            Product component = found.get(entry.getKey());
            if (component == null || component.isDeleted()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Không tìm thấy món thành phần với ID: " + entry.getKey());
            }
            if (component.isCombo()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Thành phần combo phải là món lẻ: " + component.getName());
            }
            lines.add(new ComboLine(component, entry.getValue()));
            components.put(component, entry.getValue());
        }
        assertComboCheaper(combo.getPrice(), productPricing.listPriceTotal(components));
        return lines;
    }

    private void assertComboCheaper(BigDecimal comboPrice, BigDecimal original) {
        if (comboPrice == null || comboPrice.compareTo(original) >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    String.format("Giá combo phải thấp hơn tổng giá lẻ của các món (%,.0fđ)", original.doubleValue()));
        }
    }

    /**
     * Diff theo món: món còn trong payload giữ NGUYÊN row (chỉ sửa số lượng) — không xoá rồi chèn lại cùng
     * khoá (combo_id, component_id), vì Hibernate flush INSERT trước DELETE sẽ đụng khoá chính.
     */
    private void syncComboItems(Product combo, List<ComboLine> lines) {
        Map<Long, ComboItem> current = new LinkedHashMap<>();
        for (ComboItem item : combo.getComboItems()) {
            current.put(item.getComponent().getId(), item);
        }
        List<ComboItem> target = new ArrayList<>();
        for (ComboLine line : lines) {
            ComboItem item = current.remove(line.component().getId());
            if (item == null) {
                item = new ComboItem();
                item.setId(new ComboItemId(combo.getId(), line.component().getId()));
                item.setCombo(combo);
                item.setComponent(line.component());
            }
            item.setQuantity(line.quantity());
            target.add(item);
        }
        if (!current.isEmpty()) {
            comboItemRepository.deleteAll(List.copyOf(current.values()));
        }
        comboItemRepository.saveAll(target);
        combo.setComboItems(target);
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
        LocalDateTime now = productPricing.now();
        return ProductResponse.builder()
                .id(product.getId())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .images(images)
                .price(product.getPrice())
                .available(ComboExpander.isChainAvailable(product))
                .enabled(product.isAvailable())
                .featured(product.isFeatured())
                .productType(product.getProductType())
                .salePrice(product.getSalePrice())
                .saleStartsAt(product.getSaleStartsAt())
                .saleEndsAt(product.getSaleEndsAt())
                .effectivePrice(productPricing.effectivePrice(product, now))
                .compareAtPrice(productPricing.compareAtPrice(product, now))
                .discountPercent(productPricing.discountPercent(product, now))
                .onSale(productPricing.isSaleActive(product, now))
                .comboItems(ComboItemResponse.listOf(product))
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
