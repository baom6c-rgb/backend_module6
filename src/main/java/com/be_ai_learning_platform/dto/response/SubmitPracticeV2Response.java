package com.be_ai_learning_platform.dto.response;

import com.be_ai_learning_platform.entity.enums.ExamResult;
import lombok.Data;

@Data
public class SubmitPracticeV2Response {
    private Long attemptId;
    private Integer score;
    private Integer earnedPoints;
    private Integer totalPoints;
    private ExamResult status;
    private Boolean timedOut;
    private String feedback;
    private String aiFeedback;
}
