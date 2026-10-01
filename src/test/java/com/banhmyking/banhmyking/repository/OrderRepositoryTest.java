package com.banhmyking.banhmyking.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.specification.OrderSpecifications;
import com.banhmyking.banhmyking.util.PageableFactory;

import jakarta.persistence.EntityManager;

/**
 * Kiểm {@link OrderSpecifications#ownedBy} trên MySQL thật. Mock không kiểm được spec —
 * mà đây là ranh giới cách ly dữ liệu: sai join ở đây là khách thấy đơn của người khác.
 */
@SpringBootTest
@Transactional
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private Long customerA;
    private Long customerB;

    @BeforeEach
    void seedTwoCustomers() {
        customerA = persistCustomer("ordertest-a@test.com");
        customerB = persistCustomer("ordertest-b@test.com");
    }

    @Test
    @DisplayName("ownedBy: chỉ trả đơn của chính khách, không lẫn đơn của khách khác")
    void ownedBy_doesNotLeakOtherCustomersOrders() {
        persistOrder(customerA, "BMK-ORDTEST-A", OrderStatus.PENDING);
        persistOrder(customerB, "BMK-ORDTEST-B", OrderStatus.PENDING);

        Page<Order> page = query(customerA, null);

        assertThat(page.getContent()).extracting(Order::getOrderCode).containsExactly("BMK-ORDTEST-A");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("ownedBy: lọc đúng một trạng thái")
    void ownedBy_filtersBySingleStatus() {
        persistOrder(customerA, "BMK-ORDTEST-PENDING", OrderStatus.PENDING);
        persistOrder(customerA, "BMK-ORDTEST-DONE", OrderStatus.DELIVERED);

        Page<Order> page = query(customerA, List.of(OrderStatus.PENDING));

        assertThat(page.getContent()).extracting(Order::getOrderCode).containsExactly("BMK-ORDTEST-PENDING");
    }

    @Test
    @DisplayName("ownedBy: lọc được cả nhóm trạng thái — nhóm \"Đang chuẩn bị\" ở FE")
    void ownedBy_supportsStatusGroup() {
        persistOrder(customerA, "BMK-ORDTEST-CONFIRMED", OrderStatus.CONFIRMED);
        persistOrder(customerA, "BMK-ORDTEST-PREPARING", OrderStatus.PREPARING);
        persistOrder(customerA, "BMK-ORDTEST-READY", OrderStatus.READY_FOR_PICKUP);
        persistOrder(customerA, "BMK-ORDTEST-PENDING", OrderStatus.PENDING);
        persistOrder(customerA, "BMK-ORDTEST-DELIVERED", OrderStatus.DELIVERED);

        Page<Order> page = query(customerA,
                List.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP));

        assertThat(page.getContent()).extracting(Order::getOrderCode)
                .containsExactlyInAnyOrder("BMK-ORDTEST-CONFIRMED", "BMK-ORDTEST-PREPARING", "BMK-ORDTEST-READY");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("ownedBy: danh sách rỗng = không lọc, trả hết đơn của khách")
    void ownedBy_emptyStatusList_returnsEveryStatusOfThatCustomer() {
        persistOrder(customerA, "BMK-ORDTEST-PENDING", OrderStatus.PENDING);
        persistOrder(customerA, "BMK-ORDTEST-CANCELLED", OrderStatus.CANCELLED);
        persistOrder(customerB, "BMK-ORDTEST-OTHER", OrderStatus.PENDING);

        assertThat(query(customerA, List.of()).getTotalElements()).isEqualTo(2);
        assertThat(query(customerA, null).getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("ownedBy: đơn mới nhất lên đầu — sắp xếp tường minh qua Pageable")
    void ownedBy_sortsNewestFirst() {
        Long olderId = persistOrder(customerA, "BMK-ORDTEST-OLD", OrderStatus.PENDING);
        persistOrder(customerA, "BMK-ORDTEST-NEW", OrderStatus.PENDING);

        // Lùi created_at của đơn cũ đúng 1 ngày: hai lần persist trong cùng một test có thể
        // rơi vào cùng một micro-giây (auditing set @CreatedDate lúc insert) nên không thể
        // dựa vào thứ tự insert để khẳng định thứ tự sắp xếp.
        entityManager.createNativeQuery("UPDATE orders SET created_at = :ts WHERE id = :id")
                .setParameter("ts", LocalDateTime.now().minusDays(1))
                .setParameter("id", olderId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        Page<Order> page = query(customerA, null);

        assertThat(page.getContent()).extracting(Order::getOrderCode)
                .containsExactly("BMK-ORDTEST-NEW", "BMK-ORDTEST-OLD");
    }

    /** Dùng đúng Pageable và sort mà OrderServiceImpl dùng, để test không lệch production. */
    private Page<Order> query(Long userId, List<OrderStatus> statuses) {
        return orderRepository.findAll(
                OrderSpecifications.ownedBy(userId, statuses),
                PageableFactory.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    private Long persistCustomer(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("password123");
        user.setFullName("Khách Test");
        user.setRole(RoleName.CUSTOMER);
        entityManager.persist(user);
        entityManager.flush();
        return user.getId();
    }

    private Long persistOrder(Long userId, String orderCode, OrderStatus status) {
        User user = userRepository.getReferenceById(userId);
        Order order = new Order();
        order.setOrderCode(orderCode);
        order.setUser(user);
        // Đơn luôn có cơ sở (V13): dùng cơ sở CS01 do migration V11 tạo sẵn
        order.setStore(entityManager.createQuery("select s from Store s order by s.id",
                com.banhmyking.banhmyking.entity.Store.class).setMaxResults(1).getSingleResult());
        order.setStatus(status);
        order.setReceiverName("Khách Test");
        order.setReceiverPhone("0900000009");
        order.setShippingAddress("1 Đường Test");
        order.setSubtotal(BigDecimal.valueOf(50000));
        order.setShippingFee(BigDecimal.valueOf(15000));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.valueOf(65000));
        entityManager.persist(order);
        entityManager.flush();
        return order.getId();
    }
}
