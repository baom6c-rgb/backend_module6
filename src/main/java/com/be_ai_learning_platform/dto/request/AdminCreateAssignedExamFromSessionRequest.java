package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin create an assigned exam from a Practice V2 session token.
 * This reuses the same V2 generate/select-topic/start flow as Practice.
 */
public class AdminCreateAssignedExamFromSessionRequest {

    @NotBlank
    private String sessionToken;

    private String title;

    // window time (optional)
    private LocalDateTime openAt;
    private LocalDateTime dueAt;

    // override duration for assignment (optional). If null -> use exam.durationMinutes
    private Integer durationMinutesOverride;

    private Boolean sendEmail = Boolean.TRUE;

    @NotEmpty
    private List<Long> assignedUserIds;

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getOpenAt() {
        return openAt;
    }

    public void setOpenAt(LocalDateTime openAt) {
        this.openAt = openAt;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public Integer getDurationMinutesOverride() {
        return durationMinutesOverride;
    }

    public void setDurationMinutesOverride(Integer durationMinutesOverride) {
        this.durationMinutesOverride = durationMinutesOverride;
    }

    public Boolean getSendEmail() {
        return sendEmail;
    }

    public void setSendEmail(Boolean sendEmail) {
        this.sendEmail = sendEmail;
    }

    public List<Long> getAssignedUserIds() {
        return assignedUserIds;
    }

    public void setAssignedUserIds(List<Long> assignedUserIds) {
        this.assignedUserIds = assignedUserIds;
    }
}
