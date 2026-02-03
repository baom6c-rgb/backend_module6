package com.be_ai_learning_platform.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class AdminAnalyticsAiInsightResponse {
    private String summary;
    private List<String> keyProblems;
    private List<String> atRiskPatterns;
    private List<String> recommendedActions;
    private String confidence; // LOW | MEDIUM | HIGH
    private String generatedAt; // ISO datetime
}
