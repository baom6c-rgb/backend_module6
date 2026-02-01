package com.be_ai_learning_platform.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class StartPracticeSessionResponse {
    private String sessionToken;
    private Integer durationMinutes;
    private LocalDateTime startedAt;
    private LocalDateTime deadline;
    private List<PracticeQuestionV2Response> questions;
}
