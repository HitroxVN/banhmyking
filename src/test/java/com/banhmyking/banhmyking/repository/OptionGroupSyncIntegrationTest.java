package com.banhmyking.banhmyking.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.banhmyking.banhmyking.dto.catalog.OptionGroupRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductOptionRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductRequest;
import com.banhmyking.banhmyking.dto.catalog.ProductResponse;
import com.banhmyking.banhmyking.entity.Cart;
import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.CartItemOption;
import com.banhmyking.banhmyking.entity.Category;
import com.banhmyking.banhmyking.entity.OptionGroup;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.ProductOption;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.service.CatalogService;

import jakarta.persistence.EntityManager;

/**
 * Đồng bộ nhóm + lựa chọn của món trên MySQL thật.
 *
 * <p>Mock không kiểm được phần này: ràng buộc FK {@code cart_item_options.product_option_id}
 * và thứ tự xoá chỉ lộ ra khi có DB. Đây cũng là chỗ dễ mất dữ liệu nhất — một payload chỉ
 * gửi {@code available} từng xoá sạch nhóm và lựa chọn của món.
 */
@SpringBootTest
@Transactional
class OptionGroupSyncIntegrationTest {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private EntityManager entityManager;

    /** Service đứng sau {@code @PreAuthorize("hasAnyRole('STAFF','ADMIN')")} — phải có principal. */
    @BeforeEach
    void authenticateAsStaff() {
        var auth = new UsernamePasswordAuthenticationToken(
                "staff-sync-test", null, List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Tạo món kèm nhóm: nhóm và lựa chọn được ghi đúng, giữ thứ tự payload")
    void createProductPersistsGroupsAndSelections() {
        Long productId = createProductWithGroups();

        ProductResponse response = catalogService.getProduct(productId);

        assertThat(response.getOptionGroups()).extracting("name", "required", "maxChoices", "sortOrder")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Size", true, 1, 0),
                        org.assertj.core.groups.Tuple.tuple("Topping", false, 0, 1));
        assertThat(response.getOptionGroups().get(0).getOptions()).extracting("name")
                .containsExactly("Nhỏ", "Lớn");
        assertThat(response.getOptionGroups().get(1).getOptions()).extracting("name")
                .containsExactly("Trứng");
        // Lựa chọn thuộc nhóm phải mang groupId, để FE không vẽ nhầm chúng thành topping phẳng.
        assertThat(response.getOptions()).extracting("name", "groupId")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Nhỏ", response.getOptionGroups().get(0).getId()),
                        org.assertj.core.groups.Tuple.tuple("Lớn", response.getOptionGroups().get(0).getId()),
                        org.assertj.core.groups.Tuple.tuple("Trứng", response.getOptionGroups().get(1).getId()));
    }

    @Test
    @DisplayName("Bỏ một nhóm khỏi payload: nhóm và lựa chọn của nó biến mất khỏi DB")
    void droppingGroupDeletesItsRowsFromDatabase() {
        Long productId = createProductWithGroups();

        ProductRequest request = baseRequest(productId, "available-only");
        request.setOptionGroups(List.of(groupRequest("Topping", false, 0, "Trứng")));
        catalogService.updateProduct(productId, request);

        ProductResponse response = catalogService.getProduct(productId);
        assertThat(response.getOptionGroups()).extracting("name").containsExactly("Topping");
        assertThat(response.getOptions()).extracting("name").containsExactly("Trứng");
        assertThat(groupNamesInDb(productId)).containsExactly("Topping");
    }

