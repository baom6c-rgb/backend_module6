package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.AdminUpdateUserRequest;
import com.be_ai_learning_platform.dto.request.AdminAddUserRequest;
import com.be_ai_learning_platform.dto.response.AdminResponse;
import com.be_ai_learning_platform.dto.response.AdminUserDetailResponse;
import com.be_ai_learning_platform.dto.response.OptionResponse;
import com.be_ai_learning_platform.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // US4: Admin tạo user mới
    @PostMapping("/users")
    public ResponseEntity<AdminResponse> addUser(@Valid @RequestBody AdminAddUserRequest request) {
        return new ResponseEntity<>(adminService.addUser(request), HttpStatus.CREATED);
    }

    // Hiển thị danh sách tất cả user
    @GetMapping("/users")
    public ResponseEntity<List<AdminResponse>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    // Danh sách chờ duyệt
    @GetMapping("/approvals/pending")
    public ResponseEntity<List<AdminResponse>> pendingApprovals() {
        return ResponseEntity.ok(adminService.getPendingApprovals());
    }

    // Phê duyệt user
    @PostMapping("/approvals/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id) {
        adminService.approve(id);
        return ResponseEntity.ok().build();
    }

    // Khóa user
    @PostMapping("/students/{id}/block")
    public ResponseEntity<Void> block(@PathVariable Long id) {
        adminService.blockUser(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/students/{id}/unblock")
    public ResponseEntity<Void> unblock(@PathVariable Long id) {
        adminService.unblockUser(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/students/blocked")
    public ResponseEntity<List<AdminResponse>> blockedStudents() {
        return ResponseEntity.ok(adminService.getBlockedUsers());
    }


    // ====================== US5 ======================

    // load detail for edit form
    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserDetailResponse> getUserDetail(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUserDetail(id));
    }

    // update user
    @PutMapping("/users/{id}")
    public ResponseEntity<AdminUserDetailResponse> updateUser(
            @PathVariable Long id,
            @RequestBody @Valid AdminUpdateUserRequest request
    ) {
        return ResponseEntity.ok(adminService.updateUser(id, request));
    }

    // dropdown options
    @GetMapping("/options/roles")
    public ResponseEntity<List<OptionResponse>> roleOptions() {
        return ResponseEntity.ok(adminService.getRoleOptions());
    }

    @GetMapping("/options/classes")
    public ResponseEntity<List<OptionResponse>> classOptions() {
        return ResponseEntity.ok(adminService.getClassOptions());
    }

    @GetMapping("/options/modules")
    public ResponseEntity<List<OptionResponse>> moduleOptions() {
        return ResponseEntity.ok(adminService.getModuleOptions());
    }

}
