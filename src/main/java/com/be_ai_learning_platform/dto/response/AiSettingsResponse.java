package com.be_ai_learning_platform.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AiSettingsResponse {

    private String aiProvider;

    /**
     * Masked key (e.g. abc****xyz). Never return full secret.
     */
    private String aiApiKeyMasked;

    private String aiModel;
    private Double aiTemperature;
    private Boolean aiEnabled;

    private LocalDateTime updatedAt;
}
