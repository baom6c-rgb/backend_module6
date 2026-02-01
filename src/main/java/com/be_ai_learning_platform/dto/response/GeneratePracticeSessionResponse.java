package com.be_ai_learning_platform.dto.response;

import lombok.Data;

@Data
public class GeneratePracticeSessionResponse {
    private String sessionToken;
    private Long materialId;
    private Integer numberOfQuestions;
    private Integer durationMinutes;
}
