package com.banhmyking.banhmyking.dto.auth;

import com.banhmyking.banhmyking.enums.RoleName;

public record UserInfoResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        RoleName role
) {}
