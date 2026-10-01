package com.banhmyking.banhmyking.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;

import com.banhmyking.banhmyking.dto.catalog.StockChangeRequest;
import com.banhmyking.banhmyking.entity.InventoryMovement;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.enums.InventoryReason;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.repository.InventoryMovementRepository;
import com.banhmyking.banhmyking.repository.ProductRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.impl.InventoryServiceImpl;

/**
 * Kiểm tra luật tồn kho: chặn bán quá số đang có, trừ/hoàn đúng một lần cho mỗi đơn,
 * và mọi thay đổi đều để lại dấu vết trong sổ kho.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long ORDER_ID = 7L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    // ---------------------------------------------------------------- assertEnough

    @Test
    @DisplayName("assertEnough: món không quản tồn thì không giới hạn số lượng")
    void assertEnoughSkipsUntrackedProduct() {
        assertThatCode(() -> inventoryService.assertEnough(banhMi(null), 99))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertEnough: đặt đúng bằng số tồn vẫn được (biên)")
    void assertEnoughAllowsExactStock() {
        assertThatCode(() -> inventoryService.assertEnough(banhMi(3), 3))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertEnough: vượt tồn thì báo rõ còn lại bao nhiêu")
    void assertEnoughReportsRemaining() {
        assertThatThrownBy(() -> inventoryService.assertEnough(banhMi(2), 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chỉ còn 2");
    }

    @Test
    @DisplayName("assertEnough: tồn bằng 0 thì báo đã hết hàng")
    void assertEnoughReportsSoldOut() {
        assertThatThrownBy(() -> inventoryService.assertEnough(banhMi(0), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã hết hàng");
    }

    // ------------------------------------------------------------ decreaseForOrder

    @Test
    @DisplayName("decreaseForOrder: trừ tồn và ghi sổ ORDER với số âm")
    void decreaseForOrderDecrementsAndLogs() {
        Order order = order(banhMi(10), 3);
        when(productRepository.decrementStockAtomic(PRODUCT_ID, 3)).thenReturn(1);

        inventoryService.decreaseForOrder(order);

        InventoryMovement movement = captureSavedMovement();
        assertThat(movement.getChangeQty()).isEqualTo(-3);
        assertThat(movement.getReason()).isEqualTo(InventoryReason.ORDER);
        assertThat(movement.getOrder()).isSameAs(order);
    }

    @Test
    @DisplayName("decreaseForOrder: UPDATE trúng 0 row (bị giành hàng) thì ném lỗi và không ghi sổ")
    void decreaseForOrderThrowsWhenAtomicUpdateLoses() {
        Order order = order(banhMi(10), 3);
        when(productRepository.decrementStockAtomic(PRODUCT_ID, 3)).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.decreaseForOrder(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không đủ tồn kho");

        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("decreaseForOrder: món không quản tồn thì bỏ qua, không đụng kho")
    void decreaseForOrderSkipsUntrackedProduct() {
        inventoryService.decreaseForOrder(order(banhMi(null), 3));

        verify(productRepository, never()).decrementStockAtomic(anyLong(), anyInt());
        verify(inventoryMovementRepository, never()).save(any());
    }

    // ------------------------------------------------------------- restoreForOrder

    @Test
    @DisplayName("restoreForOrder: đơn chưa từng bị trừ tồn thì không hoàn (huỷ lúc còn PENDING)")
    void restoreForOrderNoopWhenNothingWasDecreased() {
        when(inventoryMovementRepository.existsByOrderIdAndProductIdAndReason(
                ORDER_ID, PRODUCT_ID, InventoryReason.ORDER)).thenReturn(false);

        inventoryService.restoreForOrder(order(banhMi(10), 3));

        verify(productRepository, never()).incrementStockAtomic(anyLong(), anyInt());
        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("restoreForOrder: đã có sổ RESTORE thì không hoàn lần hai")
    void restoreForOrderNoopWhenAlreadyRestored() {
        when(inventoryMovementRepository.existsByOrderIdAndProductIdAndReason(
                ORDER_ID, PRODUCT_ID, InventoryReason.ORDER)).thenReturn(true);
        when(inventoryMovementRepository.existsByOrderIdAndProductIdAndReason(
                ORDER_ID, PRODUCT_ID, InventoryReason.RESTORE)).thenReturn(true);

        inventoryService.restoreForOrder(order(banhMi(7), 3));

        verify(productRepository, never()).incrementStockAtomic(anyLong(), anyInt());
        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("restoreForOrder: hoàn đúng số đã trừ và ghi sổ RESTORE")
    void restoreForOrderRestoresAndLogs() {
        when(inventoryMovementRepository.existsByOrderIdAndProductIdAndReason(
                ORDER_ID, PRODUCT_ID, InventoryReason.ORDER)).thenReturn(true);
        when(inventoryMovementRepository.existsByOrderIdAndProductIdAndReason(
                ORDER_ID, PRODUCT_ID, InventoryReason.RESTORE)).thenReturn(false);
        when(productRepository.incrementStockAtomic(PRODUCT_ID, 3)).thenReturn(1);

        inventoryService.restoreForOrder(order(banhMi(7), 3));

        InventoryMovement movement = captureSavedMovement();
        assertThat(movement.getChangeQty()).isEqualTo(3);
        assertThat(movement.getReason()).isEqualTo(InventoryReason.RESTORE);
    }

    @Test
    @DisplayName("restoreForOrder: cùng món ở 2 dòng (khác topping) thì hoàn đủ tổng số lượng")
    void restoreForOrderSumsLinesOfSameProduct() {
        Product banhMi = banhMi(7);
        Order order = order(banhMi, 2);
        OrderItem secondLine = new OrderItem();
        secondLine.setProduct(banhMi);
        secondLine.setQuantity(3);
        order.setItems(List.of(order.getItems().get(0), secondLine));
        when(inventoryMovementRepository.existsByOrderIdAndProductIdAndReason(
                ORDER_ID, PRODUCT_ID, InventoryReason.ORDER)).thenReturn(true);
        when(inventoryMovementRepository.existsByOrderIdAndProductIdAndReason(
                ORDER_ID, PRODUCT_ID, InventoryReason.RESTORE)).thenReturn(false);
        when(productRepository.incrementStockAtomic(PRODUCT_ID, 5)).thenReturn(1);

        inventoryService.restoreForOrder(order);

        InventoryMovement movement = captureSavedMovement();
        assertThat(movement.getChangeQty()).isEqualTo(5);
    }

    @Test
    @DisplayName("tryDecreaseForOrder: thiếu hàng thì trả false, cộng trả các dòng đã trừ và không ghi sổ")
    void tryDecreaseForOrderRollsBackEarlierLinesOnShortage() {
        Product first = banhMi(10);
        Product second = new Product();
        second.setId(2L);
        second.setName("Bánh mì pate");
        second.setStockQuantity(1);
        Order order = order(first, 2);
        OrderItem secondLine = new OrderItem();
        secondLine.setProduct(second);
        secondLine.setQuantity(4);
        order.setItems(List.of(order.getItems().get(0), secondLine));
        when(productRepository.decrementStockAtomic(PRODUCT_ID, 2)).thenReturn(1);
        when(productRepository.decrementStockAtomic(2L, 4)).thenReturn(0);

        assertThat(inventoryService.tryDecreaseForOrder(order)).isFalse();

        verify(productRepository).incrementStockAtomic(PRODUCT_ID, 2);
        verify(inventoryMovementRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- adjustStock

    @Test
    @DisplayName("adjustStock: chặn số lượng thay đổi bằng 0")
    void adjustStockRejectsZero() {
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi(10)));

        assertThatThrownBy(() -> inventoryService.adjustStock(PRODUCT_ID, change(0), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("khác 0");
    }

    @Test
    @DisplayName("adjustStock: món chưa quản tồn thì không cho giảm (tránh tồn âm)")
    void adjustStockRejectsNegativeOnUntracked() {
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi(null)));

        assertThatThrownBy(() -> inventoryService.adjustStock(PRODUCT_ID, change(-2), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa quản tồn");

        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustStock: lần nhập đầu tiên đặt luôn con số tồn và ghi sổ IMPORT")
    void adjustStockStartsTrackingOnFirstImport() {
        Product banhMi = banhMi(null);
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi));

        int after = inventoryService.adjustStock(PRODUCT_ID, change(8), 9L);

        assertThat(after).isEqualTo(8);
        assertThat(banhMi.getStockQuantity()).isEqualTo(8);
        verify(productRepository).save(banhMi);

        InventoryMovement movement = captureSavedMovement();
        assertThat(movement.getReason()).isEqualTo(InventoryReason.IMPORT);
        assertThat(movement.getChangeQty()).isEqualTo(8);
    }

    @Test
    @DisplayName("adjustStock: nhập thêm dùng UPDATE nguyên tử rồi đọc lại entity để trả số mới")
    void adjustStockIncrementsAtomically() {
        Product banhMi = banhMi(10);
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi));
        when(productRepository.incrementStockAtomic(PRODUCT_ID, 5)).thenReturn(1);

        int after = inventoryService.adjustStock(PRODUCT_ID, change(5), 9L);

        assertThat(after).isEqualTo(15);
        verify(productRepository).incrementStockAtomic(PRODUCT_ID, 5);
        // Bulk UPDATE không cập nhật entity đang managed -> phải refresh, nếu không response trả số cũ
        verify(entityManager).refresh(banhMi);
    }

    @Test
    @DisplayName("adjustStock: giảm quá số đang có thì báo lỗi kèm tồn hiện tại")
    void adjustStockRejectsTooLargeDecrease() {
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi(2)));
        when(productRepository.decrementStockAtomic(PRODUCT_ID, 5)).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.adjustStock(PRODUCT_ID, change(-5), 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang có 2");

        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustStock: giảm hợp lệ ghi sổ ADJUST với số âm")
    void adjustStockDecrementsAndLogsAdjust() {
        Product banhMi = banhMi(10);
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi));
        when(productRepository.decrementStockAtomic(PRODUCT_ID, 4)).thenReturn(1);

        int after = inventoryService.adjustStock(PRODUCT_ID, change(-4), 9L);

        assertThat(after).isEqualTo(6);
        verify(entityManager).refresh(banhMi);

        InventoryMovement movement = captureSavedMovement();
        assertThat(movement.getReason()).isEqualTo(InventoryReason.ADJUST);
        assertThat(movement.getChangeQty()).isEqualTo(-4);
    }

    // --------------------------------------------------------------------- helpers

    /** @param stock null = món không quản tồn */
    private Product banhMi(Integer stock) {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Bánh mì thập cẩm");
        product.setStockQuantity(stock);
        return product;
    }

    private Order order(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);

        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderCode("BMK-TEST-1");
        order.setItems(List.of(item));
        return order;
    }

    private StockChangeRequest change(int quantity) {
        StockChangeRequest request = new StockChangeRequest();
        request.setChangeQty(quantity);
        return request;
    }

    private InventoryMovement captureSavedMovement() {
        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(captor.capture());
        return captor.getValue();
    }
}
