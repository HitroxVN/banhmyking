package com.banhmyking.banhmyking.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.AddressRepository;
import com.banhmyking.banhmyking.repository.CategoryRepository;
import com.banhmyking.banhmyking.repository.ProductOptionRepository;
import com.banhmyking.banhmyking.repository.ProductRepository;
import com.banhmyking.banhmyking.repository.PromotionRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** I2: seed dev phải idempotent và không làm hỏng khởi động khi ADMIN đã đổi/xoá cơ sở. */
@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock private UserRepository userRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductOptionRepository productOptionRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private DataInitializer initializer;

    @BeforeEach
    void setUp() {
        // catalog / địa chỉ / khuyến mãi đã có → chỉ kiểm tra phần cơ sở + user
        lenient().when(productRepository.count()).thenReturn(1L);
        lenient().when(addressRepository.count()).thenReturn(1L);
        lenient().when(promotionRepository.count()).thenReturn(1L);
        lenient().when(userRepository.existsByEmail(anyString())).thenReturn(false);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(storeRepository.findByCode(anyString())).thenReturn(Optional.empty());
        lenient().when(storeRepository.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Store store(long id, String code, boolean deleted) {
        Store s = new Store();
        s.setId(id);
        s.setCode(code);
        s.setDeleted(deleted);
        s.setActive(!deleted);
        return s;
    }

    private Map<String, User> seededUsers() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues().stream().collect(Collectors.toMap(User::getEmail, u -> u));
    }

    @Test
    void startsWhenCs01RenamedAndDemoStoreSoftDeleted() {
        Store renamed = store(1L, "HN01", false);
        Store cs02Deleted = store(2L, "CS02", true);
        Store cs03 = store(3L, "CS03", false);
        when(storeRepository.findByCode("CS02")).thenReturn(Optional.of(cs02Deleted));
        when(storeRepository.findByCode("CS03")).thenReturn(Optional.of(cs03));
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(cs03, renamed));

        assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();

        // không tạo lại CS02 đã xoá (sẽ vi phạm uk_stores_code), không tạo CS03 đã có
        verify(storeRepository, never()).save(any(Store.class));
        Map<String, User> users = seededUsers();
        assertThat(users).doesNotContainKeys("staff2@gmail.com", "shipper2@gmail.com");
        // user gắn CS01 rơi về cơ sở hoạt động đầu tiên
        assertThat(users.get("staff@gmail.com").getStore()).isSameAs(cs03);
        assertThat(users.get("manager@gmail.com").getStore()).isSameAs(cs03);
        assertThat(users.get("admin@gmail.com").getStore()).isNull();
    }

    @Test
    void skipsStoreBoundUsersWhenNoActiveStoreAndDemoCodesAllDeleted() {
        when(storeRepository.findByCode("CS01")).thenReturn(Optional.of(store(1L, "CS01", true)));
        when(storeRepository.findByCode("CS02")).thenReturn(Optional.of(store(2L, "CS02", true)));
        when(storeRepository.findByCode("CS03")).thenReturn(Optional.of(store(3L, "CS03", true)));
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of());

        assertThatCode(() -> initializer.run(null)).doesNotThrowAnyException();

        verify(storeRepository, never()).save(any(Store.class));
        Map<String, User> users = seededUsers();
        assertThat(users.values()).allMatch(u -> u.getRole() == RoleName.CUSTOMER || u.getRole() == RoleName.ADMIN);
        assertThat(users).containsKeys("customer@gmail.com", "admin@gmail.com");
    }

    @Test
    void seedsMissingDemoStoresOnceAndBindsUsers() {
        Store cs01 = store(1L, "CS01", false);
        when(storeRepository.findByCode("CS01")).thenReturn(Optional.of(cs01));

        initializer.run(null);

        ArgumentCaptor<Store> created = ArgumentCaptor.forClass(Store.class);
        verify(storeRepository, org.mockito.Mockito.times(2)).save(created.capture());
        assertThat(created.getAllValues()).extracting(Store::getCode).containsExactly("CS02", "CS03");
        Map<String, User> users = seededUsers();
        assertThat(users.get("staff@gmail.com").getStore()).isSameAs(cs01);
        assertThat(users.get("staff2@gmail.com").getStore().getCode()).isEqualTo("CS02");
    }

    @Test
    void isIdempotentWhenEverythingExists() {
        when(storeRepository.findByCode("CS01")).thenReturn(Optional.of(store(1L, "CS01", false)));
        when(storeRepository.findByCode("CS02")).thenReturn(Optional.of(store(2L, "CS02", false)));
        when(storeRepository.findByCode("CS03")).thenReturn(Optional.of(store(3L, "CS03", false)));
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        initializer.run(null);

        verify(storeRepository, never()).save(any(Store.class));
        verify(userRepository, never()).save(any(User.class));
    }
}
