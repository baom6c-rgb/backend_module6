package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StartPracticeSessionRequest {

    @NotBlank
    private String sessionToken;
}
