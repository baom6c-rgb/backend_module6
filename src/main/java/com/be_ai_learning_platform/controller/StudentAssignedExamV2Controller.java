package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.GenerateAssignedExamSessionRequest;
import com.be_ai_learning_platform.dto.request.StartPracticeSessionRequest;
import com.be_ai_learning_platform.dto.request.SubmitPracticeSessionRequest;
import com.be_ai_learning_platform.dto.response.GeneratePracticeSessionResponse;
import com.be_ai_learning_platform.dto.response.StartPracticeSessionResponse;
import com.be_ai_learning_platform.dto.response.SubmitPracticeV2Response;
import com.be_ai_learning_platform.service.AssignedExamV2Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/assigned-exams/v2")
@RequiredArgsConstructor
public class StudentAssignedExamV2Controller {

    private final AssignedExamV2Service service;

    @PostMapping("/generate")
    public ResponseEntity<GeneratePracticeSessionResponse> generate(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody GenerateAssignedExamSessionRequest req
    ) {
        return ResponseEntity.ok(service.generateAssignedSessionV2(email, req));
    }

    @PostMapping("/start")
    public ResponseEntity<StartPracticeSessionResponse> start(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody StartPracticeSessionRequest req
    ) {
        return ResponseEntity.ok(service.startAssignedSessionV2(email, req));
    }

    @GetMapping("/sessions/{sessionToken}")
    public ResponseEntity<StartPracticeSessionResponse> getSession(
            @AuthenticationPrincipal String email,
            @PathVariable String sessionToken
    ) {
        return ResponseEntity.ok(service.getAssignedSessionV2(email, sessionToken));
    }

    @PostMapping("/sessions/{sessionToken}/submit")
    public ResponseEntity<SubmitPracticeV2Response> submit(
            @AuthenticationPrincipal String email,
            @PathVariable String sessionToken,
            @Valid @RequestBody SubmitPracticeSessionRequest req
    ) {
        return ResponseEntity.ok(service.submitAssignedSessionV2(email, sessionToken, req));
    }
}
