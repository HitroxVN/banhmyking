package com.banhmyking.banhmyking.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.banhmyking.banhmyking.entity.Category;
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.OrderItemComponent;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.ProductType;
import com.banhmyking.banhmyking.enums.RoleName;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** V14 trên DB dev thật (giống các test repository khác) — mọi thay đổi rollback sau test. */
@SpringBootTest
@Transactional
class ComboSchemaIntegrationTest {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ComboItemRepository comboItemRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void migrationKeepsExistingProductsValid() {
        Integer invalidType = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE product_type NOT IN ('SINGLE', 'COMBO')", Integer.class);
        assertThat(invalidType).isZero();
        // Bất biến luôn đúng kể cả khi admin đã dùng tính năng KM trên DB dev
        // (không giả định DB chưa có giá KM): chỉ món lẻ có giá KM, và 0 < giá KM < giá gốc.
        Integer invalidSale = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE sale_price IS NOT NULL "
                        + "AND (product_type <> 'SINGLE' OR sale_price <= 0 OR sale_price >= price)", Integer.class);
        assertThat(invalidSale).isZero();
    }

    @Test
    void savesComboWithComponentsAndFindsActiveCombosOfAComponent() {
        String tag = tag();
        Category category = categoryRepository.save(category(tag));
        Product banhMi = productRepository.save(product(category, "Bánh mì V14 " + tag, "30000", ProductType.SINGLE));
        Product coffee = productRepository.save(product(category, "Cà phê V14 " + tag, "20000", ProductType.SINGLE));
        Product combo = productRepository.save(product(category, "Combo V14 " + tag, "60000", ProductType.COMBO));
        comboItemRepository.saveAll(List.of(comboItem(combo, banhMi, 1), comboItem(combo, coffee, 2)));
        entityManager.flush();
        entityManager.clear();

        Product loaded = productRepository.findById(combo.getId()).orElseThrow();
        assertThat(loaded.isCombo()).isTrue();
        assertThat(loaded.getComboItems())
                .extracting(item -> item.getComponent().getName(), ComboItem::getQuantity)
                .containsExactlyInAnyOrder(tuple("Bánh mì V14 " + tag, 1), tuple("Cà phê V14 " + tag, 2));
        assertThat(comboItemRepository.findActiveComboNamesContaining(coffee.getId()))
                .containsExactly("Combo V14 " + tag);

        loaded.setDeleted(true);
        entityManager.flush();
        assertThat(comboItemRepository.findActiveComboNamesContaining(coffee.getId())).isEmpty();
    }

    @Test
    void savesSaleWindowAndOrderItemSnapshot() {
        String tag = tag();
        Category category = categoryRepository.save(category(tag));
        Product banhMi = product(category, "Bánh mì KM " + tag, "30000", ProductType.SINGLE);
        banhMi.setSalePrice(new BigDecimal("25000"));
        banhMi.setSaleStartsAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        banhMi.setSaleEndsAt(LocalDateTime.of(2026, 10, 31, 22, 0));
        banhMi = productRepository.save(banhMi);

        Store store = new Store();
        store.setCode("V14" + tag);
        store.setName("Cơ sở V14 " + tag);
        store.setAddress("Địa chỉ thử nghiệm");
        store = storeRepository.save(store);
        User customer = new User();
        customer.setEmail("v14-" + tag.toLowerCase() + "@test.local");
        customer.setPassword("not-used");
        customer.setFullName("Khách V14");
        customer.setRole(RoleName.CUSTOMER);
        customer = userRepository.save(customer);

        Order order = new Order();
        order.setOrderCode("V14-" + tag);
        order.setUser(customer);
        order.setStore(store);
        order.setStatus(OrderStatus.PENDING);
        order.setReceiverName("Khách thử");
        order.setReceiverPhone("0900000000");
        order.setShippingAddress("1 Đường Thử");
        order.setSubtotal(new BigDecimal("45000"));
        order.setTotal(new BigDecimal("45000"));
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(banhMi);
        item.setProductName("Combo snapshot");
        item.setUnitPrice(new BigDecimal("45000"));
        item.setOriginalUnitPrice(new BigDecimal("50000"));
        item.setQuantity(1);
        item.setLineTotal(new BigDecimal("45000"));
        OrderItemComponent component = new OrderItemComponent();
        component.setOrderItem(item);
        component.setProduct(banhMi);
        component.setProductName("Bánh mì KM " + tag);
        component.setQuantity(2);
        item.getComponents().add(component);
        order.getItems().add(item);
        order = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        Product reloaded = productRepository.findById(banhMi.getId()).orElseThrow();
        assertThat(reloaded.getProductType()).isEqualTo(ProductType.SINGLE);
        assertThat(reloaded.getSalePrice()).isEqualByComparingTo("25000");
        assertThat(reloaded.getSaleEndsAt()).isEqualTo(LocalDateTime.of(2026, 10, 31, 22, 0));
        OrderItem loadedItem = orderRepository.findById(order.getId()).orElseThrow().getItems().get(0);
        assertThat(loadedItem.getOriginalUnitPrice()).isEqualByComparingTo("50000");
        assertThat(loadedItem.getComponents()).singleElement().satisfies(c -> {
            assertThat(c.getProductName()).isEqualTo("Bánh mì KM " + tag);
            assertThat(c.getQuantity()).isEqualTo(2);
        });
    }

    private static String tag() {
        return Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    private static Category category(String tag) {
        Category category = new Category();
        category.setName("Danh mục V14 " + tag);
        return category;
    }

    private static Product product(Category category, String name, String price, ProductType type) {
        Product product = new Product();
        product.setCategory(category);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setProductType(type);
        return product;
    }

    private static ComboItem comboItem(Product combo, Product component, int quantity) {
        ComboItem item = new ComboItem();
        item.setId(new ComboItemId(combo.getId(), component.getId()));
        item.setCombo(combo);
        item.setComponent(component);
        item.setQuantity(quantity);
        return item;
    }
}
