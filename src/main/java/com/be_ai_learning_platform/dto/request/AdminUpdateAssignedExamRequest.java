package com.be_ai_learning_platform.dto.request;

import java.time.LocalDateTime;
import java.util.List;

public class AdminUpdateAssignedExamRequest {

    private String title;
    private Integer durationMinutes;

    private LocalDateTime openAt;
    private LocalDateTime dueAt;

    private Integer durationMinutesOverride;

    // nếu null -> không đổi danh sách
    private List<Long> assignedUserIds;

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
