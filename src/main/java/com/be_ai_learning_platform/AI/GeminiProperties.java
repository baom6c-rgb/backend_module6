package com.be_ai_learning_platform.AI;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Gemini runtime config (NO API KEY here).
 * API key is managed in DB: SystemSettings (Admin > Settings > Model AI)
 */
@Validated
@ConfigurationProperties(prefix = "ai.gemini")
public record GeminiProperties(
        @NotBlank String baseUrl,
        String model
) {}
