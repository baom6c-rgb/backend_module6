package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.List;

public class AdminCreateAssignedExamRequest {

    @NotBlank
    private String previewToken;

    private String title;

    // nếu null -> auto compute (minutesPerQuestion * totalQuestions)
    private Integer durationMinutes;

    // window time (optional)
    private LocalDateTime openAt;
    private LocalDateTime dueAt;

    // override duration cho toàn bộ assignment (optional)
    private Integer durationMinutesOverride;

    @NotEmpty
    private List<Long> assignedUserIds;

    public String getPreviewToken() { return previewToken; }
    public void setPreviewToken(String previewToken) { this.previewToken = previewToken; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public LocalDateTime getOpenAt() { return openAt; }
    public void setOpenAt(LocalDateTime openAt) { this.openAt = openAt; }

    public LocalDateTime getDueAt() { return dueAt; }
    public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }

    public Integer getDurationMinutesOverride() { return durationMinutesOverride; }
    public void setDurationMinutesOverride(Integer durationMinutesOverride) { this.durationMinutesOverride = durationMinutesOverride; }

    public List<Long> getAssignedUserIds() { return assignedUserIds; }
    public void setAssignedUserIds(List<Long> assignedUserIds) { this.assignedUserIds = assignedUserIds; }
}
