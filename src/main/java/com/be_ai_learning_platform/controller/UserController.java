package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.UserUpdateDTO;
import com.be_ai_learning_platform.dto.request.StudentUpdateProfileRequest;
import com.be_ai_learning_platform.dto.request.ChangePasswordRequest;
import com.be_ai_learning_platform.dto.response.StudentProfileResponse;
import com.be_ai_learning_platform.dto.response.UserStatusResponse;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ===== legacy/admin =====
    @PutMapping("/profile/{id}")
    public ResponseEntity<User> updateProfile(@PathVariable Long id, @RequestBody UserUpdateDTO updateDTO) {
        User updatedUser = userService.updateProfile(id, updateDTO);
        return ResponseEntity.ok(updatedUser);
    }

    // ===== US2: public polling status (waiting approval screen) =====
    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestParam String email) {
        try {
            return ResponseEntity.ok(userService.getStatusByEmail(email));
        } catch (RuntimeException ex) {
            // user bị reject-delete hoặc không tồn tại
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }
    }

    // ===== US3: student profile =====
    @GetMapping("/me/profile")
    public ResponseEntity<StudentProfileResponse> getMyProfile(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(userService.getMyProfileByEmail(email));
    }

    @PutMapping("/me/profile")
    public ResponseEntity<Void> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody StudentUpdateProfileRequest request
    ) {
        String email = authentication.getName();
        userService.updateStudentProfileByEmail(email, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<StudentProfileResponse> uploadMyAvatar(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        String email = authentication.getName();
        return ResponseEntity.ok(userService.updateMyAvatarByEmail(email, file));
    }

    // ======================= ✅ NEW: Change password =======================
    @PutMapping("/me/password")
    public ResponseEntity<?> changeMyPassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        try {
            String email = authentication.getName();
            userService.changeMyPasswordByEmail(email, request);
            return ResponseEntity.ok("Đổi mật khẩu thành công");
        } catch (Exception e) {
            String msg = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : "Đổi mật khẩu thất bại";
            return ResponseEntity.badRequest().body(msg);
        }
    }

    // ===== test only - remove later =====
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        return ResponseEntity.ok(authentication.getName());
    }
}
