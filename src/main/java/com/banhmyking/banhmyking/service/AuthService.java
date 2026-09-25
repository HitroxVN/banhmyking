package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.auth.ChangePasswordRequest;
import com.banhmyking.banhmyking.dto.auth.LoginRequest;
import com.banhmyking.banhmyking.dto.auth.RegisterRequest;
import com.banhmyking.banhmyking.dto.auth.TokenResponse;

public interface AuthService {
    /** Tạo tài khoản ở trạng thái CHƯA xác thực và gửi mail xác thực — không trả token. */
    void register(RegisterRequest request);
    TokenResponse login(LoginRequest request);
    TokenResponse refresh(String rawRefreshToken);
    void logout(String rawRefreshToken);
    void changePassword(Long userId, ChangePasswordRequest request);
    /** Đổi token thô trong link email thành trạng thái đã xác thực. */
    void verifyEmail(String rawToken);
    /** Gửi lại mail xác thực cho tài khoản chưa xác thực. */
    void resendVerificationEmail(String email);
    /**
     * Quên mật khẩu bước 1: gửi link đặt lại nếu email tồn tại.
     * Không tiết lộ email có trong hệ thống hay không — im lặng nếu không tìm thấy.
     */
    void forgotPassword(String email);
    /** Quên mật khẩu bước 2: đặt mật khẩu mới bằng token trong link, thu hồi mọi phiên đang đăng nhập. */
    void resetPassword(String rawToken, String newPassword);
    // getMe chuyển sang UserService — một nguồn sự thật cho profile, /auth/me delegate sang đó
}
