package com.be_ai_learning_platform.dto.response;

import java.util.List;

public class AttemptDetailResponse {
    private Long attemptId;
    private Long examId;

    // ✅ NEW
    private String examTitle;

    private Integer durationMinutes;
    private List<AttemptQuestionResponse> questions;

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }

    public Long getExamId() { return examId; }
    public void setExamId(Long examId) { this.examId = examId; }

    public String getExamTitle() { return examTitle; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public List<AttemptQuestionResponse> getQuestions() { return questions; }
    public void setQuestions(List<AttemptQuestionResponse> questions) { this.questions = questions; }
}
