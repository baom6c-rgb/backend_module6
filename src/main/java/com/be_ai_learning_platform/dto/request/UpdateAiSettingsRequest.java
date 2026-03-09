package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Admin updates for AI settings.
 *
 * NOTE:
 * - aiApiKey is optional. If blank => keep current key.
 */
public record UpdateAiSettingsRequest(
        @NotBlank String aiProvider,
        String aiApiKey,
        @NotBlank String aiModel,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double aiTemperature,
        @NotNull Boolean aiEnabled
) {}