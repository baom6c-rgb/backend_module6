package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.response.AttemptReviewItemResponse;

import java.util.List;

public interface AiPracticeFeedbackService {

    /**
     * Generate AI feedback "Điểm mạnh/Điểm yếu" theo nhóm kiến thức.
     * Service phải tự: build prompt -> call Gemini -> format -> guard -> fallback.
     */
    String generateFeedback(String userFullName, Integer scorePct, List<AttemptReviewItemResponse> items);
}