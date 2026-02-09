package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Admin updates for AI settings.
 *
 * NOTE:
 * - aiApiKey is optional. If blank => keep current key.
 */
public record UpdateAiSettingsRequest(
        @NotBlank String aiProvider,   // GEMINI (future: OPENAI, CLAUDE...)
        String aiApiKey,               // optional
        @NotBlank String aiModel,
        @NotNull @Min(0) @Max(1) Double aiTemperature,
        @NotNull Boolean aiEnabled
) {}
