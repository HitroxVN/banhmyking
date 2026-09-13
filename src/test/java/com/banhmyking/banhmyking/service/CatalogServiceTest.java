package com.banhmyking.banhmyking.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banhmyking.banhmyking.dto.catalog.ProductOptionRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductRequest;
import com.banhmyking.banhmyking.entity.Category;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.ProductOption;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.CartItemOptionRepository;
import com.banhmyking.banhmyking.repository.CategoryRepository;
import com.banhmyking.banhmyking.repository.ProductOptionRepository;
import com.banhmyking.banhmyking.repository.ProductRepository;
import com.banhmyking.banhmyking.service.impl.CatalogServiceImpl;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @Mock
    private CartItemOptionRepository cartItemOptionRepository;

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

    @Test
    void getProductsUsesAvailableFilter() {
        when(productRepository.findByAvailableTrueAndDeletedFalseOrderByFeaturedDescNameAsc())
                .thenReturn(List.of());

        catalogService.getProducts(null, true);

        verify(productRepository).findByAvailableTrueAndDeletedFalseOrderByFeaturedDescNameAsc();
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
        when(productOptionRepository.save(any(ProductOption.class))).thenAnswer(inv -> inv.getArgument(0));

        catalogService.updateProduct(10L, productRequestWithOption("Thêm pate", BigDecimal.valueOf(6000)));

        // Cùng row id=101 được update giá — không có delete, id giỏ hàng còn tham chiếu hợp lệ
        verify(productOptionRepository, never()).delete(any(ProductOption.class));
        verify(productOptionRepository, never()).deleteAll(any());
        verify(productOptionRepository).save(org.mockito.ArgumentMatchers.<ProductOption>argThat(
                o -> o.getId().equals(101L) && o.getExtraPrice().compareTo(BigDecimal.valueOf(6000)) == 0));
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
        when(productOptionRepository.save(any(ProductOption.class))).thenAnswer(inv -> inv.getArgument(0));

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
        when(productOptionRepository.save(any(ProductOption.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> catalogService.updateProduct(10L, productRequestWithOption("Thêm trứng", BigDecimal.valueOf(4000))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Phô mai");

        verify(productOptionRepository, never()).delete(any(ProductOption.class));
    }
}
