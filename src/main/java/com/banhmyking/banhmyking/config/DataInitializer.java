package com.banhmyking.banhmyking.config;

import com.banhmyking.banhmyking.entity.Category;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.ProductOption;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.CategoryRepository;
import com.banhmyking.banhmyking.repository.ProductOptionRepository;
import com.banhmyking.banhmyking.repository.ProductRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            log.info("Khởi tạo dữ liệu mẫu cho kiểm thử...");

            // 1. User demo
            User user = new User();
            user.setEmail("customer@banhmyking.vn");
            user.setPassword("123456");
            user.setFullName("Khách Hàng Test");
            user.setPhone("0901234567");
            user.setRole(RoleName.CUSTOMER);
            userRepository.save(user);

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

            log.info("Dữ liệu mẫu khởi tạo hoàn tất! User ID: {}, Product ID 1 (Có sẵn): {}, Product ID 2 (Hết hàng): {}",
                    user.getId(), p1.getId(), p2.getId());
        }
    }
}
