package com.be_ai_learning_platform.dto.response;

import java.util.List;

public class GenerateQuestionsResponse {
    private Long materialId;
    private Integer numberOfQuestions;
    private List<GeneratedQuestionItemResponse> questions;

    public GenerateQuestionsResponse() {}

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }

    public Integer getNumberOfQuestions() { return numberOfQuestions; }
    public void setNumberOfQuestions(Integer numberOfQuestions) { this.numberOfQuestions = numberOfQuestions; }

    public List<GeneratedQuestionItemResponse> getQuestions() { return questions; }
    public void setQuestions(List<GeneratedQuestionItemResponse> questions) { this.questions = questions; }
}
