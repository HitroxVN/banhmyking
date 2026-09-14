package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.user.UpdateProfileRequest;
import com.banhmyking.banhmyking.dto.user.UpdateRoleRequest;
import com.banhmyking.banhmyking.dto.user.UpdateStatusRequest;
import com.banhmyking.banhmyking.dto.user.UserDetailResponse;
import com.banhmyking.banhmyking.enums.RoleName;

public interface UserService {

    // ─── Self-service (mọi user đã đăng nhập) ────────────────────────────────

    UserDetailResponse getMe(Long userId);

    UserDetailResponse updateProfile(Long userId, UpdateProfileRequest request);

    // ─── Admin — đọc (STAFF + ADMIN) ─────────────────────────────────────────

    PageResponse<UserDetailResponse> getUsers(RoleName role, Boolean banned, String keyword, int page, int size);

    UserDetailResponse getUser(Long id);

    // ─── Admin — ghi (chỉ ADMIN) ─────────────────────────────────────────────

    UserDetailResponse createUser(Long actorId, com.banhmyking.banhmyking.dto.user.AdminCreateUserRequest request);

    UserDetailResponse updateUser(Long actorId, Long targetId, com.banhmyking.banhmyking.dto.user.AdminUpdateUserRequest request);

    UserDetailResponse changeRole(Long actorId, Long targetId, UpdateRoleRequest request);

    UserDetailResponse changeStatus(Long actorId, Long targetId, UpdateStatusRequest request);

    void deleteUser(Long actorId, Long targetId);
}
