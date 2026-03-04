package com.be_ai_learning_platform.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public class StudentStartAssignedExamResponse {
    private Long assignmentId;
    private Long attemptId;
    private Long examId;
    private String examTitle;
    private Integer durationMinutes;
    private Integer passScore;

    private LocalDateTime startTime;
    private LocalDateTime deadline;

    private List<AttemptQuestionResponse> questions;

    public Long getAssignmentId() { return assignmentId; }
    public void setAssignmentId(Long assignmentId) { this.assignmentId = assignmentId; }

    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }

    public Long getExamId() { return examId; }
    public void setExamId(Long examId) { this.examId = examId; }

    public String getExamTitle() { return examTitle; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public Integer getPassScore() { return passScore; }
    public void setPassScore(Integer passScore) { this.passScore = passScore; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public List<AttemptQuestionResponse> getQuestions() { return questions; }
    public void setQuestions(List<AttemptQuestionResponse> questions) { this.questions = questions; }
}
