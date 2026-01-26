package com.be_ai_learning_platform.dto.response;

public class SubmitPracticeResponse {
    private Integer score;
    private String feedback;

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
}
