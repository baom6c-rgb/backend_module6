package com.be_ai_learning_platform.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminAssignmentReviewResponse {
    private Long examId;
    private Long assignmentId;
    private Long attemptId;

    private Long studentId;
    private String studentFullName;
    private String studentEmail;

    private Integer scorePct;          // 0..100 (hoặc null nếu chưa chấm)
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;

    private List<AdminAttemptReviewItemResponse> items;
}