package com.be_ai_learning_platform.dto.response;

import java.util.List;

public class AdminExamPreviewResponse {
    private String previewToken;
    private String suggestedTitle;
    private Integer totalQuestions;
    private List<GeneratedQuestionItemResponse> questions;

    public String getPreviewToken() { return previewToken; }
    public void setPreviewToken(String previewToken) { this.previewToken = previewToken; }

    public String getSuggestedTitle() { return suggestedTitle; }
    public void setSuggestedTitle(String suggestedTitle) { this.suggestedTitle = suggestedTitle; }

    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }

    public List<GeneratedQuestionItemResponse> getQuestions() { return questions; }
    public void setQuestions(List<GeneratedQuestionItemResponse> questions) { this.questions = questions; }
}
