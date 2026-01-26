package com.be_ai_learning_platform.dto.response;

public class StartPracticeResponse {
    private Long attemptId;

    public StartPracticeResponse() {}
    public StartPracticeResponse(Long attemptId) { this.attemptId = attemptId; }

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }
}
