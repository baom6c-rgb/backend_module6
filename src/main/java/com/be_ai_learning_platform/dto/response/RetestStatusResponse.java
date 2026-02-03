package com.be_ai_learning_platform.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RetestStatusResponse {
    private Long attemptId;
    private Boolean showRetest;
    private Boolean canRetestNow;

    private Integer cooldownMinutes;
    private LocalDateTime availableAt;
    private Long remainingSeconds;
}
