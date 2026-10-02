package com.banhmyking.banhmyking.service.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.banhmyking.banhmyking.dto.catalog.StockChangeRequest;
import com.banhmyking.banhmyking.dto.catalog.StockMovementResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.store.StoreStockResponse;
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.InventoryMovement;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.StoreProduct;
import com.banhmyking.banhmyking.entity.StoreProductId;
import com.banhmyking.banhmyking.enums.InventoryReason;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.InventoryMovementRepository;
import com.banhmyking.banhmyking.repository.ProductRepository;
import com.banhmyking.banhmyking.repository.StoreProductRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.ComboExpander;
import com.banhmyking.banhmyking.service.InventoryService;
import com.banhmyking.banhmyking.util.PageableFactory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final ProductRepository productRepository;
    private final StoreProductRepository storeProductRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decreaseForOrder(Order order) {
        Product shortage = decreaseAllOrNothing(order);
        if (shortage != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Sản phẩm \"" + shortage.getName() + "\" không đủ tồn kho để xác nhận đơn");
        }
    }

    @Override
    public boolean tryDecreaseForOrder(Order order) {
        return decreaseAllOrNothing(order) == null;
    }

    /**
     * Trừ tồn theo NHU CẦU GỘP món lẻ (spec combo-sale §4.2): dòng combo × q → từng thành phần ×
     * (số lượng × q), cộng dồn với dòng món lẻ cùng món. Thiếu ở một món thì cộng trả các món đã trừ
     * và trả về món thiếu; đủ hết thì ghi sổ ORDER theo từng MÓN LẺ (để restoreForOrder hoàn đúng).
     */
    private Product decreaseAllOrNothing(Order order) {
        Long storeId = order.getStore().getId();
        Map<Product, Integer> lines = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null && item.getQuantity() != null) {
                lines.merge(item.getProduct(), item.getQuantity(), Integer::sum);
            }
        }
        Map<Product, Integer> demand = ComboExpander.expand(lines);
        Set<Long> tracked = trackedProductIds(storeId, demand.keySet());
        Map<Product, Integer> decreased = new LinkedHashMap<>();
        for (Map.Entry<Product, Integer> entry : demand.entrySet()) {
            Product product = entry.getKey();
            if (!tracked.contains(product.getId())) {
                continue;
            }
            // Số row = 0 nghĩa là không đủ hàng (hoặc giao dịch khác vừa lấy mất hàng).
            if (storeProductRepository.decrementStockAtomic(storeId, product.getId(), entry.getValue()) == 0) {
                decreased.forEach((done, quantity) ->
                        storeProductRepository.incrementStockAtomic(storeId, done.getId(), quantity));
                return product;
            }
            decreased.put(product, entry.getValue());
        }
        decreased.forEach((product, quantity) ->
                saveMovement(order.getStore(), product, -quantity, InventoryReason.ORDER, order, null, null));
        return null;
    }

    /** Món được quản tồn tại cơ sở = có dòng store_products với stock_quantity khác NULL. */
    private Set<Long> trackedProductIds(Long storeId, Collection<Product> products) {
        List<Long> productIds = products.stream()
                .map(Product::getId).filter(Objects::nonNull).distinct().toList();
        if (productIds.isEmpty()) {
            return Set.of();
        }
        return storeProductRepository.findByIdStoreIdAndIdProductIdIn(storeId, productIds).stream()
                .filter(sp -> sp.getStockQuantity() != null)
                .map(sp -> sp.getId().getProductId())
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreForOrder(Order order) {
        Long orderId = order.getId();
        if (orderId == null) {
            return;
        }
        // Hoàn về ĐÚNG cơ sở đã bị trừ — lấy store_id ghi trên sổ ORDER của đơn, không dùng order.store
        // (spec §3.6): nếu đơn từng đổi cơ sở sau khi trừ tồn, order.store đã là cơ sở mới.
        // Sổ ORDER ghi theo món lẻ (kể cả đơn có combo) nên hoàn theo sổ là hoàn đúng thành phần.
        Map<StoreProductId, Integer> quantityByKey = new LinkedHashMap<>();
        Map<StoreProductId, InventoryMovement> sampleByKey = new LinkedHashMap<>();
        for (InventoryMovement decrease : inventoryMovementRepository.findByOrderIdAndReason(orderId, InventoryReason.ORDER)) {
            if (decrease.getStore() == null || decrease.getProduct() == null || decrease.getChangeQty() == null) {
                continue;
            }
            StoreProductId key = new StoreProductId(decrease.getStore().getId(), decrease.getProduct().getId());
            quantityByKey.merge(key, Math.abs(decrease.getChangeQty()), Integer::sum);
            sampleByKey.putIfAbsent(key, decrease);
        }
        for (Map.Entry<StoreProductId, Integer> entry : quantityByKey.entrySet()) {
            StoreProductId key = entry.getKey();
            int quantity = entry.getValue();
            if (quantity <= 0 || inventoryMovementRepository.existsByOrderIdAndStoreIdAndProductIdAndReason(
                    orderId, key.getStoreId(), key.getProductId(), InventoryReason.RESTORE)) {
                continue;
            }
            InventoryMovement sample = sampleByKey.get(key);
            if (storeProductRepository.incrementStockAtomic(key.getStoreId(), key.getProductId(), quantity) > 0) {
                saveMovement(sample.getStore(), sample.getProduct(), quantity,
                        InventoryReason.RESTORE, order, null, null);
            }
        }
    }

    /**
     * Món lẻ: hết món / tắt / thiếu tồn so với nhu cầu gộp. Combo (spec §4.1): (1) chính combo tắt, xoá
     * hoặc bị báo hết tại cơ sở → tên combo; (2–3) thành phần không bán được hoặc thiếu tồn so với nhu
     * cầu gộp của cả giỏ → "Tên combo (hết A, B)".
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> unavailableItems(Long storeId, Map<Product, Integer> quantities) {
        if (quantities.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> demandById = new HashMap<>();
        ComboExpander.expand(quantities).forEach((product, quantity) -> {
            if (product.getId() != null) {
                demandById.merge(product.getId(), quantity, Integer::sum);
            }
        });
        Set<Long> ids = new LinkedHashSet<>(demandById.keySet());
        quantities.keySet().stream().map(Product::getId).filter(Objects::nonNull).forEach(ids::add);
        Map<Long, StoreProduct> rows = rowsOf(storeId, ids);

        List<String> names = new ArrayList<>();
        for (Map.Entry<Product, Integer> entry : quantities.entrySet()) {
            Product product = entry.getKey();
            StoreProduct row = rows.get(product.getId());
            if (!product.isCombo()) {
                int needed = product.getId() != null
                        ? demandById.getOrDefault(product.getId(), entry.getValue())
                        : entry.getValue();
                if (isBlocked(product, row, needed)) {
                    names.add(product.getName());
                }
                continue;
            }
            if (!product.isAvailable() || product.isDeleted() || (row != null && !row.isAvailable())) {
                names.add(product.getName());
                continue;
            }
            List<String> missing = product.getComboItems().stream()
                    .map(ComboItem::getComponent)
                    .filter(Objects::nonNull)
                    .filter(component -> isBlocked(component, rows.get(component.getId()),
                            demandById.getOrDefault(component.getId(), 0)))
                    .map(Product::getName)
                    .sorted()
                    .toList();
            if (!missing.isEmpty()) {
                names.add(product.getName() + " (hết " + String.join(", ", missing) + ")");
            }
        }
        names.sort(String::compareTo);
        return names;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoreStockResponse> listStoreStock(Long storeId) {
        Map<Long, StoreProduct> rows = new HashMap<>();
        for (StoreProduct row : storeProductRepository.findByIdStoreId(storeId)) {
            rows.put(row.getId().getProductId(), row);
        }
        return productRepository.findAll(Sort.by("name")).stream()
                .filter(product -> !product.isDeleted())
                .map(product -> toStockResponse(product, rows.get(product.getId()), blockedBy(product, rows)))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreStockResponse setAvailability(Long storeId, Long productId, boolean available) {
        Product product = requireProduct(productId);
        StoreProduct row = storeProductRepository.findByIdStoreIdAndIdProductId(storeId, productId)
                .orElseGet(() -> newRow(storeId, product));
        row.setAvailable(available);
        StoreProduct saved = storeProductRepository.save(row);
        return toStockResponse(product, saved, comboBlockedBy(storeId, product));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreStockResponse adjustStock(Long storeId, Long productId, StockChangeRequest request, Long actorId) {
        Product product = requireProduct(productId);
        if (product.isCombo()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Combo không có tồn kho riêng — hãy nhập tồn cho từng món trong combo");
        }
        int changeQty = request.getChangeQty();
        if (changeQty == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Số lượng thay đổi phải khác 0");
        }
        StoreProduct row = storeProductRepository.findByIdStoreIdAndIdProductId(storeId, productId).orElse(null);
        Integer current = row == null ? null : row.getStockQuantity();
        if (current == null) {
            // Chưa quản tồn: lần nhập đầu tiên đặt luôn con số ban đầu.
            if (changeQty < 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Món chưa quản tồn tại cơ sở nên không thể giảm; nhập số dương để bắt đầu quản.");
            }
            if (row == null) {
                row = newRow(storeId, product);
            }
            row.setStockQuantity(changeQty);
            row = storeProductRepository.save(row);
        } else if (changeQty > 0) {
            storeProductRepository.incrementStockAtomic(storeId, productId, changeQty);
            // Bulk UPDATE không đụng tới entity đang managed; không đọc lại thì response
            // trong cùng request (open-in-view) trả về số tồn cũ.
            entityManager.refresh(row);
        } else {
            if (storeProductRepository.decrementStockAtomic(storeId, productId, -changeQty) == 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Tồn kho không đủ để giảm " + (-changeQty) + " (đang có " + current + ")");
            }
            entityManager.refresh(row);
        }
        saveMovement(row.getStore(), product, changeQty,
                changeQty > 0 ? InventoryReason.IMPORT : InventoryReason.ADJUST, null, request.getNote(), actorId);
        return toStockResponse(product, row, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> getMovements(Long storeId, Long productId, int page, int size) {
        Pageable pageable = PageableFactory.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(inventoryMovementRepository
                .findPageByStoreIdAndProductId(storeId, productId, pageable)
                .map(this::toMovementResponse));
    }

    private Product requireProduct(Long productId) {
        return productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
    }

    private StoreProduct newRow(Long storeId, Product product) {
        StoreProduct row = new StoreProduct();
        row.setId(new StoreProductId(storeId, product.getId()));
        row.setStore(entityManager.getReference(Store.class, storeId));
        row.setProduct(product);
        return row;
    }

    /** HashMap (không dùng Map.of) để get(null) an toàn với Product chưa có id. */
    private Map<Long, StoreProduct> rowsOf(Long storeId, Collection<Long> productIds) {
        Map<Long, StoreProduct> rows = new HashMap<>();
        if (productIds.isEmpty()) {
            return rows;
        }
        for (StoreProduct row : storeProductRepository.findByIdStoreIdAndIdProductIdIn(storeId, productIds)) {
            rows.put(row.getId().getProductId(), row);
        }
        return rows;
    }

    /** Món lẻ không bán được tại cơ sở: tắt toàn chuỗi, đã xoá, bị báo hết, hoặc tồn < nhu cầu. */
    private static boolean isBlocked(Product product, StoreProduct row, int demand) {
        boolean soldOut = !product.isAvailable() || product.isDeleted() || (row != null && !row.isAvailable());
        boolean shortStock = row != null && row.getStockQuantity() != null && row.getStockQuantity() < demand;
        return soldOut || shortStock;
    }

    /** Thành phần đang làm MỘT combo không bán được tại cơ sở; món lẻ → rỗng. */
    private List<String> blockedBy(Product product, Map<Long, StoreProduct> rows) {
        if (!product.isCombo()) {
            return List.of();
        }
        return product.getComboItems().stream()
                .filter(item -> item.getComponent() != null)
                .filter(item -> isBlocked(item.getComponent(), rows.get(item.getComponent().getId()),
                        item.getQuantity()))
                .map(item -> item.getComponent().getName())
                .sorted()
                .toList();
    }

    private List<String> comboBlockedBy(Long storeId, Product product) {
        if (!product.isCombo()) {
            return List.of();
        }
        List<Long> componentIds = product.getComboItems().stream()
                .map(ComboItem::getComponent).filter(Objects::nonNull).map(Product::getId).toList();
        return blockedBy(product, rowsOf(storeId, componentIds));
    }

    private StoreStockResponse toStockResponse(Product product, StoreProduct row, List<String> blockedBy) {
        boolean combo = product.isCombo();
        return StoreStockResponse.builder()
                .productId(product.getId())
                .productName(product.getName())
                .categoryName(product.getCategory() == null ? null : product.getCategory().getName())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .productType(product.getProductType())
                .onChainMenu(product.isAvailable())
                .available(row == null || row.isAvailable())
                .stockQuantity(combo || row == null ? null : row.getStockQuantity())
                .lowStockThreshold(row == null ? 5 : row.getLowStockThreshold())
                .lowStock(!combo && row != null && row.isLowStock())
                .blockedBy(blockedBy)
                .build();
    }

    private void saveMovement(Store store, Product product, int changeQty, InventoryReason reason,
                              Order order, String note, Long actorId) {
        InventoryMovement movement = new InventoryMovement();
        movement.setStore(store);
        movement.setProduct(product);
        movement.setChangeQty(changeQty);
        movement.setReason(reason);
        movement.setOrder(order);
        movement.setNote(note);
        if (actorId != null) {
            movement.setCreatedBy(userRepository.getReferenceById(actorId));
        }
        inventoryMovementRepository.save(movement);
    }

    private StockMovementResponse toMovementResponse(InventoryMovement movement) {
        return StockMovementResponse.builder()
                .id(movement.getId())
                .changeQty(movement.getChangeQty())
                .reason(movement.getReason())
                .orderCode(movement.getOrder() == null ? null : movement.getOrder().getOrderCode())
                .note(movement.getNote())
                .createdAt(movement.getCreatedAt())
                .build();
    }
}
