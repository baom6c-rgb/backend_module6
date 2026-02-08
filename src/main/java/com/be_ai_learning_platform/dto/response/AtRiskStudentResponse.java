// AtRiskStudentResponse.java
package com.be_ai_learning_platform.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class AtRiskStudentResponse {
    private Long userId;
    private String fullName;
    private String email;

    private Long attemptsCount;
    private Double avgScore;
    private Double failRate; // 0..1
    /**
     * Tiến độ (FE hiển thị progress bar): % đạt của học viên theo các bài đã làm.
     * passRate = 1 - failRate (0..1)
     */
    private Double passRate; // 0..1
    private String lastAttemptAt; // ISO datetime

    private String riskLevel; // LOW/MEDIUM/HIGH (FE map -> Yếu/Trung bình/Tốt)
    private Integer riskScore; // 0..100
    private String[] reasons;

    // ===== AI fields (optional) =====
    private String insightSummary; // 1-2 câu
    private String[] weakTopics; // 2-5 items
    private String[] recommendedNextSteps; // 3-6 items

    // ✅ NEW: AI strengths (optional)
    private List<String> strengths;
}
