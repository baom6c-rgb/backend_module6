package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class GenerateQuestionsRequest {
    @NotNull
    private Long materialId;

    @NotNull
    @Min(1) @Max(30) // tạm giới hạn để đỡ tốn token
    private Integer numberOfQuestions;

    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }

    public Integer getNumberOfQuestions() { return numberOfQuestions; }
    public void setNumberOfQuestions(Integer numberOfQuestions) { this.numberOfQuestions = numberOfQuestions; }
}
