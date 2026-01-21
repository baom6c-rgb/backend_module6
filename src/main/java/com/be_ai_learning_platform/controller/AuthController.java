package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.CompleteProfileRequest;
import com.be_ai_learning_platform.dto.request.GoogleLoginRequest;
import com.be_ai_learning_platform.dto.request.LoginRequest;
import com.be_ai_learning_platform.dto.request.RegisterRequest;
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
