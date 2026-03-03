package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.NotNull;

public class GenerateAssignedExamSessionRequest {

    @NotNull
    private Long assignmentId;

    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }
}
