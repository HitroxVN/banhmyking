package com.banhmyking.banhmyking.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.banhmyking.banhmyking.dto.catalog.StockChangeRequest;
import com.banhmyking.banhmyking.dto.catalog.StockMovementResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.entity.InventoryMovement;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.enums.InventoryReason;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.InventoryMovementRepository;
import com.banhmyking.banhmyking.repository.ProductRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.InventoryService;
import com.banhmyking.banhmyking.util.PageableFactory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final ProductRepository productRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Override
    public void assertEnough(Product product, int quantity) {
        Integer stock = product.getStockQuantity();
        if (stock == null || stock >= quantity) {
            return;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, stock <= 0
                ? "Sản phẩm \"" + product.getName() + "\" đã hết hàng"
                : "Sản phẩm \"" + product.getName() + "\" chỉ còn " + stock);
    }

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
     * Trừ tồn cho mọi dòng của đơn. Thiếu hàng ở một dòng thì cộng trả các dòng đã trừ trước đó
     * và trả về sản phẩm thiếu; đủ hết thì ghi movement và trả null.
     */
    private Product decreaseAllOrNothing(Order order) {
        List<OrderItem> decreased = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product == null || product.getStockQuantity() == null) {
                continue;
            }
            // Số row = 0 nghĩa là không đủ hàng (hoặc giao dịch khác vừa lấy mất hàng).
            if (productRepository.decrementStockAtomic(product.getId(), item.getQuantity()) == 0) {
                for (OrderItem done : decreased) {
                    productRepository.incrementStockAtomic(done.getProduct().getId(), done.getQuantity());
                }
                return product;
            }
            decreased.add(item);
        }
        for (OrderItem item : decreased) {
            saveMovement(item.getProduct(), -item.getQuantity(), InventoryReason.ORDER, order, null, null);
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreForOrder(Order order) {
        Long orderId = order.getId();
        if (orderId == null) {
            return;
        }
        // Gộp theo sản phẩm: cùng một món có thể nằm ở nhiều dòng (khác topping). Nếu hoàn từng dòng,
        // dòng đầu ghi RESTORE xong thì dòng sau bị check "đã hoàn" bỏ qua → mất tồn kho.
        Map<Long, Integer> quantityByProduct = new LinkedHashMap<>();
        Map<Long, Product> productById = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product == null) {
                continue;
            }
            quantityByProduct.merge(product.getId(), item.getQuantity(), Integer::sum);
            productById.putIfAbsent(product.getId(), product);
        }
        for (Map.Entry<Long, Integer> entry : quantityByProduct.entrySet()) {
            Long productId = entry.getKey();
            if (!inventoryMovementRepository.existsByOrderIdAndProductIdAndReason(
                    orderId, productId, InventoryReason.ORDER)
                    || inventoryMovementRepository.existsByOrderIdAndProductIdAndReason(
                            orderId, productId, InventoryReason.RESTORE)) {
                continue;
            }
            int quantity = entry.getValue();
            if (productRepository.incrementStockAtomic(productId, quantity) > 0) {
                saveMovement(productById.get(productId), quantity, InventoryReason.RESTORE, order, null, null);
            }
        }
    }

    @Override
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public int adjustStock(Long productId, StockChangeRequest request, Long actorId) {
        Product product = productRepository.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
        int changeQty = request.getChangeQty();
        if (changeQty == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Số lượng thay đổi phải khác 0");
        }

        Integer current = product.getStockQuantity();
        if (current == null) {
            // Chưa quản tồn: lần nhập đầu tiên đặt luôn con số ban đầu.
            if (changeQty < 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Sản phẩm chưa quản tồn nên không thể giảm; nhập số dương để bắt đầu quản.");
            }
            product.setStockQuantity(changeQty);
            productRepository.save(product);
        } else if (changeQty > 0) {
            productRepository.incrementStockAtomic(productId, changeQty);
            // Bulk UPDATE không đụng tới entity đang managed; không đọc lại thì response
            // trong cùng request (open-in-view) trả về số tồn cũ.
            entityManager.refresh(product);
        } else {
            if (productRepository.decrementStockAtomic(productId, -changeQty) == 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Tồn kho không đủ để giảm " + (-changeQty) + " (đang có " + current + ")");
            }
            entityManager.refresh(product);
        }

        saveMovement(product, changeQty,
                changeQty > 0 ? InventoryReason.IMPORT : InventoryReason.ADJUST,
                null, request.getNote(), actorId);
        return current == null ? changeQty : current + changeQty;
    }

    @Override
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public PageResponse<StockMovementResponse> getMovements(Long productId, int page, int size) {
        Pageable pageable = PageableFactory.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StockMovementResponse> movements = inventoryMovementRepository
                .findPageByProductId(productId, pageable)
                .map(this::toMovementResponse);
        return PageResponse.from(movements);
    }

    private void saveMovement(Product product, int changeQty, InventoryReason reason,
                              Order order, String note, Long actorId) {
        InventoryMovement movement = new InventoryMovement();
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
