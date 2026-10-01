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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Auth", description = "APIs đăng ký, đăng nhập, xác thực email và quản lý mật khẩu")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Đăng ký tài khoản",
            description = "Tạo tài khoản mới và gửi email xác thực. Không trả token — phải xác thực email trước khi đăng nhập.")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        // Không trả token: tài khoản còn phải xác thực email mới đăng nhập được.
        return ApiResponse.ok("Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản.");
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Xác thực email",
            description = "Kích hoạt tài khoản bằng token trong link gửi qua email.")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ApiResponse.ok("Đã xác thực thành công email.");
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Gửi lại email xác thực",
            description = "Gửi lại link xác thực cho tài khoản chưa kích hoạt.")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationEmail(request.email());
        return ApiResponse.ok("Nếu email này đã đăng ký và chưa xác thực, chúng tôi đã gửi lại link xác thực. Vui lòng kiểm tra hộp thư.");
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Yêu cầu đặt lại mật khẩu",
            description = "Gửi link đặt lại mật khẩu qua email. Thông điệp trả về luôn cố định để không tiết lộ email có tồn tại hay không.")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        // Thông điệp cố định cho mọi trường hợp — không tiết lộ email có tồn tại hay không.
        return ApiResponse.ok("Nếu email này đã đăng ký, chúng tôi đã gửi link đặt lại mật khẩu. Vui lòng kiểm tra hộp thư.");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Đặt lại mật khẩu",
            description = "Đặt mật khẩu mới bằng token nhận qua email.")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.ok("Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.");
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập",
            description = "Trả về access token + refresh token. Yêu cầu tài khoản đã xác thực email và không bị khoá.")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Đăng nhập thành công", authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới token",
            description = "Đổi refresh token lấy cặp token mới (có xoay token). Dùng lại token đã thu hồi sẽ thu hồi toàn bộ token của user.")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok("Làm mới token thành công", authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất",
            description = "Thu hồi refresh token. Access token hiện có vẫn dùng được tới khi hết hạn.")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok("Đăng xuất thành công");
    }

    @PatchMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu",
            description = "Đổi mật khẩu của chính mình, cần nhập mật khẩu hiện tại.")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(SecurityUtils.requireUserId(principal), request);
        return ApiResponse.ok("Đổi mật khẩu thành công");
    }

    @GetMapping("/me")
    @Operation(summary = "Thông tin tài khoản đang đăng nhập",
            description = "Trả cùng shape với /users/me.")
    public ApiResponse<UserDetailResponse> me(@AuthenticationPrincipal UserDetails principal) {
        // delegate sang UserService — /auth/me và /users/me trả cùng shape, một nguồn sự thật
        return ApiResponse.ok(userService.getMe(SecurityUtils.requireUserId(principal)));
    }
}
