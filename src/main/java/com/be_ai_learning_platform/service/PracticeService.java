package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.PracticeGenerateRequest;
import com.be_ai_learning_platform.dto.request.SubmitPracticeRequest;
import com.be_ai_learning_platform.dto.response.*;

public interface PracticeService {
    GenerateQuestionsResponse generatePreview(String email, PracticeGenerateRequest req);
    StartPracticeResponse start(String email, PracticeGenerateRequest req);
    AttemptDetailResponse getAttempt(String email, Long attemptId);
    SubmitPracticeResponse submit(String email, Long attemptId, SubmitPracticeRequest req);

    // ✅ NEW
    AttemptReviewResponse getReview(String email, Long attemptId);
}
