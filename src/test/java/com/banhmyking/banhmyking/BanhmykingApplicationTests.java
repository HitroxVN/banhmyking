package com.banhmyking.banhmyking;

import com.banhmyking.banhmyking.dto.cart.AddToCartRequest;
import com.banhmyking.banhmyking.dto.cart.CartResponse;
import com.banhmyking.banhmyking.dto.cart.UpdateCartItemRequest;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.CartService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BanhmykingApplicationTests {

    @Autowired
    private CartService cartService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    /**
     * Test chạy trên DB dev thật nên KHÔNG được ghi cứng ID (user 1, món 1, option 1-2 khác nhau
     * giữa các máy) và không được để lại dữ liệu: tự tạo user tạm, tự tìm món hợp lệ,
     * @Transactional rollback toàn bộ sau khi chạy.
     */
    @Test
    @Transactional
    void testFullCartFlow() {
        Long productId = findProductUsableInCart();
        Assumptions.assumeTrue(productId != null, "DB chưa có món nào đặt được — bỏ qua test");
        List<Long> optionIds = firstOptionOfEachRequiredGroup(productId);

        User user = new User();
        user.setEmail("cart-flow-" + UUID.randomUUID() + "@test.local");
        user.setPassword("not-used");
        user.setFullName("Cart Flow Test");
        user.setRole(RoleName.CUSTOMER);
        Long userId = userRepository.save(user).getId();

        // Full flow integration test: Add -> Get -> Update -> Remove
        AddToCartRequest addRequest = AddToCartRequest.builder()
                .productId(productId)
                .quantity(2)
                .optionIds(optionIds)
                .build();
        CartResponse addResponse = cartService.addToCart(userId, addRequest);
        Long itemId = addResponse.getItems().get(0).getId();

        CartResponse getResponse = cartService.getCart(userId);
        assertFalse(getResponse.getItems().isEmpty());

        UpdateCartItemRequest updateRequest = UpdateCartItemRequest.builder()
                .quantity(5)
                .build();
        CartResponse updateResponse = cartService.updateItemQuantity(userId, itemId, updateRequest);
        assertEquals(5, updateResponse.getItems().get(0).getQuantity());

        CartResponse removeResponse = cartService.removeItem(userId, itemId);
        assertTrue(removeResponse.getItems().isEmpty());
    }

    /** Món đang bán, đủ tồn cho 5 phần, và mọi nhóm bắt buộc đều có ít nhất 1 lựa chọn. */
    private Long findProductUsableInCart() {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT p.id FROM products p
                WHERE p.is_deleted = FALSE AND p.is_available = TRUE
                  AND (p.stock_quantity IS NULL OR p.stock_quantity >= 5)
                  AND NOT EXISTS (
                      SELECT 1 FROM option_groups g
                      WHERE g.product_id = p.id AND g.is_required = TRUE
                        AND NOT EXISTS (SELECT 1 FROM product_options o WHERE o.group_id = g.id))
                ORDER BY p.id
                LIMIT 1
                """, Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private List<Long> firstOptionOfEachRequiredGroup(Long productId) {
        return jdbcTemplate.queryForList("""
                SELECT MIN(o.id) FROM option_groups g
                JOIN product_options o ON o.group_id = g.id
                WHERE g.product_id = ? AND g.is_required = TRUE
                GROUP BY g.id
                """, Long.class, productId);
    }
}
