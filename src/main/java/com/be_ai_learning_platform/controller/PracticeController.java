package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.PracticeGenerateRequest;
import com.be_ai_learning_platform.dto.request.SubmitPracticeRequest;
import com.be_ai_learning_platform.dto.response.*;
import com.be_ai_learning_platform.service.PracticeService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/practice")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    // 1) Generate preview (xem trước)
    @PostMapping("/generate")
    public GenerateQuestionsResponse generatePreview(
            Authentication authentication,
            @Valid @RequestBody PracticeGenerateRequest req
    ) {
        return practiceService.generatePreview(authentication.getName(), req);
    }

    // 2) Start practice (bấm làm bài -> tạo attemptId)
    @PostMapping("/start")
    public StartPracticeResponse start(
            Authentication authentication,
            @Valid @RequestBody PracticeGenerateRequest req
    ) {
        return practiceService.start(authentication.getName(), req);
    }

    // 3) Load attempt (đề để làm bài) - không trả correctAnswer
    @GetMapping("/attempts/{attemptId}")
    public AttemptDetailResponse getAttempt(
            Authentication authentication,
            @PathVariable Long attemptId
    ) {
        return practiceService.getAttempt(authentication.getName(), attemptId);
    }

    // 4) Submit (chấm điểm + nhận xét)
    @PostMapping("/attempts/{attemptId}/submit")
    public SubmitPracticeResponse submit(
            Authentication authentication,
            @PathVariable Long attemptId,
            @Valid @RequestBody SubmitPracticeRequest req
    ) {
        return practiceService.submit(authentication.getName(), attemptId, req);
    }

    // ✅ 5) Review (xem lại đáp án sau khi submit)
    @GetMapping("/attempts/{attemptId}/review")
    public AttemptReviewResponse review(
            Authentication authentication,
            @PathVariable Long attemptId
    ) {
        return practiceService.getReview(authentication.getName(), attemptId);
    }
}
