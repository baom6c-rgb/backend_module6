package com.be_ai_learning_platform.dto.response;

import lombok.Data;

@Data
public class TimeSeriesPointResponse {
    private String date; // yyyy-MM-dd
    private Long attempts;
    private Double avgScore;
    private Double failRate; // 0..1
}
