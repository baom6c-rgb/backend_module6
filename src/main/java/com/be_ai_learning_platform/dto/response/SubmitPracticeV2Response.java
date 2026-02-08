package com.be_ai_learning_platform.dto.response;

import com.be_ai_learning_platform.entity.enums.ExamResult;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubmitPracticeV2Response {
    private Long attemptId;

    // ✅ NEW
    private Long examId;
    private String examTitle;

    private Integer score;
    private Integer earnedPoints;
    private Integer totalPoints;
    private ExamResult status;

    // ===== Retest =====
    private Boolean showRetest;
    private Boolean canRetestNow;
    private Integer retestCooldownMinutes;
    private LocalDateTime retestAvailableAt;
    private Long retestRemainingSeconds;

    private Boolean timedOut;
    private String feedback;
    private String aiFeedback;
}
