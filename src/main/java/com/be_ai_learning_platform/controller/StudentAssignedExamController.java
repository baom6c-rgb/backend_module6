package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.CheatingEventRequest;
import com.be_ai_learning_platform.dto.request.SubmitPracticeRequest;
import com.be_ai_learning_platform.dto.response.AttemptReviewResponse;
import com.be_ai_learning_platform.dto.response.StudentAssignedExamItemResponse;
import com.be_ai_learning_platform.dto.response.StudentStartAssignedExamResponse;
import com.be_ai_learning_platform.dto.response.SubmitPracticeResponse;
import com.be_ai_learning_platform.service.CheatingEventService;
import com.be_ai_learning_platform.service.StudentAssignedExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student/assigned-exams")
@RequiredArgsConstructor
public class StudentAssignedExamController {

    private final StudentAssignedExamService service;
    private final CheatingEventService cheatingEventService;

    @GetMapping
    public ResponseEntity<List<StudentAssignedExamItemResponse>> list(
            @AuthenticationPrincipal String email
    ) {
        return ResponseEntity.ok(service.list(email));
    }

    @PostMapping("/{assignmentId}/start")
    public ResponseEntity<StudentStartAssignedExamResponse> start(
            @AuthenticationPrincipal String email,
            @PathVariable Long assignmentId
    ) {
        return ResponseEntity.ok(service.start(email, assignmentId));
    }

    @PostMapping("/{assignmentId}/submit")
    public ResponseEntity<SubmitPracticeResponse> submit(
            @AuthenticationPrincipal String email,
            @PathVariable Long assignmentId,
            @Valid @RequestBody SubmitPracticeRequest req
    ) {
        return ResponseEntity.ok(service.submit(email, assignmentId, req));
    }

    // ===== Anti-cheat logging =====
    @PostMapping("/{assignmentId}/cheating")
    public ResponseEntity<Map<String, Object>> reportCheating(
            @AuthenticationPrincipal String email,
            @PathVariable Long assignmentId,
            @Valid @RequestBody CheatingEventRequest req
    ) {
        return ResponseEntity.ok(cheatingEventService.report(email, assignmentId, req));
    }

    @GetMapping("/{assignmentId}/review")
    public ResponseEntity<AttemptReviewResponse> review(
            @AuthenticationPrincipal String email,
            @PathVariable Long assignmentId
    ) {
        return ResponseEntity.ok(service.getReview(email, assignmentId));
    }

    @GetMapping("/{assignmentId}/study-guide")
    public ResponseEntity<Map<String, String>> studyGuide(
            @AuthenticationPrincipal String email,
            @PathVariable Long assignmentId
    ) {
        String guide = service.getStudyGuide(email, assignmentId);
        return ResponseEntity.ok(Map.of("studyGuide", guide == null ? "" : guide));
    }
}
