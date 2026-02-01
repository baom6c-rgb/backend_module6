package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.*;
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

    // =========================
    // V2 - No preview, no DB until submit
    // =========================

    /**
     * V2/Step 1: Generate (AI sinh câu hỏi) -> chỉ trả token + thời gian, KHÔNG trả danh sách câu hỏi.
     * FE sẽ hiển thị nút "Bắt đầu làm bài".
     */
    @PostMapping("/v2/generate")
    public GeneratePracticeSessionResponse generateSessionV2(
            Authentication authentication,
            @Valid @RequestBody GeneratePracticeSessionRequest req
    ) {
        return practiceService.generateSessionV2(authentication.getName(), req);
    }

    /**
     * V2/Step 2: Start -> trả danh sách câu hỏi (không có đáp án đúng).
     */
    @PostMapping("/v2/start")
    public StartPracticeSessionResponse startSessionV2(
            Authentication authentication,
            @Valid @RequestBody StartPracticeSessionRequest req
    ) {
        return practiceService.startSessionV2(authentication.getName(), req);
    }

    /**
     * V2/Resume: reload trang -> lấy lại state + deadline.
     */
    @GetMapping("/v2/sessions/{sessionToken}")
    public StartPracticeSessionResponse getSessionV2(
            Authentication authentication,
            @PathVariable String sessionToken
    ) {
        return practiceService.getSessionV2(authentication.getName(), sessionToken);
    }

    /**
     * V2/Submit: chấm điểm + LƯU DB (chỉ lưu khi submit).
     */
    @PostMapping("/v2/sessions/{sessionToken}/submit")
    public SubmitPracticeV2Response submitSessionV2(
            Authentication authentication,
            @PathVariable String sessionToken,
            @Valid @RequestBody SubmitPracticeSessionRequest req
    ) {
        return practiceService.submitSessionV2(authentication.getName(), sessionToken, req);
    }
}
