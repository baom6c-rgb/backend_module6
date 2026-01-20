package com.example.studentmanagement.controller;

import com.example.studentmanagement.dto.*;
import com.example.studentmanagement.entity.User;
import com.example.studentmanagement.enums.UserStatus;
import com.example.studentmanagement.security.JwtUtil;
import com.example.studentmanagement.service.AuthService;
import com.example.studentmanagement.service.GoogleAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5175")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final JwtUtil jwtUtil;

    // ======================= REGISTER =======================
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        authService.register(request);
        return ResponseEntity.ok("Register success. Waiting for approval");
    }

    // ======================= FORM LOGIN =======================
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {

        User user = authService.authenticate(request);

        // ❌ BLOCKED
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new RuntimeException("Tài khoản của bạn đã bị khóa");
        }

        // ⏳ PENDING → KHÔNG SINH JWT
        if (user.getStatus() == UserStatus.PENDING) {
            return ResponseEntity.ok(
                    new AuthResponse(
                            null,
                            user.getRole().name(),
                            user.getStatus().name()
                    )
            );
        }

        // ✅ APPROVED → SINH JWT
        String token = jwtUtil.generateToken(user.getEmail());

        return ResponseEntity.ok(
                new AuthResponse(
                        token,
                        user.getRole().name(),
                        user.getStatus().name()
                )
        );
    }

    // ======================= GOOGLE LOGIN =======================
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(
            @RequestBody GoogleLoginRequest request
    ) throws Exception {

        User user = googleAuthService.authenticate(request.getIdToken());

        // ❌ BLOCKED
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new RuntimeException("Tài khoản Google đã bị khóa");
        }

        // ⏳ PENDING → KHÔNG SINH JWT
        if (user.getStatus() == UserStatus.PENDING) {
            return ResponseEntity.ok(
                    new AuthResponse(
                            null,
                            user.getRole().name(),
                            user.getStatus().name()
                    )
            );
        }

        // ✅ APPROVED → SINH JWT
        String token = jwtUtil.generateToken(user.getEmail());

        return ResponseEntity.ok(
                new AuthResponse(
                        token,
                        user.getRole().name(),
                        user.getStatus().name()
                )
        );
    }
}
