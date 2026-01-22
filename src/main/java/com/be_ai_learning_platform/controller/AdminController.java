package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.AdminAddUserRequest;
import com.be_ai_learning_platform.dto.response.AdminResponse;
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
        adminService.block(id);
        return ResponseEntity.ok().build();
    }
}