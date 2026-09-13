package com.banhmyking.banhmyking.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banhmyking.banhmyking.dto.catalog.ProductRequest;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
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
}
