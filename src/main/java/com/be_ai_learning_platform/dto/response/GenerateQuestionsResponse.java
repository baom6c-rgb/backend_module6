package com.be_ai_learning_platform.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class GenerateQuestionsResponse {

    private Long materialId;
    private Integer numberOfQuestions;

    // token trả về từ preview
    private String previewToken;

    private List<GeneratedQuestionItemResponse> questions;

    public GenerateQuestionsResponse() {}
}
