package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.*;
import com.be_ai_learning_platform.dto.response.AdminExamDetailResponse;
import com.be_ai_learning_platform.dto.response.AdminExamPreviewResponse;
import com.be_ai_learning_platform.service.AdminAssignedExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/assigned-exams")
@RequiredArgsConstructor
public class AdminAssignedExamController {

    private final AdminAssignedExamService service;

    // ===== AI preview =====
    @PostMapping("/preview")
    public ResponseEntity<AdminExamPreviewResponse> preview(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody AdminExamPreviewRequest req
    ) {
        return ResponseEntity.ok(service.preview(email, req));
    }

    // ===== Create + assign =====
    @PostMapping
    public ResponseEntity<Map<String, Object>> createAndAssign(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody AdminCreateAssignedExamRequest req
    ) {
        return ResponseEntity.ok(service.createAndAssign(email, req));
    }

    // ===== List =====
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listMyExams(
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(service.listMyExams(email));
    }

    // ===== Detail =====
    @GetMapping("/{examId}")
    public ResponseEntity<AdminExamDetailResponse> detail(
            @AuthenticationPrincipal String email,
            @PathVariable Long examId
    ) {
        return ResponseEntity.ok(service.getDetail(email, examId));
    }

    @GetMapping("/{examId}/cheating")
    public ResponseEntity<List<Map<String, Object>>> cheatingEvents(
            @AuthenticationPrincipal String email,
            @PathVariable Long examId
    ) {
        return ResponseEntity.ok(service.getCheatingEvents(email, examId));
    }

    // ===== Update =====
    @PutMapping("/{examId}")
    public ResponseEntity<Map<String, Object>> update(
            @AuthenticationPrincipal String email,
            @PathVariable Long examId,
            @RequestBody AdminUpdateAssignedExamRequest req
    ) {
        return ResponseEntity.ok(service.update(email, examId, req));
    }

    // ===== Hard delete =====
    @DeleteMapping("/{examId}")
    public ResponseEntity<Map<String, Object>> hardDelete(
            @AuthenticationPrincipal String email,
            @PathVariable Long examId
    ) {
        return ResponseEntity.ok(service.hardDelete(email, examId));
    }
}
