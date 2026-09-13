package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.user.UpdateProfileRequest;
import com.banhmyking.banhmyking.dto.user.UpdateRoleRequest;
import com.banhmyking.banhmyking.dto.user.UpdateStatusRequest;
import com.banhmyking.banhmyking.dto.user.UserDetailResponse;
import com.banhmyking.banhmyking.entity.RefreshToken;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.repository.RefreshTokenRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @InjectMocks UserServiceImpl userService;

    private static final Long ACTOR_ID = 1L;   // admin thao tác
    private static final Long TARGET_ID = 2L;  // user bị tác động

    private User buildUser(Long id, RoleName role, boolean banned) {
        User u = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", id);
        u.setEmail("user" + id + "@gmail.com");
        u.setFullName("User " + id);
        u.setPhone("0900" + id);
        u.setRole(role);
        u.setBanned(banned);
        return u;
    }

    // ─── changeRole ──────────────────────────────────────────────────────────

    @Test
    void changeRole_success_andRevokesTokens() {
        User target = buildUser(TARGET_ID, RoleName.CUSTOMER, false);
        when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
        RefreshToken rt = new RefreshToken();
        when(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(TARGET_ID)).thenReturn(List.of(rt));

        UserDetailResponse res = userService.changeRole(ACTOR_ID, TARGET_ID, new UpdateRoleRequest(RoleName.STAFF));

        assertThat(res.role()).isEqualTo(RoleName.STAFF);
        assertThat(rt.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(rt);
    }

    @Test
    void changeRole_selfRejected() {
        assertThatThrownBy(() -> userService.changeRole(ACTOR_ID, ACTOR_ID, new UpdateRoleRequest(RoleName.STAFF)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không thể thay đổi vai trò của chính mình");
    }

    @Test
    void changeRole_lastActiveAdminRejected() {
        User target = buildUser(TARGET_ID, RoleName.ADMIN, false);
        when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndDeletedFalse(RoleName.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.changeRole(ACTOR_ID, TARGET_ID, new UpdateRoleRequest(RoleName.STAFF)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không thể thay đổi vai trò của ADMIN cuối cùng");
    }

    @Test
    void changeRole_bannedAdminCanBeDemoted_whenAnotherAdminExists() {
        User target = buildUser(TARGET_ID, RoleName.ADMIN, true); // admin đã khoá
        when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndDeletedFalse(RoleName.ADMIN)).thenReturn(2L); // còn admin khác chưa xoá
        when(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(TARGET_ID)).thenReturn(List.of());

        UserDetailResponse res = userService.changeRole(ACTOR_ID, TARGET_ID, new UpdateRoleRequest(RoleName.CUSTOMER));

        assertThat(res.role()).isEqualTo(RoleName.CUSTOMER);
    }

    @Test
    void changeRole_lastBannedAdminCannotBeDemoted() {
        User target = buildUser(TARGET_ID, RoleName.ADMIN, true);
        when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndDeletedFalse(RoleName.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.changeRole(ACTOR_ID, TARGET_ID, new UpdateRoleRequest(RoleName.CUSTOMER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ADMIN cuối cùng");
    }

    @Test
    void changeRole_notFound() {
        when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changeRole(ACTOR_ID, TARGET_ID, new UpdateRoleRequest(RoleName.STAFF)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ─── changeStatus ────────────────────────────────────────────────────────

    @Test
    void changeStatus_banRevokesTokens() {
        User target = buildUser(TARGET_ID, RoleName.CUSTOMER, false);
        when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
        RefreshToken rt = new RefreshToken();
        when(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(TARGET_ID)).thenReturn(List.of(rt));

        UserDetailResponse res = userService.changeStatus(ACTOR_ID, TARGET_ID, new UpdateStatusRequest(true));

        assertThat(res.banned()).isTrue();
        assertThat(rt.getRevokedAt()).isNotNull();
    }

    @Test
    void changeStatus_unbanDoesNotRevoke() {
        User target = buildUser(TARGET_ID, RoleName.CUSTOMER, true);
        when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));

        UserDetailResponse res = userService.changeStatus(ACTOR_ID, TARGET_ID, new UpdateStatusRequest(false));

        assertThat(res.banned()).isFalse();
        verify(refreshTokenRepository, never()).findByUserIdAndRevokedAtIsNull(any());
    }

    @Test
    void changeStatus_selfRejected() {
        assertThatThrownBy(() -> userService.changeStatus(ACTOR_ID, ACTOR_ID, new UpdateStatusRequest(true)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không thể khoá/mở tài khoản của chính mình");
    }

    @Test
    void changeStatus_lastAdminBanRejected() {
        User target = buildUser(TARGET_ID, RoleName.ADMIN, false);
        when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndDeletedFalseAndBannedFalse(RoleName.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.changeStatus(ACTOR_ID, TARGET_ID, new UpdateStatusRequest(true)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không thể khoá ADMIN cuối cùng");
    }

    // ─── deleteUser ──────────────────────────────────────────────────────────

    @Test
    void deleteUser_success_softDeletesAndWipesTokens() {
        User target = buildUser(TARGET_ID, RoleName.CUSTOMER, false);
        when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));

        userService.deleteUser(ACTOR_ID, TARGET_ID);

        assertThat(target.isDeleted()).isTrue();
        verify(userRepository).save(target);
        verify(refreshTokenRepository).deleteByUserId(TARGET_ID);
    }

    @Test
    void deleteUser_selfRejected() {
        assertThatThrownBy(() -> userService.deleteUser(ACTOR_ID, ACTOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không thể xoá tài khoản của chính mình");
    }

    @Test
    void deleteUser_lastAdminRejected() {
        User target = buildUser(TARGET_ID, RoleName.ADMIN, false);
        when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndDeletedFalse(RoleName.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.deleteUser(ACTOR_ID, TARGET_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không thể xoá ADMIN cuối cùng");
    }

    @Test
    void deleteUser_bannedLastAdminRejected() {
        User target = buildUser(TARGET_ID, RoleName.ADMIN, true);
        when(userRepository.findByIdAndDeletedFalse(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleAndDeletedFalse(RoleName.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.deleteUser(ACTOR_ID, TARGET_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Không thể xoá ADMIN cuối cùng");
    }

    // ─── getMe / updateProfile ────────────────────────────────────────────────

    @Test
    void getMe_mapsAllFields() {
        User u = buildUser(1L, RoleName.CUSTOMER, false);
        u.setImage("https://cdn.banhmyking.vn/a/1.png");
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));

        UserDetailResponse res = userService.getMe(1L);

        assertThat(res.id()).isEqualTo(1L);
        assertThat(res.email()).isEqualTo("user1@gmail.com");
        assertThat(res.image()).isEqualTo("https://cdn.banhmyking.vn/a/1.png");
        assertThat(res.role()).isEqualTo(RoleName.CUSTOMER);
        assertThat(res.banned()).isFalse();
    }

    @Test
    void updateProfile_changesFieldsButNotEmail() {
        User u = buildUser(1L, RoleName.CUSTOMER, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);

        UserDetailResponse res = userService.updateProfile(1L,
                new UpdateProfileRequest("Tên Mới", "0912345678", "https://x.com/avatar.png"));

        assertThat(res.fullName()).isEqualTo("Tên Mới");
        assertThat(res.phone()).isEqualTo("0912345678");
        assertThat(res.image()).isEqualTo("https://x.com/avatar.png");
        assertThat(res.email()).isEqualTo("user1@gmail.com"); // email bất biến
    }

    @Test
    void updateProfile_bannedUserNotFound() {
        User u = buildUser(1L, RoleName.CUSTOMER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> userService.updateProfile(1L,
                new UpdateProfileRequest("Tên Mới", "0912345678", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ─── getUsers ─────────────────────────────────────────────────────────────

    @Test
    void getUsers_mapsPageResponse() {
        User u1 = buildUser(1L, RoleName.ADMIN, false);
        User u2 = buildUser(2L, RoleName.CUSTOMER, false);
        when(userRepository.searchUsers(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(u1, u2)));

        var res = userService.getUsers(null, null, null, 0, 10);

        assertThat(res.content()).hasSize(2);
        assertThat(res.totalElements()).isEqualTo(2);
        assertThat(res.page()).isZero();
        verify(userRepository).searchUsers(eq(null), eq(null), eq(null), any(Pageable.class));
    }
}
