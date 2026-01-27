package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PracticeGenerateRequest {

    @NotNull
    private Long materialId;

    @NotNull
    @Min(1)
    @Max(30)
    private Integer numberOfQuestions;

    // optional: thời lượng
    private Integer durationMinutes;

    // optional: token lấy từ generatePreview để start không gọi AI lại
    private String previewToken;
}
