package com.be_ai_learning_platform.dto.response;

import lombok.Data;

@Data
public class AtRiskStudentResponse {
    private Long userId;
    private String fullName;
    private String email;

    private Long attemptsCount;
    private Double avgScore;
    private Double failRate; // 0..1
    private String lastAttemptAt; // ISO datetime

    private String riskLevel; // LOW/MEDIUM/HIGH
    private Integer riskScore; // 0..100
    private String[] reasons;
}
