package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.response.AdminResponse;
import com.be_ai_learning_platform.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ===== Pending approvals =====
    @GetMapping("/approvals/pending")
    public ResponseEntity<List<AdminResponse>> pendingApprovals() {
        return ResponseEntity.ok(adminService.getPendingApprovals());
    }

    @PostMapping("/approvals/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id) {
        adminService.approve(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/approvals/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id) {
        adminService.reject(id);
        return ResponseEntity.ok().build();
    }

    // ===== Active students (block/unblock) =====
    @GetMapping("/students/active")
    public ResponseEntity<List<AdminResponse>> activeStudents() {
        return ResponseEntity.ok(adminService.getActiveStudents());
    }

    @PostMapping("/students/{id}/block")
    public ResponseEntity<Void> block(@PathVariable Long id) {
        adminService.block(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/students/{id}/unblock")
    public ResponseEntity<Void> unblock(@PathVariable Long id) {
        adminService.unblock(id);
        return ResponseEntity.ok().build();
    }
}
