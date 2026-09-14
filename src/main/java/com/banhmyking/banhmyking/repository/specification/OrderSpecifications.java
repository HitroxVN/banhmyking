package com.banhmyking.banhmyking.repository.specification;

import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.enums.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderSpecifications {

    private OrderSpecifications() {}

    /**
     * Đơn được gán cho shipper (shipper_id = :shipperId), tùy chọn lọc theo trạng thái.
     */
    public static Specification<Order> assignedTo(Long shipperId, OrderStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("shipper").get("id"), shipperId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Specification cho Staff/Admin lọc đơn hàng toàn hệ thống.
     * ?status&fromDate&toDate
     */
    public static Specification<Order> withFilters(OrderStatus status, LocalDateTime fromDate, LocalDateTime toDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
