package com.banhmyking.banhmyking.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.banhmyking.banhmyking.dto.catalog.OptionGroupRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductOptionRequest;
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
import com.banhmyking.banhmyking.service.impl.CatalogServiceImpl;
import com.banhmyking.banhmyking.util.PageableFactory;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @Mock
    private OptionGroupRepository optionGroupRepository;

    @Mock
    private CartItemOptionRepository cartItemOptionRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private CatalogServiceImpl catalogService;

    @Test
    void createProductRejectsMissingCategory() {
        ProductRequest request = new ProductRequest();
        request.setCategoryId(99L);
        request.setName("Banh mi");
        request.setPrice(BigDecimal.valueOf(30000));
        when(categoryRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.createProduct(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteProductUsesSoftDelete() {
        Product product = new Product();
        product.setId(10L);
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(product));

        catalogService.deleteProduct(10L);

        org.assertj.core.api.Assertions.assertThat(product.isDeleted()).isTrue();
        org.assertj.core.api.Assertions.assertThat(product.isAvailable()).isFalse();
        verify(productRepository).save(product);
    }

    // ─── Tìm kiếm / lọc / sắp xếp / phân trang (server-side) ────────────────

    @Test
    void getProductsReturnsPageEnvelopeWithEnrichedItems() {
        Category category = new Category();
        category.setId(5L);
        category.setName("Mặn");
        Product product = new Product();
        product.setId(10L);
        product.setCategory(category);
        product.setName("Bánh mì Đặc Biệt");
        product.setPrice(BigDecimal.valueOf(35000));

        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 12), 30));
        when(reviewRepository.summarizeByProductIds(List.of(10L)))
                .thenReturn(List.<Object[]>of(new Object[] { 10L, 4.5, 7L }));
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl("/img/banh-mi.jpg");
        when(productImageRepository.findByProductIdInOrderBySortOrderAscIdAsc(List.of(10L)))
                .thenReturn(List.of(image));

        PageResponse<ProductResponse> result =
                catalogService.getProducts(null, true, null, null, null, null, ProductSort.FEATURED, 0, 12);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).getName()).isEqualTo("Bánh mì Đặc Biệt");
        assertThat(result.content().get(0).getAverageRating()).isEqualTo(4.5);
        assertThat(result.content().get(0).getTotalReviews()).isEqualTo(7L);
        assertThat(result.content().get(0).getImages()).containsExactly("/img/banh-mi.jpg");
        // Metadata phân trang lấy từ Page gốc, không phải từ số phần tử của trang
        assertThat(result.totalElements()).isEqualTo(30);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.last()).isFalse();
    }

    @Test
    void getProductsPassesPageSizeAndSortThroughAndClampsOversize() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12), 0));

        catalogService.getProducts(null, true, null, null, null, null, ProductSort.PRICE_DESC, 2, 999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(any(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(PageableFactory.MAX_SIZE);
        assertThat(pageable.getSort().getOrderFor("price").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    /** Sort theo giá phải kèm khoá phụ theo tên, nếu không hai món cùng giá đổi chỗ giữa hai trang. */
    @Test
    void productSortWhitelistMapsToColumnsWithTieBreaker() {
        assertThat(ProductSort.FEATURED.toSort().getOrderFor("featured").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(ProductSort.PRICE_ASC.toSort().getOrderFor("price").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(ProductSort.NEWEST.toSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(ProductSort.PRICE_DESC.toSort().getOrderFor("name")).isNotNull();
    }

    /**
     * Collation coi `đ` khác `d`, nên từ khoá gõ không dấu phải sinh thêm biến thể `đ` mới khớp
     * được tên món có dấu. Đây là logic dễ bị "dọn dẹp" mất mà lỗi lại im lặng trên UI.
     */
    @Test
    void searchKeywordAddsDStrokeVariantWhenKeywordHasD() {
        List<String> patterns = captureLikePatterns("dac biet");

        assertThat(patterns).contains("%dac biet%", "%đac biet%");
    }

    @Test
    void searchKeywordWithoutDSkipsRedundantVariant() {
        List<String> patterns = captureLikePatterns("banh mi");

        assertThat(patterns).containsExactly("%banh mi%", "%banh mi%");
    }

    /**
     * Lọc khoảng giá: chỉ thêm điều kiện khi mốc là số dương. Mốc 0/âm coi như khách để trống —
     * nếu vẫn gửi xuống, món rẻ nhất cũng bị loại và thực đơn trông như trống.
     */
    @Test
    void searchAddsPriceBoundsOnlyForPositiveValues() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        Root<Product> root = mock(Root.class);
        @SuppressWarnings("unchecked")
        Path<Object> path = mock(Path.class);
        when(root.get(anyString())).thenReturn(path);

        ProductSpecifications.search(null, true, null, null,
                        BigDecimal.valueOf(20000), BigDecimal.valueOf(50000))
                .toPredicate(root, null, cb);

        verify(cb).greaterThanOrEqualTo(any(), eq(BigDecimal.valueOf(20000)));
        verify(cb).lessThanOrEqualTo(any(), eq(BigDecimal.valueOf(50000)));
    }

    @Test
    void searchSkipsPriceBoundsWhenZeroOrNull() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        Root<Product> root = mock(Root.class);
        @SuppressWarnings("unchecked")
        Path<Object> path = mock(Path.class);
        when(root.get(anyString())).thenReturn(path);

        ProductSpecifications.search(null, true, null, null, BigDecimal.ZERO, null)
                .toPredicate(root, null, cb);

        verify(cb, never()).greaterThanOrEqualTo(any(), eq(BigDecimal.ZERO));
        verify(cb, never()).lessThanOrEqualTo(any(), any(BigDecimal.class));
    }

    private List<String> captureLikePatterns(String keyword) {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        Root<Product> root = mock(Root.class);
        @SuppressWarnings("unchecked")
        Path<Object> path = mock(Path.class);
        when(root.get(anyString())).thenReturn(path);

        ProductSpecifications.search(null, true, keyword, null, null, null).toPredicate(root, null, cb);

        ArgumentCaptor<String> patternCaptor = ArgumentCaptor.forClass(String.class);
        verify(cb, atLeast(2)).like(any(), patternCaptor.capture());
        // cột phải được lọc mềm + chỉ lấy món đang bán
        verify(cb).isFalse(any());
        verify(cb).isTrue(any());
        return patternCaptor.getAllValues();
    }

    // ─── updateProduct phải giữ option id (FK giỏ hàng), không delete-hard ─────────

    private ProductRequest productRequestWithOption(String name, BigDecimal extra) {
        ProductOptionRequest option = new ProductOptionRequest();
        option.setName(name);
        option.setExtraPrice(extra);
        ProductRequest request = new ProductRequest();
        request.setCategoryId(5L);
        request.setName("Bánh mì");
        request.setPrice(BigDecimal.valueOf(30000));
        request.setOptions(new ArrayList<>(List.of(option)));
        return request;
    }

    private ProductOption existingOption(Long id, String name, BigDecimal extra) {
        ProductOption option = new ProductOption();
        option.setId(id);
        option.setName(name);
        option.setExtraPrice(extra);
        return option;
    }

    @Test
    void updateProductKeepsExistingOptionRowAndOnlyUpdatesPrice() {
        Product product = new Product();
        product.setId(10L);
        Category category = new Category();
        category.setId(5L);
        category.setName("Mặn");
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productOptionRepository.findByProductId(10L))
                .thenReturn(new ArrayList<>(List.of(existingOption(101L, "Thêm pate", BigDecimal.valueOf(5000)))));
        when(productOptionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        catalogService.updateProduct(10L, productRequestWithOption("Thêm pate", BigDecimal.valueOf(6000)));

        // Cùng row id=101 được update giá — không có delete, id giỏ hàng còn tham chiếu hợp lệ
        verify(productOptionRepository, never()).delete(any(ProductOption.class));
        verify(productOptionRepository, never()).deleteAll(any());
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<ProductOption>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(productOptionRepository).saveAll(captor.capture());
        // một lần saveAll duy nhất, row id=101 update giá — id không đổi
        org.assertj.core.api.Assertions.assertThat(captor.getValue())
                .singleElement()
                .satisfies(o -> {
                    org.assertj.core.api.Assertions.assertThat(o.getId()).isEqualTo(101L);
                    org.assertj.core.api.Assertions.assertThat(o.getExtraPrice())
                            .isEqualByComparingTo(BigDecimal.valueOf(6000));
                });
    }

    @Test
    void updateProductRemovesUnreferencedOptionHardDeleteAllowed() {
        Product product = new Product();
        product.setId(10L);
        Category category = new Category();
        category.setId(5L);
        category.setName("Mặn");
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        // DB còn option "Phô mai" nhưng request không nhắc tới, và không giỏ hàng nào tham chiếu
        when(productOptionRepository.findByProductId(10L))
                .thenReturn(new ArrayList<>(List.of(existingOption(102L, "Phô mai", BigDecimal.valueOf(10000)))));
        when(cartItemOptionRepository.countByProductOption_IdIn(anyCollection())).thenReturn(0L);
        when(productOptionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        catalogService.updateProduct(10L, productRequestWithOption("Thêm trứng", BigDecimal.valueOf(4000)));

        verify(productOptionRepository).delete(org.mockito.ArgumentMatchers.<ProductOption>argThat(
                o -> o.getId().equals(102L)));
    }

    @Test
    void updateProductRefusesToDropStillReferencedOption() {
        Product product = new Product();
        product.setId(10L);
        Category category = new Category();
        category.setId(5L);
        category.setName("Mặn");
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productOptionRepository.findByProductId(10L))
                .thenReturn(new ArrayList<>(List.of(existingOption(102L, "Phô mai", BigDecimal.valueOf(10000)))));
        when(cartItemOptionRepository.countByProductOption_IdIn(anyCollection())).thenReturn(3L); // còn giỏ hàng dùng

        assertThatThrownBy(() -> catalogService.updateProduct(10L, productRequestWithOption("Thêm trứng", BigDecimal.valueOf(4000))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Phô mai");

        // Chặn trước khi ghi: không xoá và cũng không thêm row mới nào.
        verify(productOptionRepository, never()).delete(any(ProductOption.class));
        verify(productOptionRepository, never()).saveAll(any());
    }

    @Test
    void uploadProductImage_delegatesToStorageWithProductsDir() {
        // Validate định dạng/dung lượng nằm ở FileStorageService (đã có test riêng);
        // ở đây chỉ chốt rằng ảnh sản phẩm đi đúng thư mục "products".
        org.springframework.mock.web.MockMultipartFile imageFile =
                new org.springframework.mock.web.MockMultipartFile("file", "banhmi.png", "image/png", new byte[]{1, 2, 3});
        when(fileStorageService.storeImage(imageFile, FileStorageService.PRODUCT_DIR))
                .thenReturn("/uploads/products/abc.png");

        assertThat(catalogService.uploadProductImage(imageFile)).isEqualTo("/uploads/products/abc.png");
    }

    @Test
    @DisplayName("request gửi options = [] → hiểu là xoá hết, option không tham chiếu bị xoá")
    void updateProductWithEmptyOptionsClearsFlatOptions() {
        Product product = new Product();
        product.setId(10L);
        Category category = new Category();
        category.setId(5L);
        category.setName("Mặn");
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productOptionRepository.findByProductId(10L))
                .thenReturn(new ArrayList<>(List.of(existingOption(102L, "Phô mai", BigDecimal.valueOf(10000)))));
        when(cartItemOptionRepository.countByProductOption_IdIn(anyCollection())).thenReturn(0L);
        when(productOptionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = new ProductRequest();
        request.setCategoryId(5L);
        request.setName("Bánh mì");
        request.setPrice(BigDecimal.valueOf(30000));
        request.setOptions(List.of());

        catalogService.updateProduct(10L, request);

        verify(productOptionRepository).delete(org.mockito.ArgumentMatchers.<ProductOption>argThat(
                o -> o.getId().equals(102L)));
    }

    @Test
    @DisplayName("Không gửi options lẫn optionGroups: lựa chọn và nhóm cũ giữ nguyên")
    void updateProductWithoutOptionsFieldsLeavesSelectionsUntouched() {
        // Đường đi của "bật/tắt còn hàng": chỉ gửi available. Trước đây hiểu null = xoá sạch,
        // nên thao tác đó âm thầm xoá nhóm + lựa chọn của món.
        OptionGroup group = existingGroup(300L, "Size", 1);
        ProductOption grouped = existingOption(101L, "Lớn", BigDecimal.valueOf(5000));
        grouped.setGroup(group);
        ProductOption flat = existingOption(102L, "Phô mai", BigDecimal.valueOf(10000));
        Product product = new Product();
        product.setId(10L);
        Category category = new Category();
        category.setId(5L);
        category.setName("Mặn");
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = new ProductRequest();
        request.setCategoryId(5L);
        request.setName("Bánh mì");
        request.setPrice(BigDecimal.valueOf(30000));
        request.setAvailable(false);

        catalogService.updateProduct(10L, request);

        assertThat(product.isAvailable()).isFalse();
        // Không chạm gì tới lựa chọn: không đọc, không ghi, không xoá.
        verify(productOptionRepository, never()).findByProductId(any());
        verify(productOptionRepository, never()).delete(any(ProductOption.class));
        verify(productOptionRepository, never()).saveAll(any());
        verify(optionGroupRepository, never()).deleteAll(anyCollection());
        assertThat(grouped.getGroup()).isSameAs(group);
        assertThat(flat.getGroup()).isNull();
    }

    @Test
    @DisplayName("nhiều option → 1 lần saveAll duy nhất (hết save per-row N+1)")
    void updateProductSavesAllOptionsInSingleBatch() {
        Product product = new Product();
        product.setId(10L);
        Category category = new Category();
        category.setId(5L);
        category.setName("Mặn");
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productOptionRepository.findByProductId(10L)).thenReturn(new ArrayList<>());
        when(productOptionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = new ProductRequest();
        request.setCategoryId(5L);
        request.setName("Bánh mì");
        request.setPrice(BigDecimal.valueOf(30000));
        ProductOptionRequest o1 = new ProductOptionRequest();
        o1.setName("Pate"); o1.setExtraPrice(BigDecimal.valueOf(5000));
        ProductOptionRequest o2 = new ProductOptionRequest();
        o2.setName("Chả lụa"); o2.setExtraPrice(BigDecimal.valueOf(8000));
        ProductOptionRequest o3 = new ProductOptionRequest();
        o3.setName("Dưa góp"); o3.setExtraPrice(BigDecimal.valueOf(3000));
        request.setOptions(new ArrayList<>(List.of(o1, o2, o3)));

        catalogService.updateProduct(10L, request);

        verify(productOptionRepository).saveAll(any());
        verify(productOptionRepository, never()).save(any(ProductOption.class));
    }

    // ─── Bộ ảnh sản phẩm (A3) ────────────────────────────────────────────────

    /** Dựng sẵn product + category cho luồng update, trả về request đã hợp lệ. */
    private ProductRequest stubUpdateProduct() {
        Product product = new Product();
        product.setId(10L);
        Category category = new Category();
        category.setId(5L);
        category.setName("Mặn");
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequest request = new ProductRequest();
        request.setCategoryId(5L);
        request.setName("Bánh mì");
        request.setPrice(BigDecimal.valueOf(30000));
        return request;
    }

    @SuppressWarnings("unchecked")
    private List<ProductImage> savedImages() {
        org.mockito.ArgumentCaptor<List<ProductImage>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(productImageRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Không gửi images (null) → giữ nguyên bộ ảnh cũ, không xoá gì")
    void updateProductWithoutImagesKeepsExistingGallery() {
        catalogService.updateProduct(10L, stubUpdateProduct());

        verify(productImageRepository, never()).deleteByProduct_Id(any());
        verify(productImageRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Gửi images → thay cả bộ ảnh, sortOrder đánh theo đúng thứ tự trong request")
    void updateProductWithImagesReplacesGalleryInOrder() {
        ProductRequest request = stubUpdateProduct();
        request.setImages(List.of("/uploads/products/a.png", "/uploads/products/b.png"));

        catalogService.updateProduct(10L, request);

        verify(productImageRepository).deleteByProduct_Id(10L);
        List<ProductImage> saved = savedImages();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getImageUrl()).isEqualTo("/uploads/products/a.png");
        assertThat(saved.get(0).getSortOrder()).isZero();
        assertThat(saved.get(1).getImageUrl()).isEqualTo("/uploads/products/b.png");
        assertThat(saved.get(1).getSortOrder()).isEqualTo(1);
        assertThat(saved.get(0).getProduct().getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Ảnh rỗng và ảnh trùng bị loại, URL được trim trước khi lưu")
    void updateProductWithImagesFiltersBlankAndDuplicates() {
        ProductRequest request = stubUpdateProduct();
        request.setImages(Arrays.asList("  /uploads/products/a.png  ", "", "  ", null,
                "/uploads/products/a.png", "/uploads/products/b.png"));

        catalogService.updateProduct(10L, request);

        assertThat(savedImages()).extracting(ProductImage::getImageUrl)
                .containsExactly("/uploads/products/a.png", "/uploads/products/b.png");
    }

    @Test
    @DisplayName("Gửi images rỗng → xoá sạch bộ ảnh")
    void updateProductWithEmptyImagesClearsGallery() {
        ProductRequest request = stubUpdateProduct();
        request.setImages(List.of());

        catalogService.updateProduct(10L, request);

        verify(productImageRepository).deleteByProduct_Id(10L);
        assertThat(savedImages()).isEmpty();
    }

    @Test
    @DisplayName("getProduct trả bộ ảnh theo đúng thứ tự đã lưu")
    void getProductReturnsGalleryInOrder() {
        Product product = new Product();
        product.setId(10L);
        Category category = new Category();
        category.setId(5L);
        category.setName("Mặn");
        product.setCategory(category);
        product.setName("Bánh mì");
        product.setPrice(BigDecimal.valueOf(30000));
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(product));

        ProductImage first = new ProductImage();
        first.setImageUrl("/uploads/products/a.png");
        ProductImage second = new ProductImage();
        second.setImageUrl("/uploads/products/b.png");
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(10L))
                .thenReturn(new ArrayList<>(List.of(first, second)));

        assertThat(catalogService.getProduct(10L).getImages())
                .containsExactly("/uploads/products/a.png", "/uploads/products/b.png");
    }

    // ─── Nhóm lựa chọn (size/topping) ──────────────────────────────────────────────

    private OptionGroup existingGroup(Long id, String name, int maxChoices) {
        OptionGroup group = new OptionGroup();
        group.setId(id);
        group.setName(name);
        group.setMaxChoices(maxChoices);
        return group;
    }

    private OptionGroupRequest groupRequest(String name, boolean required, int maxChoices, String... optionNames) {
        OptionGroupRequest group = new OptionGroupRequest();
        group.setName(name);
        group.setRequired(required);
        group.setMaxChoices(maxChoices);
        List<ProductOptionRequest> options = new ArrayList<>();
        for (String optionName : optionNames) {
            ProductOptionRequest option = new ProductOptionRequest();
            option.setName(optionName);
            option.setExtraPrice(BigDecimal.valueOf(5000));
            options.add(option);
        }
        group.setOptions(options);
        return group;
    }

    /** Thêm phần saveAll trả về chính đối số — chỉ luồng đồng bộ nhóm mới chạm tới. */
    private void stubGroupSaves() {
        when(optionGroupRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productOptionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Tạo nhóm mới: lựa chọn được gán đúng nhóm và sort_order theo thứ tự payload")
    void updateProductAssignsOptionsToTheirGroup() {
        ProductRequest request = stubUpdateProduct();
        stubGroupSaves();
        when(optionGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(10L)).thenReturn(new ArrayList<>());

        request.setOptionGroups(List.of(
                groupRequest("Size", true, 1, "Nhỏ", "Lớn"),
                groupRequest("Topping", false, 0, "Trứng")));

        catalogService.updateProduct(10L, request);

        ArgumentCaptor<List<OptionGroup>> groupCaptor = ArgumentCaptor.forClass(List.class);
        verify(optionGroupRepository).saveAll(groupCaptor.capture());
        assertThat(groupCaptor.getValue()).extracting(OptionGroup::getName, OptionGroup::getSortOrder)
                .containsExactly(tuple("Size", 0), tuple("Topping", 1));

        ArgumentCaptor<List<ProductOption>> optionCaptor = ArgumentCaptor.forClass(List.class);
        verify(productOptionRepository).saveAll(optionCaptor.capture());
        assertThat(optionCaptor.getValue()).extracting(ProductOption::getName,
                        option -> option.getGroup().getName())
                .containsExactly(tuple("Nhỏ", "Size"), tuple("Lớn", "Size"), tuple("Trứng", "Topping"));
    }

    @Test
    @DisplayName("Không xoá gì khi nhóm cũ vẫn còn trong payload — chỉ cập nhật tại chỗ")
    void updateProductKeepsGroupAndOptionRowsWhenNamesUnchanged() {
        OptionGroup existing = existingGroup(300L, "Size", 0);
        ProductOption keptOption = existingOption(101L, "Nhỏ", BigDecimal.valueOf(5000));
        keptOption.setGroup(existing);
        ProductRequest request = stubUpdateProduct();
        stubGroupSaves();
        when(optionGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(10L))
                .thenReturn(new ArrayList<>(List.of(existing)));
        when(productOptionRepository.findByProductId(10L)).thenReturn(new ArrayList<>(List.of(keptOption)));

        request.setOptionGroups(List.of(groupRequest("Size", true, 1, "Nhỏ")));

        catalogService.updateProduct(10L, request);

        // Row cũ được giữ nguyên id: giỏ hàng đang tham chiếu product_option_id không vỡ FK.
        ArgumentCaptor<List<ProductOption>> optionCaptor = ArgumentCaptor.forClass(List.class);
        verify(productOptionRepository).saveAll(optionCaptor.capture());
        assertThat(optionCaptor.getValue()).extracting(ProductOption::getId).containsExactly(101L);
        verify(optionGroupRepository, never()).deleteAll(anyCollection());
        assertThat(existing.getMaxChoices()).isEqualTo(1);
    }

    @Test
    @DisplayName("Nhóm bị bỏ khỏi payload: xoá nhóm và toàn bộ lựa chọn của nó")
    void updateProductDeletesGroupAndItsOptionsWhenDropped() {
        OptionGroup dropped = existingGroup(300L, "Size", 1);
        ProductOption mine = existingOption(101L, "Nhỏ", BigDecimal.valueOf(5000));
        mine.setGroup(dropped);
        ProductRequest request = stubUpdateProduct();
        stubGroupSaves();
        when(optionGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(10L))
                .thenReturn(new ArrayList<>(List.of(dropped)));
        when(productOptionRepository.findByProductId(10L)).thenReturn(new ArrayList<>(List.of(mine)));
        request.setOptionGroups(List.of()); // gửi mảng rỗng = xoá hết nhóm

        catalogService.updateProduct(10L, request);

        verify(productOptionRepository).delete(mine);
        verify(optionGroupRepository).deleteAll(List.of(dropped));
    }

    @Test
    @DisplayName("Lựa chọn của nhóm bị bỏ còn nằm trong giỏ: chặn bằng 409 và không xoá gì")
    void updateProductRejectsDroppingGroupWhoseOptionsAreInCarts() {
        OptionGroup dropped = existingGroup(300L, "Size", 1);
        ProductOption mine = existingOption(101L, "Nhỏ", BigDecimal.valueOf(5000));
        mine.setGroup(dropped);
        ProductRequest request = stubUpdateProduct();
        when(optionGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(10L))
                .thenReturn(new ArrayList<>(List.of(dropped)));
        when(productOptionRepository.findByProductId(10L)).thenReturn(new ArrayList<>(List.of(mine)));
        when(cartItemOptionRepository.countByProductOption_IdIn(anyCollection())).thenReturn(2L);
        request.setOptionGroups(List.of());

        assertThatThrownBy(() -> catalogService.updateProduct(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Nhỏ")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        // Xoá nhóm trước sẽ CASCADE ở tầng DB và nổ FK cart_item_options — nên phải không ghi gì.
        verify(productOptionRepository, never()).delete(any(ProductOption.class));
        verify(productOptionRepository, never()).saveAll(any());
        verify(optionGroupRepository, never()).deleteAll(anyCollection());
        verify(optionGroupRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Nhóm bắt buộc mà không có lựa chọn nào thì bị từ chối ngay")
    void updateProductRejectsRequiredGroupWithoutOptions() {
        // Từ chối trước khi chạm product_options nên chỉ cần dựng tới product + category.
        Product product = new Product();
        product.setId(10L);
        Category category = new Category();
        category.setId(5L);
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(optionGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(10L)).thenReturn(new ArrayList<>());

        ProductRequest request = new ProductRequest();
        request.setCategoryId(5L);
        request.setName("Bánh mì");
        request.setPrice(BigDecimal.valueOf(30000));
        request.setOptionGroups(List.of(groupRequest("Size", true, 1)));

        assertThatThrownBy(() -> catalogService.updateProduct(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Size")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("Danh sách phẳng cũ (options, không nhóm) vẫn đồng bộ được như trước")
    void updateProductStillSyncsFlatOptionsWithoutGroups() {
        ProductRequest request = stubUpdateProduct();
        stubGroupSaves();
        ProductOptionRequest flat = new ProductOptionRequest();
        flat.setName("Thêm pate");
        flat.setExtraPrice(BigDecimal.valueOf(6000));
        request.setOptions(new ArrayList<>(List.of(flat)));

        catalogService.updateProduct(10L, request);

        ArgumentCaptor<List<ProductOption>> optionCaptor = ArgumentCaptor.forClass(List.class);
        verify(productOptionRepository).saveAll(optionCaptor.capture());
        assertThat(optionCaptor.getValue()).extracting(ProductOption::getName, ProductOption::getGroup)
                .containsExactly(tuple("Thêm pate", null));
    }
}

