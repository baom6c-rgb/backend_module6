package com.be_ai_learning_platform.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SystemSettingsResponse {

    private Integer passScore;
    private Double minutesPerQuestion;

    private Boolean emailNotificationsEnabled;
    private String adminEmails;

    private LocalDateTime updatedAt;
}
