package com.banhmyking.banhmyking.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.banhmyking.banhmyking.dto.catalog.ComboItemRequest;
import com.banhmyking.banhmyking.dto.catalog.ComboItemResponse;
import com.banhmyking.banhmyking.dto.catalog.ProductRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductResponse;
import com.banhmyking.banhmyking.entity.Category;
import com.banhmyking.banhmyking.enums.ProductType;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.service.CatalogService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo / sửa / xoá combo qua CatalogService trên MariaDB thật: khoá ghép combo_items chỉ lộ lỗi
 * thứ tự flush khi có DB. Mọi thay đổi rollback sau test.
 */
@SpringBootTest
@Transactional
class ComboCatalogIntegrationTest {

    @Autowired private CatalogService catalogService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long categoryId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin-combo-test", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Category category = new Category();
        category.setName("Combo IT " + System.nanoTime());
        categoryId = categoryRepository.save(category).getId();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUpdateAndDeleteComboAgainstRealDatabase() {
        Long banhMi = catalogService.createProduct(single("Bánh mì IT", "30000")).getId();
        Long coffee = catalogService.createProduct(single("Cà phê IT", "20000")).getId();

        Long comboId = catalogService.createProduct(combo("60000", line(banhMi, 1), line(coffee, 2))).getId();
        flushAndClear();
        ProductResponse created = catalogService.getProduct(comboId);
        assertThat(created.getProductType()).isEqualTo(ProductType.COMBO);
        assertThat(created.getCompareAtPrice()).isEqualByComparingTo("70000");
        assertThat(created.getComboItems()).extracting(ComboItemResponse::getName, ComboItemResponse::getQuantity)
                .containsExactly(tuple("Bánh mì IT", 1), tuple("Cà phê IT", 2));

        // Bỏ cà phê, đổi số lượng bánh mì (row giữ nguyên khoá → UPDATE)
        catalogService.updateProduct(comboId, combo("50000", line(banhMi, 2)));
        flushAndClear();
        assertThat(rowsOf(comboId)).isEqualTo(1);

        // Thêm lại cà phê (INSERT) — không đụng khoá chính
        catalogService.updateProduct(comboId, combo("70000", line(banhMi, 2), line(coffee, 1)));
        flushAndClear();
        assertThat(rowsOf(comboId)).isEqualTo(2);
        assertThat(catalogService.getProduct(comboId).getCompareAtPrice()).isEqualByComparingTo("80000");

        // Sửa combo không gửi comboItems = giữ nguyên thành phần
        ProductRequest keep = combo("75000");
        keep.setComboItems(null);
        catalogService.updateProduct(comboId, keep);
        flushAndClear();
        assertThat(rowsOf(comboId)).isEqualTo(2);

        // Tắt cà phê → combo "Tạm hết" ở mức chuỗi nhưng combo vẫn bật
        ProductRequest coffeeOff = single("Cà phê IT", "20000");
        coffeeOff.setAvailable(false);
        catalogService.updateProduct(coffee, coffeeOff);
        flushAndClear();
        ProductResponse afterOff = catalogService.getProduct(comboId);
        assertThat(afterOff.isAvailable()).isFalse();
        assertThat(afterOff.isEnabled()).isTrue();

        // Xoá món đang trong combo bị chặn; xoá combo trước thì xoá được
        assertThatThrownBy(() -> catalogService.deleteProduct(banhMi))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Món đang nằm trong combo");
        catalogService.deleteProduct(comboId);
        flushAndClear();
        assertThat(rowsOf(comboId)).isEqualTo(2); // combo_items giữ lại cho lịch sử
        catalogService.deleteProduct(banhMi);
    }

    private int rowsOf(Long comboId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM combo_items WHERE combo_id = ?", Integer.class, comboId);
        return count == null ? 0 : count;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private ProductRequest single(String name, String price) {
        ProductRequest request = new ProductRequest();
        request.setCategoryId(categoryId);
        request.setName(name);
        request.setPrice(new BigDecimal(price));
        return request;
    }

    private ProductRequest combo(String price, ComboItemRequest... lines) {
        ProductRequest request = single("Combo IT", price);
        request.setProductType(ProductType.COMBO);
        request.setComboItems(new ArrayList<>(List.of(lines)));
        return request;
    }

    private static ComboItemRequest line(Long productId, int quantity) {
        ComboItemRequest line = new ComboItemRequest();
        line.setProductId(productId);
        line.setQuantity(quantity);
        return line;
    }
}
