package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.*;
import com.be_ai_learning_platform.dto.response.AuthResponse;
import com.be_ai_learning_platform.dto.response.EmailOtpResponse;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.UserRoleRepository;
import com.be_ai_learning_platform.security.JwtUtil;
import com.be_ai_learning_platform.service.AuthService;
import com.be_ai_learning_platform.service.EmailOtpService;
import com.be_ai_learning_platform.service.GoogleAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5175")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final JwtUtil jwtUtil;
    private final EmailOtpService emailOtpService;

    // ✅ thêm
    private final UserRoleRepository userRoleRepository;

    @PostMapping("/otp/request")
    public ResponseEntity<EmailOtpResponse> requestEmailOtp(@Valid @RequestBody RequestEmailOtpRequest request) {
        EmailOtpResponse res = emailOtpService.requestOtp(request.getEmail());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyEmailOtp(@Valid @RequestBody VerifyEmailOtpRequest request) {
        emailOtpService.verifyOtp(request.getOtpSessionId(), request.getEmail(), request.getOtp());
        return ResponseEntity.ok("OTP verified");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok("Register success. Waiting for approval");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request);
        return buildAuthResponse(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }
        return ResponseEntity.ok("Đã đăng xuất thành công");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.processForgotPassword(request.getEmail());
        return ResponseEntity.ok("Link đặt lại mật khẩu đã được gửi vào email của bạn.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.updatePassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok("Mật khẩu đã được cập nhật thành công.");
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@RequestBody GoogleLoginRequest request) {
        User user = googleAuthService.authenticate(request.getIdToken());
        return buildAuthResponse(user);
    }

    // ✅ complete-profile trả AuthResponse để FE nhận token WAITING_APPROVAL
    @PostMapping("/complete-profile")
    public ResponseEntity<AuthResponse> completeProfile(@RequestBody CompleteProfileRequest request) {
        User user = authService.completeProfile(request);
        return buildAuthResponse(user);
    }

    private ResponseEntity<AuthResponse> buildAuthResponse(User user) {
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new RuntimeException("Tài khoản đã bị khóa");
        }

        // ✅ lấy roles chắc chắn (không dựa vào user.getUserRoles())
        List<String> roles = userRoleRepository.findRoleNamesByUserId(user.getId());

        // ✅ CREATED: chưa cần token
        if (user.getStatus() == UserStatus.CREATED) {
            return ResponseEntity.ok(new AuthResponse(null, roles, user.getStatus().name(), user.getEmail()));
        }

        // ✅ WAITING_APPROVAL: phát token để vào màn hình chờ (Hướng B)
        if (user.getStatus() == UserStatus.WAITING_APPROVAL) {
            String token = jwtUtil.generateToken(user.getEmail(), roles);
            return ResponseEntity.ok(new AuthResponse(token, roles, user.getStatus().name(), user.getEmail()));
        }

        // ✅ ACTIVE: token bình thường
        if (user.getStatus() == UserStatus.ACTIVE) {
            String token = jwtUtil.generateToken(user.getEmail(), roles);
            return ResponseEntity.ok(new AuthResponse(token, roles, user.getStatus().name(), user.getEmail()));
        }

        return ResponseEntity.ok(new AuthResponse(null, roles, user.getStatus().name(), user.getEmail()));
    }
}
