package com.be_ai_learning_platform.ai;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ai.gemini")
public record GeminiProperties(
        String baseUrl,
        String model,
        String apiKey
) {}

