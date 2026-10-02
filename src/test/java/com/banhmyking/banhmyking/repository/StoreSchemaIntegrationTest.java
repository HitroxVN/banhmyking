package com.banhmyking.banhmyking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.StoreProduct;
import com.banhmyking.banhmyking.entity.StoreProductId;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Chạy trên DB dev thật (giống các test repository khác) — mọi thay đổi rollback sau test. */
@SpringBootTest
@Transactional
class StoreSchemaIntegrationTest {

    @Autowired private StoreRepository storeRepository;
    @Autowired private StoreProductRepository storeProductRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesStoreOneAndBackfillsExistingOrders() {
        // V11 tạo CS01, nhưng admin được đổi mã/xoá nó trên DB dev → chỉ đòi bảng stores có dữ liệu.
        assertThat(storeRepository.count()).isPositive();
        Integer ordersWithoutStore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE store_id IS NULL", Integer.class);
        assertThat(ordersWithoutStore).isZero();
        Integer staffWithoutStore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role IN ('STAFF','SHIPPER') AND store_id IS NULL", Integer.class);
        assertThat(staffWithoutStore).isZero();
    }

    @Test
    void savesStoreAndPerStoreProductRow() {
        Store store = new Store();
        store.setCode("CSTEST");
        store.setName("Cơ sở test");
        store.setAddress("1 Đường Test, Hà Nội");
        store.setLatitude(new BigDecimal("21.028700"));
        store.setLongitude(new BigDecimal("105.852400"));
        store.setOpenTime(LocalTime.of(6, 30));
        store.setCloseTime(LocalTime.of(22, 0));
        store = storeRepository.save(store);

        Product product = productRepository.findAll().get(0);
        StoreProduct row = new StoreProduct();
        row.setId(new StoreProductId(store.getId(), product.getId()));
        row.setStore(store);
        row.setProduct(product);
        row.setAvailable(false);
        row.setStockQuantity(7);
        storeProductRepository.saveAndFlush(row);

        List<StoreProduct> rows = storeProductRepository.findByIdStoreId(store.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).isAvailable()).isFalse();
        assertThat(rows.get(0).getStockQuantity()).isEqualTo(7);
        assertThat(store.hasLocation()).isTrue();
        assertThat(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc())
                .extracting(Store::getCode).contains("CSTEST");
    }
}
