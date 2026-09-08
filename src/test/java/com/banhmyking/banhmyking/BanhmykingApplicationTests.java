package com.banhmyking.banhmyking;

import com.banhmyking.banhmyking.dto.cart.AddToCartRequest;
import com.banhmyking.banhmyking.dto.cart.CartResponse;
import com.banhmyking.banhmyking.dto.cart.UpdateCartItemRequest;
import com.banhmyking.banhmyking.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class BanhmykingApplicationTests {

    @Autowired
    private CartService cartService;

    @Test
    void contextLoads() {
    }

    @Test
    void testFullCartFlow() {
        // Full flow integration test: Add -> Get -> Update -> Remove
        AddToCartRequest addRequest = AddToCartRequest.builder()
                .productId(1L)
                .quantity(2)
                .optionIds(List.of(1L, 2L))
                .build();
        CartResponse addResponse = cartService.addToCart(1L, addRequest);
        Long itemId = addResponse.getItems().get(0).getId();

        CartResponse getResponse = cartService.getCart(1L);
        org.junit.jupiter.api.Assertions.assertFalse(getResponse.getItems().isEmpty());

        UpdateCartItemRequest updateRequest = UpdateCartItemRequest.builder()
                .quantity(5)
                .build();
        CartResponse updateResponse = cartService.updateItemQuantity(1L, itemId, updateRequest);
        org.junit.jupiter.api.Assertions.assertEquals(5, updateResponse.getItems().get(0).getQuantity());

        CartResponse removeResponse = cartService.removeItem(1L, itemId);
        org.junit.jupiter.api.Assertions.assertTrue(removeResponse.getItems().isEmpty());
    }
}
