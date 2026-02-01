package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GeneratePracticeSessionRequest {

    @NotNull
    private Long materialId;

    @NotNull
    @Min(1)
    @Max(30)
    private Integer numberOfQuestions;
}
