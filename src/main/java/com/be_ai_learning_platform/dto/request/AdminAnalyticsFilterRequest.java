package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminAnalyticsFilterRequest {

    private Long classId;   // optional
    private Long moduleId;  // optional

    /**
     * ISO datetime string, ví dụ: "2026-02-01T00:00:00"
     * Nếu null -> không filter
     */
    private String from; // optional
    private String to;   // optional

    private String keyword; // optional (search by email/fullName)

    @Min(0)
    private Integer scoreMin; // optional

    @Min(0)
    private Integer scoreMax; // optional
}
