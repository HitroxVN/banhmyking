package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.common.PageResponse;
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
import com.banhmyking.banhmyking.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    // ─── Self-service ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserDetailResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted() && !u.isBanned())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy user"));
        return toDetail(user);
    }

    @Override
    @Transactional
    public UserDetailResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted() && !u.isBanned())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy user"));

        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        user.setImage(request.imageUrl());
        return toDetail(userRepository.save(user));
    }

    // ─── Admin — đọc ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public PageResponse<UserDetailResponse> getUsers(RoleName role, Boolean banned, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserDetailResponse> result = userRepository
                .searchUsers(role, banned, (keyword == null || keyword.isBlank()) ? null : keyword.trim(), pageable)
                .map(this::toDetail);
        return PageResponse.from(result);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public UserDetailResponse getUser(Long id) {
        User user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy user"));
        return toDetail(user);
    }

    // ─── Admin — ghi ─────────────────────────────────────────────────────────

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
                user.getImage(), user.getRole(), user.isBanned(), user.getCreatedAt());
    }
}
