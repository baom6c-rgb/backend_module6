package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.*;
import com.be_ai_learning_platform.dto.response.*;

public interface PracticeService {
    GenerateQuestionsResponse generatePreview(String email, PracticeGenerateRequest req);
    StartPracticeResponse start(String email, PracticeGenerateRequest req);
    AttemptDetailResponse getAttempt(String email, Long attemptId);
    SubmitPracticeResponse submit(String email, Long attemptId, SubmitPracticeRequest req);

    // ✅ NEW
    AttemptReviewResponse getReview(String email, Long attemptId);

    // =========================
    // V2 (no preview, no DB until submit)
    // =========================
    GeneratePracticeSessionResponse generateSessionV2(String email, GeneratePracticeSessionRequest req);

    /**
     * V2 topic selection: when material contains multiple lessons/topics,
     * BE asks user to pick one topicId and then generates a focused session.
     */
    GeneratePracticeSessionResponse selectTopicAndGenerateSessionV2(String email, SelectTopicRequest req);

    StartPracticeSessionResponse startSessionV2(String email, StartPracticeSessionRequest req);
    StartPracticeSessionResponse getSessionV2(String email, String sessionToken);
    SubmitPracticeV2Response submitSessionV2(String email, String sessionToken, SubmitPracticeSessionRequest req);

    // =========================
    // V2 Admin create assigned exam (persist exam/questions only)
    // =========================
    Long createExamFromSessionV2(String email, String sessionToken);

    // =========================
    // V2 Retest
    // =========================
    RetestStatusResponse getRetestStatusV2(String email, Long attemptId);
    StartPracticeSessionResponse startRetestV2(String email, Long attemptId);

    String getOrGenerateStudyGuide(String name, Long attemptId);
}
