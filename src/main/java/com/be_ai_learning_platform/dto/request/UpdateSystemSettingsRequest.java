package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateSystemSettingsRequest {

    @NotNull
    @Min(0)
    @Max(100)
    private Integer passScore;

    @NotNull
    @Min(0)
    private Double minutesPerQuestion;

    @NotNull
    @Min(0)
    @Max(1440)
    private Integer retestCooldownMinutes; // minutes

    @NotNull
    private Boolean emailNotificationsEnabled;

    @NotNull
    private String adminEmails; // CSV: a@x.com,b@y.com
}
