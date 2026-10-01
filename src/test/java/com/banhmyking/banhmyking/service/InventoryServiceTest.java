package com.banhmyking.banhmyking.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;

import com.banhmyking.banhmyking.dto.catalog.StockChangeRequest;
import com.banhmyking.banhmyking.dto.store.StoreStockResponse;
import com.banhmyking.banhmyking.entity.InventoryMovement;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.StoreProduct;
import com.banhmyking.banhmyking.entity.StoreProductId;
import com.banhmyking.banhmyking.enums.InventoryReason;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.repository.InventoryMovementRepository;
import com.banhmyking.banhmyking.repository.ProductRepository;
import com.banhmyking.banhmyking.repository.StoreProductRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.impl.InventoryServiceImpl;

/**
 * Kiểm tra luật tồn kho theo cơ sở: trừ/hoàn đúng một lần cho mỗi đơn tại cơ sở của đơn,
 * hết món theo cơ sở, và mọi thay đổi đều để lại dấu vết trong sổ kho.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long ORDER_ID = 7L;
    private static final Long STORE_ID = 3L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StoreProductRepository storeProductRepository;

    @Mock
    private InventoryMovementRepository inventoryMovementRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    // ------------------------------------------------------------ decreaseForOrder

    @Test
    @DisplayName("decreaseForOrder: trừ tồn tại cơ sở của đơn và ghi sổ ORDER với số âm")
    void decreaseForOrderDecrementsAndLogs() {
        Order order = order(banhMi(), 3);
        stubTracked(row(10, true));
        when(storeProductRepository.decrementStockAtomic(STORE_ID, PRODUCT_ID, 3)).thenReturn(1);

        inventoryService.decreaseForOrder(order);

        InventoryMovement movement = captureSavedMovement();
        assertThat(movement.getChangeQty()).isEqualTo(-3);
        assertThat(movement.getReason()).isEqualTo(InventoryReason.ORDER);
        assertThat(movement.getOrder()).isSameAs(order);
        assertThat(movement.getStore().getId()).isEqualTo(STORE_ID);
    }

    @Test
    @DisplayName("decreaseForOrder: UPDATE trúng 0 row (bị giành hàng) thì ném lỗi và không ghi sổ")
    void decreaseForOrderThrowsWhenAtomicUpdateLoses() {
        Order order = order(banhMi(), 3);
        stubTracked(row(10, true));
        when(storeProductRepository.decrementStockAtomic(STORE_ID, PRODUCT_ID, 3)).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.decreaseForOrder(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không đủ tồn kho");

        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("decreaseForOrder: món không quản tồn thì bỏ qua, không đụng kho")
    void decreaseForOrderSkipsUntrackedProduct() {
        when(storeProductRepository.findByIdStoreIdAndIdProductIdIn(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of());

        inventoryService.decreaseForOrder(order(banhMi(), 3));

        verify(storeProductRepository, never()).decrementStockAtomic(anyLong(), anyLong(), anyInt());
        verify(inventoryMovementRepository, never()).save(any());
    }

    // ------------------------------------------------------------- restoreForOrder

    @Test
    @DisplayName("restoreForOrder: đơn chưa từng bị trừ tồn thì không hoàn (huỷ lúc còn PENDING)")
    void restoreForOrderNoopWhenNothingWasDecreased() {
        when(inventoryMovementRepository.findByOrderIdAndReason(ORDER_ID, InventoryReason.ORDER))
                .thenReturn(List.of());

        inventoryService.restoreForOrder(order(banhMi(), 3));

        verify(storeProductRepository, never()).incrementStockAtomic(anyLong(), anyLong(), anyInt());
        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("restoreForOrder: đã có sổ RESTORE (cùng cơ sở) thì không hoàn lần hai")
    void restoreForOrderNoopWhenAlreadyRestored() {
        when(inventoryMovementRepository.findByOrderIdAndReason(ORDER_ID, InventoryReason.ORDER))
                .thenReturn(List.of(orderMovement(STORE_ID, banhMi(), -3)));
        when(inventoryMovementRepository.existsByOrderIdAndStoreIdAndProductIdAndReason(
                ORDER_ID, STORE_ID, PRODUCT_ID, InventoryReason.RESTORE)).thenReturn(true);

        inventoryService.restoreForOrder(order(banhMi(), 3));

        verify(storeProductRepository, never()).incrementStockAtomic(anyLong(), anyLong(), anyInt());
        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("restoreForOrder: hoàn đúng số đã trừ về đúng cơ sở và ghi sổ RESTORE")
    void restoreForOrderRestoresAndLogs() {
        when(inventoryMovementRepository.findByOrderIdAndReason(ORDER_ID, InventoryReason.ORDER))
                .thenReturn(List.of(orderMovement(STORE_ID, banhMi(), -3)));
        when(storeProductRepository.incrementStockAtomic(STORE_ID, PRODUCT_ID, 3)).thenReturn(1);

        inventoryService.restoreForOrder(order(banhMi(), 3));

        InventoryMovement movement = captureSavedMovement();
        assertThat(movement.getChangeQty()).isEqualTo(3);
        assertThat(movement.getReason()).isEqualTo(InventoryReason.RESTORE);
        assertThat(movement.getStore().getId()).isEqualTo(STORE_ID);
    }

    @Test
    @DisplayName("restoreForOrder: cùng món ở 2 dòng (khác topping) thì hoàn đủ tổng số lượng")
    void restoreForOrderSumsLinesOfSameProduct() {
        Product banhMi = banhMi();
        Order order = order(banhMi, 2);
        OrderItem secondLine = new OrderItem();
        secondLine.setProduct(banhMi);
        secondLine.setQuantity(3);
        order.setItems(List.of(order.getItems().get(0), secondLine));
        when(inventoryMovementRepository.findByOrderIdAndReason(ORDER_ID, InventoryReason.ORDER))
                .thenReturn(List.of(orderMovement(STORE_ID, banhMi, -2), orderMovement(STORE_ID, banhMi, -3)));
        when(storeProductRepository.incrementStockAtomic(STORE_ID, PRODUCT_ID, 5)).thenReturn(1);

        inventoryService.restoreForOrder(order);

        InventoryMovement movement = captureSavedMovement();
        assertThat(movement.getChangeQty()).isEqualTo(5);
    }

    @Test
    @DisplayName("restoreForOrder: hoàn về cơ sở ghi trên sổ ORDER, không phải order.store (M4 / spec §3.6)")
    void restoreForOrderUsesStoreOfOrderMovementNotCurrentOrderStore() {
        long oldStoreId = 9L;
        Order order = order(banhMi(), 3); // order.store = STORE_ID (cơ sở hiện tại, giả sử đã chuyển)
        when(inventoryMovementRepository.findByOrderIdAndReason(ORDER_ID, InventoryReason.ORDER))
                .thenReturn(List.of(orderMovement(oldStoreId, banhMi(), -3)));
        when(inventoryMovementRepository.existsByOrderIdAndStoreIdAndProductIdAndReason(
                ORDER_ID, oldStoreId, PRODUCT_ID, InventoryReason.RESTORE)).thenReturn(false);
        when(storeProductRepository.incrementStockAtomic(oldStoreId, PRODUCT_ID, 3)).thenReturn(1);

        inventoryService.restoreForOrder(order);

        verify(storeProductRepository, never()).incrementStockAtomic(eq(STORE_ID), anyLong(), anyInt());
        InventoryMovement movement = captureSavedMovement();
        assertThat(movement.getStore().getId()).isEqualTo(oldStoreId);
        assertThat(movement.getReason()).isEqualTo(InventoryReason.RESTORE);
        assertThat(movement.getOrder()).isSameAs(order);
    }

    @Test
    @DisplayName("restoreForOrder: RESTORE ở cơ sở KHÁC không chặn việc hoàn ở cơ sở đã trừ")
    void restoreForOrderAlreadyRestoredCheckIsPerStore() {
        long oldStoreId = 9L;
        when(inventoryMovementRepository.findByOrderIdAndReason(ORDER_ID, InventoryReason.ORDER))
                .thenReturn(List.of(orderMovement(oldStoreId, banhMi(), -3)));
        when(inventoryMovementRepository.existsByOrderIdAndStoreIdAndProductIdAndReason(
                ORDER_ID, oldStoreId, PRODUCT_ID, InventoryReason.RESTORE)).thenReturn(false);
        when(storeProductRepository.incrementStockAtomic(oldStoreId, PRODUCT_ID, 3)).thenReturn(1);

        inventoryService.restoreForOrder(order(banhMi(), 3));

        verify(inventoryMovementRepository, never()).existsByOrderIdAndStoreIdAndProductIdAndReason(
                ORDER_ID, STORE_ID, PRODUCT_ID, InventoryReason.RESTORE);
        verify(storeProductRepository).incrementStockAtomic(oldStoreId, PRODUCT_ID, 3);
    }

    @Test
    @DisplayName("tryDecreaseForOrder: thiếu hàng thì trả false, cộng trả các dòng đã trừ và không ghi sổ")
    void tryDecreaseForOrderRollsBackEarlierLinesOnShortage() {
        Product first = banhMi();
        Product second = new Product();
        second.setId(2L);
        second.setName("Bánh mì pate");
        Order order = order(first, 2);
        OrderItem secondLine = new OrderItem();
        secondLine.setProduct(second);
        secondLine.setQuantity(4);
        order.setItems(List.of(order.getItems().get(0), secondLine));
        StoreProduct secondRow = row(1, true);
        secondRow.setId(new StoreProductId(STORE_ID, 2L));
        stubTracked(row(10, true), secondRow);
        when(storeProductRepository.decrementStockAtomic(STORE_ID, PRODUCT_ID, 2)).thenReturn(1);
        when(storeProductRepository.decrementStockAtomic(STORE_ID, 2L, 4)).thenReturn(0);

        assertThat(inventoryService.tryDecreaseForOrder(order)).isFalse();

        verify(storeProductRepository).incrementStockAtomic(STORE_ID, PRODUCT_ID, 2);
        verify(inventoryMovementRepository, never()).save(any());
    }

    // ------------------------------------------------------------ unavailableItems

    @Test
    @DisplayName("unavailableItems: hết món tại cơ sở hoặc thiếu tồn thì trả tên món")
    void unavailableItemsReportsSoldOutAndShortStock() {
        Product product = banhMi();
        when(storeProductRepository.findByIdStoreIdAndIdProductIdIn(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of(row(2, true)));

        assertThat(inventoryService.unavailableItems(STORE_ID, Map.of(product, 3)))
                .containsExactly("Bánh mì thập cẩm");
        assertThat(inventoryService.unavailableItems(STORE_ID, Map.of(product, 2))).isEmpty();
    }

    @Test
    @DisplayName("unavailableItems: không có dòng store_products = đang bán, không quản tồn")
    void unavailableItemsTreatsMissingRowAsAvailable() {
        when(storeProductRepository.findByIdStoreIdAndIdProductIdIn(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of());

        assertThat(inventoryService.unavailableItems(STORE_ID, Map.of(banhMi(), 99))).isEmpty();
    }

    @Test
    @DisplayName("unavailableItems: món đã tắt khỏi thực đơn chuỗi luôn không bán được")
    void unavailableItemsRespectsChainMenu() {
        Product product = banhMi();
        product.setAvailable(false);
        when(storeProductRepository.findByIdStoreIdAndIdProductIdIn(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of());

        assertThat(inventoryService.unavailableItems(STORE_ID, Map.of(product, 1)))
                .containsExactly("Bánh mì thập cẩm");
    }

    // ------------------------------------------------------------ setAvailability

    @Test
    @DisplayName("setAvailability: chưa có dòng thì tạo mới với is_available theo yêu cầu")
    void setAvailabilityCreatesRowWhenMissing() {
        Store store = new Store();
        store.setId(STORE_ID);
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi()));
        when(storeProductRepository.findByIdStoreIdAndIdProductId(STORE_ID, PRODUCT_ID)).thenReturn(Optional.empty());
        when(entityManager.getReference(Store.class, STORE_ID)).thenReturn(store);
        when(storeProductRepository.save(any(StoreProduct.class))).thenAnswer(inv -> inv.getArgument(0));

        StoreStockResponse result = inventoryService.setAvailability(STORE_ID, PRODUCT_ID, false);

        assertThat(result.isAvailable()).isFalse();
        assertThat(result.getStockQuantity()).isNull();
    }

    // ---------------------------------------------------------------- adjustStock

    @Test
    @DisplayName("adjustStock: chặn số lượng thay đổi bằng 0")
    void adjustStockRejectsZero() {
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi()));

        assertThatThrownBy(() -> inventoryService.adjustStock(STORE_ID, PRODUCT_ID, change(0), 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("khác 0");
    }

    @Test
    @DisplayName("adjustStock: món chưa quản tồn thì không cho giảm (tránh tồn âm)")
    void adjustStockRejectsNegativeOnUntracked() {
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi()));
        when(storeProductRepository.findByIdStoreIdAndIdProductId(STORE_ID, PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.adjustStock(STORE_ID, PRODUCT_ID, change(-2), 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa quản tồn");

        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustStock: lần nhập đầu tiên đặt luôn con số tồn và ghi sổ IMPORT")
    void adjustStockStartsTrackingOnFirstImport() {
        Store store = new Store();
        store.setId(STORE_ID);
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi()));
        when(storeProductRepository.findByIdStoreIdAndIdProductId(STORE_ID, PRODUCT_ID)).thenReturn(Optional.empty());
        when(entityManager.getReference(Store.class, STORE_ID)).thenReturn(store);
        when(storeProductRepository.save(any(StoreProduct.class))).thenAnswer(inv -> inv.getArgument(0));

        StoreStockResponse result = inventoryService.adjustStock(STORE_ID, PRODUCT_ID, change(12), 5L);

        assertThat(result.getStockQuantity()).isEqualTo(12);
        ArgumentCaptor<StoreProduct> saved = ArgumentCaptor.forClass(StoreProduct.class);
        verify(storeProductRepository).save(saved.capture());
        assertThat(saved.getValue().getStockQuantity()).isEqualTo(12);

        InventoryMovement movement = captureSavedMovement();
        assertThat(movement.getReason()).isEqualTo(InventoryReason.IMPORT);
        assertThat(movement.getChangeQty()).isEqualTo(12);
        assertThat(movement.getStore().getId()).isEqualTo(STORE_ID);
    }

    @Test
    @DisplayName("adjustStock: nhập thêm dùng UPDATE nguyên tử rồi đọc lại entity để trả số mới")
    void adjustStockIncrementsAtomically() {
        StoreProduct row = row(5, true);
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi()));
        when(storeProductRepository.findByIdStoreIdAndIdProductId(STORE_ID, PRODUCT_ID)).thenReturn(Optional.of(row));

        inventoryService.adjustStock(STORE_ID, PRODUCT_ID, change(3), 5L);

        verify(storeProductRepository).incrementStockAtomic(STORE_ID, PRODUCT_ID, 3);
        // Bulk UPDATE không cập nhật entity đang managed -> phải refresh, nếu không response trả số cũ
        verify(entityManager).refresh(row);
    }

    @Test
    @DisplayName("adjustStock: giảm quá số đang có thì báo lỗi kèm tồn hiện tại")
    void adjustStockRejectsTooLargeDecrease() {
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi()));
        when(storeProductRepository.findByIdStoreIdAndIdProductId(STORE_ID, PRODUCT_ID))
                .thenReturn(Optional.of(row(2, true)));
        when(storeProductRepository.decrementStockAtomic(STORE_ID, PRODUCT_ID, 5)).thenReturn(0);

        assertThatThrownBy(() -> inventoryService.adjustStock(STORE_ID, PRODUCT_ID, change(-5), 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang có 2");

        verify(inventoryMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustStock: giảm hợp lệ ghi sổ ADJUST với số âm")
    void adjustStockDecrementsAndLogsAdjust() {
        StoreProduct row = row(10, true);
        when(productRepository.findByIdAndDeletedFalse(PRODUCT_ID)).thenReturn(Optional.of(banhMi()));
        when(storeProductRepository.findByIdStoreIdAndIdProductId(STORE_ID, PRODUCT_ID)).thenReturn(Optional.of(row));
        when(storeProductRepository.decrementStockAtomic(STORE_ID, PRODUCT_ID, 4)).thenReturn(1);

        inventoryService.adjustStock(STORE_ID, PRODUCT_ID, change(-4), 5L);

        verify(entityManager).refresh(row);
        InventoryMovement movement = captureSavedMovement();
        assertThat(movement.getReason()).isEqualTo(InventoryReason.ADJUST);
        assertThat(movement.getChangeQty()).isEqualTo(-4);
    }

    // --------------------------------------------------------------------- helpers

    private void stubTracked(StoreProduct... rows) {
        when(storeProductRepository.findByIdStoreIdAndIdProductIdIn(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of(rows));
    }

    private Product banhMi() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Bánh mì thập cẩm");
        product.setAvailable(true);
        return product;
    }

    private Order order(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        Store store = new Store();
        store.setId(STORE_ID);
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderCode("BMK-TEST-1");
        order.setStore(store);
        order.setItems(List.of(item));
        return order;
    }

    private StoreProduct row(Integer stock, boolean available) {
        StoreProduct sp = new StoreProduct();
        sp.setId(new StoreProductId(STORE_ID, PRODUCT_ID));
        sp.setProduct(banhMi());
        sp.setAvailable(available);
        sp.setStockQuantity(stock);
        return sp;
    }

    private StockChangeRequest change(int quantity) {
        StockChangeRequest request = new StockChangeRequest();
        request.setChangeQty(quantity);
        return request;
    }

    private InventoryMovement orderMovement(long storeId, Product product, int changeQty) {
        Store store = new Store();
        store.setId(storeId);
        InventoryMovement movement = new InventoryMovement();
        movement.setStore(store);
        movement.setProduct(product);
        movement.setChangeQty(changeQty);
        movement.setReason(InventoryReason.ORDER);
        return movement;
    }

    private InventoryMovement captureSavedMovement() {
        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(captor.capture());
        return captor.getValue();
    }
}
