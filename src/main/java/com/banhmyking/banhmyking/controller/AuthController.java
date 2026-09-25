package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.auth.ChangePasswordRequest;
import com.banhmyking.banhmyking.dto.auth.ForgotPasswordRequest;
import com.banhmyking.banhmyking.dto.auth.LoginRequest;
import com.banhmyking.banhmyking.dto.auth.ResetPasswordRequest;
import com.banhmyking.banhmyking.dto.auth.RefreshRequest;
import com.banhmyking.banhmyking.dto.auth.RegisterRequest;
import com.banhmyking.banhmyking.dto.auth.ResendVerificationRequest;
import com.banhmyking.banhmyking.dto.auth.TokenResponse;
import com.banhmyking.banhmyking.dto.auth.VerifyEmailRequest;
import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.user.UserDetailResponse;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.AuthService;
import com.banhmyking.banhmyking.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        // Không trả token: tài khoản còn phải xác thực email mới đăng nhập được.
        return ApiResponse.ok("Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản.");
    }

    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ApiResponse.ok("Đã xác thực thành công email.");
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationEmail(request.email());
        return ApiResponse.ok("Đã gửi lại email xác thực. Vui lòng kiểm tra hộp thư.");
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        // Thông điệp cố định cho mọi trường hợp — không tiết lộ email có tồn tại hay không.
        return ApiResponse.ok("Nếu email này đã đăng ký, chúng tôi đã gửi link đặt lại mật khẩu. Vui lòng kiểm tra hộp thư.");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.ok("Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.");
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Đăng nhập thành công", authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok("Làm mới token thành công", authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok("Đăng xuất thành công");
    }

    @PatchMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(SecurityUtils.requireUserId(principal), request);
        return ApiResponse.ok("Đổi mật khẩu thành công");
    }

    @GetMapping("/me")
    public ApiResponse<UserDetailResponse> me(@AuthenticationPrincipal UserDetails principal) {
        // delegate sang UserService — /auth/me và /users/me trả cùng shape, một nguồn sự thật
        return ApiResponse.ok(userService.getMe(SecurityUtils.requireUserId(principal)));
    }
}
