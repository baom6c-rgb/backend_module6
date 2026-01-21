package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.response.AuthResponse;
import com.be_ai_learning_platform.dto.request.GoogleLoginRequest;
import com.be_ai_learning_platform.dto.request.LoginRequest;
import com.be_ai_learning_platform.dto.request.RegisterRequest;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.UserRole;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.security.JwtUtil;
import com.be_ai_learning_platform.service.AuthService;
import com.be_ai_learning_platform.service.GoogleAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5175")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final JwtUtil jwtUtil;

    // ======================= REGISTER (FORM) =======================
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        authService.register(request);
        return ResponseEntity.ok("Register success. Waiting for approval");
    }

    // ======================= LOGIN FORM =======================
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        User user = authService.login(request);

        return buildAuthResponse(user);
    }

    // ======================= LOGIN GOOGLE =======================
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(
            @RequestBody GoogleLoginRequest request
    ) throws Exception {

        User user = googleAuthService.authenticate(request.getIdToken());

        return buildAuthResponse(user);
    }

    // ======================= COMMON RESPONSE =======================
    private ResponseEntity<AuthResponse> buildAuthResponse(User user) {

        // ❌ BLOCKED
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new RuntimeException("Tài khoản đã bị khóa");
        }

        // ⏳ CHƯA ACTIVE → KHÔNG CẤP JWT
        if (user.getStatus() != UserStatus.ACTIVE) {
            return ResponseEntity.ok(
                    new AuthResponse(
                            null,
                            null,
                            user.getStatus().name()
                    )
            );
        }

        // ✅ ACTIVE → LẤY ROLE → SINH JWT
        List<String> roles = user.getUserRoles()
                .stream()
                .map(ur -> ur.getRole().getName())
                .collect(Collectors.toList());

        String token = jwtUtil.generateToken(
                user.getEmail(),
                roles
        );

        return ResponseEntity.ok(
                new AuthResponse(
                        token,
                        roles,
                        user.getStatus().name()
                )
        );
    }
}
