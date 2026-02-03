package com.be_ai_learning_platform.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class AdminAnalyticsOverviewResponse {

    private Long totalAttempts;
    private Long totalStudents;
    private Double avgScore;
    private Double passRate; // 0..1
    private Double failRate; // 0..1

    private List<AtRiskStudentResponse> atRiskStudents; // top 20
    private List<TimeSeriesPointResponse> timeSeries;   // theo ngày
}
