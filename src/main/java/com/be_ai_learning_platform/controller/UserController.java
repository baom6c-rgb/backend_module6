package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.UserUpdateDTO;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Cho phép Frontend gọi API
public class UserController {

    private final UserService userService;

    @PutMapping("/profile/{id}")
    public ResponseEntity<User> updateProfile(@PathVariable Long id, @RequestBody UserUpdateDTO updateDTO) {
        User updatedUser = userService.updateProfile(id, updateDTO);
        return ResponseEntity.ok(updatedUser);
    }
}
