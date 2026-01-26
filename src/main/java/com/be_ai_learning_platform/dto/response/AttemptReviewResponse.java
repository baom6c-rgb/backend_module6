package com.be_ai_learning_platform.dto.response;

import java.util.List;

public class AttemptReviewResponse {
    private Long attemptId;
    private Integer score;          // 0-100
    private Integer totalQuestions;
    private Integer correctCount;

    private List<AttemptReviewItemResponse> items;

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }

    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }

    public List<AttemptReviewItemResponse> getItems() { return items; }
    public void setItems(List<AttemptReviewItemResponse> items) { this.items = items; }
}
