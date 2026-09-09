package com.banhmyking.banhmyking.config;

import com.banhmyking.banhmyking.entity.Address;
import com.banhmyking.banhmyking.entity.Category;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.ProductOption;
import com.banhmyking.banhmyking.entity.Promotion;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.DiscountType;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.AddressRepository;
import com.banhmyking.banhmyking.repository.CategoryRepository;
import com.banhmyking.banhmyking.repository.ProductOptionRepository;
import com.banhmyking.banhmyking.repository.ProductRepository;
import com.banhmyking.banhmyking.repository.PromotionRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final AddressRepository addressRepository;
    private final PromotionRepository promotionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            log.info("Khởi tạo dữ liệu mẫu cho kiểm thử...");

            // 1. User demo — đủ 4 role, password BCrypt-encoded
            seedUser("customer@gmail.com", "12345678", "Khách Hàng Test", "0901234567", RoleName.CUSTOMER);
            seedUser("admin@gmail.com", "12345678", "Quản Trị Viên", "0900000001", RoleName.ADMIN);
            seedUser("staff@gmail.com", "12345678", "Nhân Viên Test", "0900000002", RoleName.STAFF);
            seedUser("shipper@gmail.com", "12345678", "Shipper Test", "0900000003", RoleName.SHIPPER);

            // 2. Category demo
            Category category = new Category();
            category.setName("Bánh Mì");
            category.setDescription("Bánh mì giòn rụm truyền thống");
            category.setSortOrder(1);
            categoryRepository.save(category);

            // 3. Product 1 (Khả dụng, isAvailable = true)
            Product p1 = new Product();
            p1.setCategory(category);
            p1.setName("Bánh mì Pate Chả Lụa");
            p1.setDescription("Bánh mì giòn kẹp pate gan béo ngậy và chả lụa hảo hạng");
            p1.setImageUrl("https://images.unsplash.com/photo-1626804475297-41608ea09aeb");
            p1.setPrice(BigDecimal.valueOf(30000));
            p1.setAvailable(true);
            p1.setDeleted(false);
            productRepository.save(p1);

            // Options cho Product 1
            ProductOption opt1 = new ProductOption();
            opt1.setProduct(p1);
            opt1.setName("Thêm pate");
            opt1.setExtraPrice(BigDecimal.valueOf(5000));

            ProductOption opt2 = new ProductOption();
            opt2.setProduct(p1);
            opt2.setName("Thêm chả lụa");
            opt2.setExtraPrice(BigDecimal.valueOf(8000));

            ProductOption opt3 = new ProductOption();
            opt3.setProduct(p1);
            opt3.setName("Thêm trứng ốp la");
            opt3.setExtraPrice(BigDecimal.valueOf(7000));

            productOptionRepository.saveAll(List.of(opt1, opt2, opt3));

            // 4. Product 2 (Không khả dụng, isAvailable = false để test AC 5)
            Product p2 = new Product();
            p2.setCategory(category);
            p2.setName("Bánh mì Chảo Đặc Biệt (Tạm ngưng phục vụ)");
            p2.setDescription("Bánh mì chảo nóng sốt (hiện đang tạm ngưng phục vụ)");
            p2.setImageUrl("https://images.unsplash.com/photo-1544025162-d76694265947");
            p2.setPrice(BigDecimal.valueOf(45000));
            p2.setAvailable(false);
            p2.setDeleted(false);
            productRepository.save(p2);

        }

        if (addressRepository.count() == 0) {
            // Gán chắc chắn cho customer demo — findAll().findFirst() không đảm bảo thứ tự
            userRepository.findByEmailAndDeletedFalse("customer@gmail.com").ifPresent(u -> {
                Address addr = new Address();
                addr.setUser(u);
                addr.setReceiverName("Khách Hàng Test");
                addr.setReceiverPhone("0901234567");
                addr.setFullAddress("123 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh");
                addr.setDefaultAddress(true);
                addressRepository.save(addr);
                log.info("Khởi tạo địa chỉ mẫu ID: {} cho user ID: {}", addr.getId(), u.getId());
            });
        }

        if (promotionRepository.count() == 0) {
            Promotion promo1 = new Promotion();
            promo1.setCode("BANHMYKING10");
            promo1.setDescription("Giảm 10% tối đa 20.000đ cho đơn từ 50.000đ");
            promo1.setDiscountType(DiscountType.PERCENTAGE);
            promo1.setValue(BigDecimal.valueOf(10));
            promo1.setMaxDiscountAmount(BigDecimal.valueOf(20000));
            promo1.setMinOrderAmount(BigDecimal.valueOf(50000));
            promo1.setStartsAt(java.time.LocalDateTime.now().minusDays(1));
            promo1.setEndsAt(java.time.LocalDateTime.now().plusMonths(1));
            promo1.setMaxUsage(100);
            promo1.setActive(true);
            promotionRepository.save(promo1);

            Promotion promo2 = new Promotion();
            promo2.setCode("GIAM10K");
            promo2.setDescription("Giảm ngay 10.000đ cho đơn từ 30.000đ");
            promo2.setDiscountType(DiscountType.FIXED_AMOUNT);
            promo2.setValue(BigDecimal.valueOf(10000));
            promo2.setMinOrderAmount(BigDecimal.valueOf(30000));
            promo2.setStartsAt(java.time.LocalDateTime.now().minusDays(1));
            promo2.setEndsAt(java.time.LocalDateTime.now().plusMonths(1));
            promo2.setMaxUsage(50);
            promo2.setActive(true);
            promotionRepository.save(promo2);

            log.info("Khởi tạo khuyến mãi mẫu: BANHMYKING10, GIAM10K");
        }
    }

    /** Seed 1 user demo — encode BCrypt */
    private void seedUser(String email, String rawPassword, String fullName, String phone, RoleName role) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setFullName(fullName);
        u.setPhone(phone);
        u.setRole(role);
        userRepository.save(u);
        log.info("Seed user: {} (role {})", email, role);
    }
}
