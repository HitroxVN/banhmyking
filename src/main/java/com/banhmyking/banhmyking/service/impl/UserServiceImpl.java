package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.user.UpdateProfileRequest;
import com.banhmyking.banhmyking.dto.user.UpdateRoleRequest;
import com.banhmyking.banhmyking.dto.user.UpdateStatusRequest;
import com.banhmyking.banhmyking.dto.user.UserDetailResponse;
import com.banhmyking.banhmyking.entity.RefreshToken;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.repository.RefreshTokenRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.FileStorageService;
import com.banhmyking.banhmyking.service.UserService;
import com.banhmyking.banhmyking.util.PageableFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final StoreRepository storeRepository;

    private static final java.util.Set<RoleName> STORE_ROLES =
            java.util.EnumSet.of(RoleName.STAFF, RoleName.SHIPPER, RoleName.MANAGER);

    // ─── Self-service ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserDetailResponse getMe(Long userId) {
        return toDetail(requireActiveUser(userId));
    }

    @Override
    @Transactional
    public UserDetailResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requireActiveUser(userId);

        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        // Chỉ đổi ảnh khi client THỰC SỰ gửi field này. Form sửa tên/SĐT không gửi imageUrl
        // → gán thẳng sẽ vô tình xoá avatar mỗi lần lưu. Xoá ảnh có endpoint riêng.
        if (request.imageUrl() != null) {
            // Ảnh trong /uploads/ chỉ được gán qua endpoint upload. Nếu cho gán tay, user có thể trỏ
            // avatar vào file của người khác rồi gọi DELETE /users/me/avatar để xoá file đó.
            if (request.imageUrl().startsWith("/uploads/")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Ảnh đại diện phải được tải lên qua chức năng upload");
            }
            user.setImage(request.imageUrl());
        }
        return toDetail(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserDetailResponse uploadAvatar(Long userId, MultipartFile file) {
        User user = requireActiveUser(userId);

        String oldImage = user.getImage();
        user.setImage(fileStorageService.storeImage(file, FileStorageService.AVATAR_DIR));
        UserDetailResponse response = toDetail(userRepository.save(user));

        // Ghi DB xong mới xoá file cũ — lỗi xoá không làm hỏng ảnh vừa lưu.
        fileStorageService.deleteImage(oldImage, FileStorageService.AVATAR_DIR);
        return response;
    }

    @Override
    @Transactional
    public UserDetailResponse removeAvatar(Long userId) {
        User user = requireActiveUser(userId);

        String oldImage = user.getImage();
        user.setImage(null);
        UserDetailResponse response = toDetail(userRepository.save(user));

        fileStorageService.deleteImage(oldImage, FileStorageService.AVATAR_DIR);
        return response;
    }

    /** User đang đăng nhập, chưa xoá và chưa bị khoá. */
    private User requireActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted() && !u.isBanned())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy user"));
    }

    // ─── Admin — đọc ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserDetailResponse> getUsers(RoleName role, Boolean banned, String keyword, Long storeId, int page, int size) {
        Pageable pageable = PageableFactory.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserDetailResponse> result = userRepository
                .searchUsers(role, banned, (keyword == null || keyword.isBlank()) ? null : keyword.trim(), storeId, pageable)
                .map(this::toDetail);
        return PageResponse.from(result);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public UserDetailResponse getUser(Long id) {
        User user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy user"));
        return toDetail(user);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public List<UserDetailResponse> getStoreStaff(Long actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy user"));
        if (actor.getRole() != RoleName.MANAGER || actor.getStore() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Quản trị viên xem nhân sự ở trang Tài khoản");
        }
        return userRepository.findByStoreIdAndDeletedFalseOrderByRoleAscFullNameAsc(actor.getStore().getId())
                .stream().map(this::toDetail).toList();
    }

    // ─── Admin — ghi ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserDetailResponse createUser(Long actorId, com.banhmyking.banhmyking.dto.user.AdminCreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Email đã tồn tại trong hệ thống");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null);
        user.setRole(request.role() != null ? request.role() : RoleName.CUSTOMER);
        user.setStore(resolveStore(user.getRole(), request.storeId()));
        user.setBanned(false);
        user.setDeleted(false);
        // ADMIN tạo tài khoản nội bộ (staff/shipper) — không đi qua luồng xác thực email
        user.setEmailVerified(true);

        User saved = userRepository.save(user);
        return toDetail(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserDetailResponse updateUser(Long actorId, Long targetId, com.banhmyking.banhmyking.dto.user.AdminUpdateUserRequest request) {
        User target = findActiveTarget(targetId);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            target.setFullName(request.fullName().trim());
        }

        if (request.phone() != null) {
            target.setPhone(request.phone().trim());
        }

        if (request.password() != null && !request.password().isBlank()) {
            target.setPassword(passwordEncoder.encode(request.password()));
            // Giống changePassword/resetPassword: đổi mật khẩu thì đăng xuất mọi phiên cũ
            revokeRefreshTokens(targetId);
        }

        RoleName newRole = request.role() != null ? request.role() : target.getRole();
        Long newStoreId = request.storeId() != null ? request.storeId() : storeIdOf(target);
        boolean roleChanged = newRole != target.getRole();
        if (roleChanged) {
            if (targetId.equals(actorId)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Không thể thay đổi vai trò của chính mình");
            }
            if (target.getRole() == RoleName.ADMIN && countActiveAdmins() <= 1) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Không thể thay đổi vai trò của ADMIN cuối cùng");
            }
        }
        Store newStore = resolveStore(newRole, newStoreId);
        boolean storeChanged = !java.util.Objects.equals(newStore == null ? null : newStore.getId(), storeIdOf(target));
        target.setRole(newRole);
        target.setStore(newStore);
        if (roleChanged || storeChanged) {
            revokeRefreshTokens(targetId);
        }

        if (request.banned() != null && request.banned() != target.isBanned()) {
            if (targetId.equals(actorId)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Không thể khoá/mở tài khoản của chính mình");
            }
            if (Boolean.TRUE.equals(request.banned()) && target.getRole() == RoleName.ADMIN && countActiveAdmins() <= 1) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Không thể khoá ADMIN cuối cùng");
            }
            target.setBanned(request.banned());
            if (target.isBanned()) {
                revokeRefreshTokens(targetId);
            }
        }

        User updated = userRepository.save(target);
        return toDetail(updated);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserDetailResponse changeRole(Long actorId, Long targetId, UpdateRoleRequest request) {
        if (targetId.equals(actorId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Không thể thay đổi vai trò của chính mình");
        }

        User target = findActiveTarget(targetId);

        // nên không cho demote nếu đó là người cuối cùng còn giữ role — đếm theo "admin chưa xóa"
        if (target.getRole() == RoleName.ADMIN && request.role() != RoleName.ADMIN
                && userRepository.countByRoleAndDeletedFalse(RoleName.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Không thể thay đổi vai trò của ADMIN cuối cùng");
        }

        target.setStore(resolveStore(request.role(), request.storeId() != null ? request.storeId() : storeIdOf(target)));
        target.setRole(request.role());
        userRepository.save(target);

        // Đổi role = đổi quyền → ép re-login
        revokeRefreshTokens(targetId);
        return toDetail(target);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserDetailResponse changeStatus(Long actorId, Long targetId, UpdateStatusRequest request) {
        if (targetId.equals(actorId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Không thể khoá/mở tài khoản của chính mình");
        }

        User target = findActiveTarget(targetId);

        if (Boolean.TRUE.equals(request.banned())) {
            if (target.getRole() == RoleName.ADMIN && !target.isBanned() && countActiveAdmins() <= 1) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Không thể khoá ADMIN cuối cùng");
            }
            target.setBanned(true);
            // Khoá = chặn cả token đang sống → thu hồi refresh token
            revokeRefreshTokens(targetId);
        } else {
            target.setBanned(false);
        }

        userRepository.save(target);
        return toDetail(target);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(Long actorId, Long targetId) {
        if (targetId.equals(actorId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Không thể xoá tài khoản của chính mình");
        }

        User target = findActiveTarget(targetId);

        if (target.getRole() == RoleName.ADMIN && userRepository.countByRoleAndDeletedFalse(RoleName.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Không thể xoá ADMIN cuối cùng");
        }

        target.setDeleted(true);
        userRepository.save(target);
        refreshTokenRepository.deleteByUserId(targetId);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** STAFF/SHIPPER/MANAGER bắt buộc thuộc một cơ sở; vai trò khác luôn không thuộc cơ sở nào (spec §4). */
    private Store resolveStore(RoleName role, Long storeId) {
        if (!STORE_ROLES.contains(role)) {
            return null;
        }
        if (storeId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn cơ sở làm việc cho vai trò " + role);
        }
        return storeRepository.findByIdAndDeletedFalse(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy cơ sở với ID: " + storeId));
    }

    private static Long storeIdOf(User user) {
        return user.getStore() == null ? null : user.getStore().getId();
    }

    private User findActiveTarget(Long targetId) {
        return userRepository.findByIdAndDeletedFalse(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy user"));
    }

    private long countActiveAdmins() {
        return userRepository.countByRoleAndDeletedFalseAndBannedFalse(RoleName.ADMIN);
    }

    private void revokeRefreshTokens(Long userId) {
        List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        if (active.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        active.forEach(rt -> {
            rt.setRevokedAt(now);
            refreshTokenRepository.save(rt);
        });
    }

    private UserDetailResponse toDetail(User user) {
        return new UserDetailResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getImage(), user.getRole(), user.isBanned(), user.getCreatedAt(),
                user.getStore() == null ? null : user.getStore().getId(),
                user.getStore() == null ? null : user.getStore().getName());
    }
}
