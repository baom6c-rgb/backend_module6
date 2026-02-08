package com.be_ai_learning_platform.dto.response;

public class StartPracticeResponse {
    private Long attemptId;

    // ✅ NEW
    private Long examId;
    private String examTitle;

    public StartPracticeResponse() {}

    public StartPracticeResponse(Long attemptId) {
        this.attemptId = attemptId;
    }

    // (optional) constructor tiện
    public StartPracticeResponse(Long attemptId, Long examId, String examTitle) {
        this.attemptId = attemptId;
        this.examId = examId;
        this.examTitle = examTitle;
    }

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }

    public Long getExamId() { return examId; }
    public void setExamId(Long examId) { this.examId = examId; }

    public String getExamTitle() { return examTitle; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }
}
