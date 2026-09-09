package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.auth.ChangePasswordRequest;
import com.banhmyking.banhmyking.dto.auth.LoginRequest;
import com.banhmyking.banhmyking.dto.auth.RegisterRequest;
import com.banhmyking.banhmyking.dto.auth.TokenResponse;
import com.banhmyking.banhmyking.dto.auth.UserInfoResponse;

public interface AuthService {
    TokenResponse register(RegisterRequest request);
    TokenResponse login(LoginRequest request);
    TokenResponse refresh(String rawRefreshToken);
    void logout(String rawRefreshToken);
    void changePassword(Long userId, ChangePasswordRequest request);
    UserInfoResponse getMe(Long userId);
}
