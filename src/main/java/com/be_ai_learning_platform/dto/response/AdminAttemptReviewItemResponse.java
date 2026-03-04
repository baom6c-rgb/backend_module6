package com.be_ai_learning_platform.dto.response;

import lombok.Data;

import java.util.Map;

@Data
public class AdminAttemptReviewItemResponse {
    private Long questionId;
    private String questionType; // "MCQ" / "ESSAY" (string cho FE dễ)
    private String content;

    private String optionsJson; // giữ y như DB (FE parseOptions đang dùng)
    private String correctAnswer;

    private String selectedAnswer;
    private boolean isCorrect;

    private String explanation; // dùng analysis
}