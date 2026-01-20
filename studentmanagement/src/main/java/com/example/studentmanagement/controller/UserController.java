package com.example.studentmanagement.controller;

import com.example.studentmanagement.dto.SelectClassRequest;
import com.example.studentmanagement.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/select-class")
    public ResponseEntity<?> selectClass(
            @RequestBody SelectClassRequest request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        userService.selectClass(email, request.getClassId());
        return ResponseEntity.ok().build();
    }
}