    @Test
    @DisplayName("Chỉ gửi available: nhóm và lựa chọn cũ giữ nguyên (chống hồi quy mất dữ liệu)")
    void updatingOnlyAvailabilityLeavesSelectionsUntouched() {
        Long productId = createProductWithGroups();

        ProductRequest request = baseRequest(productId, "available-only");
        request.setAvailable(false);
        catalogService.updateProduct(productId, request);

        ProductResponse response = catalogService.getProduct(productId);
        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getOptionGroups()).extracting("name").containsExactly("Size", "Topping");
        assertThat(response.getOptions()).extracting("name").containsExactlyInAnyOrder("Nhỏ", "Lớn", "Trứng");
    }

    @Test
    @DisplayName("Lựa chọn của nhóm bị bỏ còn trong giỏ khách: chặn 409 và không xoá gì")
    void rejectsDroppingGroupWhoseSelectionsAreStillInACart() {
        Long productId = createProductWithGroups();
        Long optionId = optionIdOf(productId, "Nhỏ");
        putOptionInACart(productId, optionId);

        ProductRequest request = baseRequest(productId, "available-only");
        request.setOptionGroups(List.of(groupRequest("Topping", false, 0, "Trứng")));

        assertThatThrownBy(() -> catalogService.updateProduct(productId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Nhỏ");

        // Nhóm Size + lựa chọn của nó phải còn nguyên: xoá nhóm trước sẽ CASCADE và nổ FK.
        assertThat(groupNamesInDb(productId)).containsExactlyInAnyOrder("Size", "Topping");
        assertThat(optionNamesInDb(productId)).containsExactlyInAnyOrder("Nhỏ", "Lớn", "Trứng");
        assertThat(optionIdOf(productId, "Nhỏ")).isEqualTo(optionId);
    }

    @Test
    @DisplayName("Đổi thứ tự nhóm và lựa chọn: giữ nguyên row id (giỏ hàng trỏ tới không vỡ)")
    void reorderingGroupsAndSelectionsKeepsSameRowIds() {
        Long productId = createProductWithGroups();
        Long smallId = optionIdOf(productId, "Nhỏ");
        Long largeId = optionIdOf(productId, "Lớn");

        ProductRequest request = baseRequest(productId, "reorder");
        request.setOptionGroups(List.of(
                groupRequest("Topping", false, 0, "Trứng"),
                groupRequest("Size", true, 1, "Lớn", "Nhỏ")));
        catalogService.updateProduct(productId, request);

        assertThat(optionIdOf(productId, "Nhỏ")).isEqualTo(smallId);
        assertThat(optionIdOf(productId, "Lớn")).isEqualTo(largeId);
        assertThat(groupNamesInDb(productId)).containsExactly("Topping", "Size");
    }

    @Test
    @DisplayName("Đổi tên lựa chọn = row mới: giỏ hàng cũ chặn lại bằng 409 thay vì trỏ vào row đã xoá")
    void renamingSelectionReplacesTheRow() {
        Long productId = createProductWithGroups();
        Long before = optionIdOf(productId, "Nhỏ");
        putOptionInACart(productId, before);

        ProductRequest request = baseRequest(productId, "rename");
        request.setOptionGroups(List.of(
                groupRequest("Size", true, 1, "Nhỏ xíu", "Lớn"),
                groupRequest("Topping", false, 0, "Trứng")));

        // Ghép lựa chọn theo TÊN (payload không mang id), nên đổi tên là xoá row cũ + tạo row mới.
        assertThatThrownBy(() -> catalogService.updateProduct(productId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Nhỏ");

        assertThat(optionNamesInDb(productId)).containsExactlyInAnyOrder("Nhỏ", "Lớn", "Trứng");
    }

    // ─── Dựng dữ liệu ────────────────────────────────────────────────────────────

    private Long createProductWithGroups() {
        Category category = new Category();
        category.setName("Sync Test " + System.nanoTime());
        entityManager.persist(category);
        entityManager.flush();

        ProductRequest request = new ProductRequest();
        request.setCategoryId(category.getId());
        request.setName("Bánh mì sync test");
        request.setPrice(BigDecimal.valueOf(30000));
        request.setAvailable(true);
        request.setOptionGroups(List.of(
                groupRequest("Size", true, 1, "Nhỏ", "Lớn"),
                groupRequest("Topping", false, 0, "Trứng")));

        return catalogService.createProduct(request).getId();
    }

    /** Payload tối thiểu để update một món đã có, không đụng tới nhóm/lựa chọn. */
    private ProductRequest baseRequest(Long productId, String nameSuffix) {
        Product product = entityManager.find(Product.class, productId);
        ProductRequest request = new ProductRequest();
        request.setCategoryId(product.getCategory().getId());
        request.setName(product.getName());
        request.setPrice(product.getPrice());
        request.setAvailable(product.isAvailable());
        request.setDescription(nameSuffix);
        return request;
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

    private void putOptionInACart(Long productId, Long optionId) {
        User user = new User();
        user.setEmail("optgroupsync-" + System.nanoTime() + "@test.com");
        user.setPassword("password123");
        user.setFullName("Khách Test");
        user.setRole(RoleName.CUSTOMER);
        entityManager.persist(user);

        Cart cart = new Cart();
        cart.setUser(user);
        entityManager.persist(cart);

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(entityManager.find(Product.class, productId));
        item.setQuantity(1);
        entityManager.persist(item);

        CartItemOption link = new CartItemOption();
        link.setCartItem(item);
        link.setProductOption(entityManager.find(ProductOption.class, optionId));
        entityManager.persist(link);
        entityManager.flush();
    }

    private Long optionIdOf(Long productId, String name) {
        return (Long) entityManager
                .createNativeQuery("SELECT id FROM product_options WHERE product_id = :pid AND name = :name")
                .setParameter("pid", productId)
                .setParameter("name", name)
                .getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private List<String> groupNamesInDb(Long productId) {
        return entityManager
                .createNativeQuery("SELECT name FROM option_groups WHERE product_id = :pid ORDER BY sort_order")
                .setParameter("pid", productId)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<String> optionNamesInDb(Long productId) {
        return entityManager
                .createNativeQuery("SELECT name FROM product_options WHERE product_id = :pid ORDER BY id")
                .setParameter("pid", productId)
                .getResultList();
    }
}
