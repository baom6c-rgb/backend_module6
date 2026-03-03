package com.be_ai_learning_platform.dto.response;

import com.be_ai_learning_platform.entity.enums.ExamType;

import java.time.LocalDateTime;
import java.util.List;

public class AdminExamDetailResponse {
    private Long examId;
    private ExamType type;
    private String title;
    private Integer durationMinutes;
    private Integer passScore;
    private LocalDateTime createdAt;

    private List<QuestionDetailResponse> questions;
    private List<AdminAssignmentItemResponse> assignments;

    public Long getExamId() { return examId; }
    public void setExamId(Long examId) { this.examId = examId; }

    public ExamType getType() { return type; }
    public void setType(ExamType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public Integer getPassScore() { return passScore; }
    public void setPassScore(Integer passScore) { this.passScore = passScore; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<QuestionDetailResponse> getQuestions() { return questions; }
    public void setQuestions(List<QuestionDetailResponse> questions) { this.questions = questions; }

    public List<AdminAssignmentItemResponse> getAssignments() { return assignments; }
    public void setAssignments(List<AdminAssignmentItemResponse> assignments) { this.assignments = assignments; }
}
