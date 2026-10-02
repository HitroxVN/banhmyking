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
     * Đơn của chính khách (user_id = :userId), tùy chọn lọc theo một hay nhiều trạng thái.
     *
     * <p>Nhận danh sách chứ không phải một trạng thái vì bộ lọc ở FE gộp nhóm:
     * "Đang chuẩn bị" = CONFIRMED/PREPARING/READY_FOR_PICKUP, "Đã huỷ" = CANCELLED/FAILED.
     * Gửi một trạng thái duy nhất vẫn chạy y hệt — danh sách chỉ có 1 phần tử.
     */
    public static Specification<Order> ownedBy(Long userId, List<OrderStatus> statuses) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Lọc đơn theo status/khoảng thời gian, KHÔNG giới hạn cơ sở — chỉ dùng cho ngữ cảnh toàn chuỗi
     * (ADMIN); luồng STAFF/MANAGER phải dùng bản có storeId bên dưới.
     * ?status&fromDate&toDate
     */
    public static Specification<Order> withFilters(OrderStatus status, LocalDateTime fromDate, LocalDateTime toDate) {
        return withFilters(status, fromDate, toDate, null);
    }

    /** Staff/Manager/Admin lọc đơn; storeId null = mọi cơ sở (chỉ ADMIN được truyền null — xem StoreAccessGuard). */
    public static Specification<Order> withFilters(OrderStatus status, LocalDateTime fromDate, LocalDateTime toDate,
                                                   Long storeId) {
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
            if (storeId != null) {
                predicates.add(cb.equal(root.get("store").get("id"), storeId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
