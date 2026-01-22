package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.*;
import com.be_ai_learning_platform.dto.response.AuthResponse;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.security.JwtUtil;
import com.be_ai_learning_platform.service.AuthService;
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

    // 2. YÊU CẦU QUÊN MẬT KHẨU (Gửi mail)
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.processForgotPassword(request.getEmail());
        return ResponseEntity.ok("Link đặt lại mật khẩu đã được gửi vào email của bạn.");
    }

    // 3. ĐẶT LẠI MẬT KHẨU MỚI
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.updatePassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok("Mật khẩu đã được cập nhật thành công.");
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@RequestBody GoogleLoginRequest request) throws Exception {
        User user = googleAuthService.authenticate(request.getIdToken());
        return buildAuthResponse(user);
    }
    @PostMapping("/complete-profile")
    public ResponseEntity<?> completeProfile(
            @RequestBody CompleteProfileRequest request
    ) {
        authService.completeProfile(request);
        return ResponseEntity.ok("Profile completed. Waiting for approval");
    }

    private ResponseEntity<AuthResponse> buildAuthResponse(User user) {
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new RuntimeException("Tài khoản đã bị khóa");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            return ResponseEntity.ok(
                    new AuthResponse(
                            null,
                            null,
                            user.getStatus().name(),
                            user.getEmail()
                    )
            );

        }

        List<String> roles = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getName())
                .toList();

        String token = jwtUtil.generateToken(user.getEmail(), roles);

        return ResponseEntity.ok(
                new AuthResponse(
                        token,
                        roles,
                        user.getStatus().name(),
                        user.getEmail()
                )
        );

    }
}
