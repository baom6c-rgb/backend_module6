package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.SelectClassRequest;
import com.be_ai_learning_platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/select-class")
    public ResponseEntity<?> selectClass(@RequestBody SelectClassRequest request) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated");
        }

        String email = authentication.getName();

        userService.selectClass(email, request.getClassId());
        return ResponseEntity.ok().build();
    }

// phần này chỉ để test về sau xoá
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        return ResponseEntity.ok(authentication.getName());
    }
}
