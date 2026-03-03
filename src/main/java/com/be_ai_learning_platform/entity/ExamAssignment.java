package com.be_ai_learning_platform.entity;

import com.be_ai_learning_platform.entity.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "exam_assignment",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_exam_assignment_exam_student", columnNames = {"exam_id", "student_id"})
        },
        indexes = {
                @Index(name = "idx_exam_assignment_student", columnList = "student_id"),
                @Index(name = "idx_exam_assignment_exam", columnList = "exam_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExamAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_id", nullable = false)
    private User assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    // ✅ Assign theo thời gian
    private LocalDateTime openAt;
    private LocalDateTime dueAt;

    // ✅ Override thời gian làm bài cho riêng assignment (không đụng Exam.durationMinutes)
    private Integer durationMinutesOverride;

    @Enumerated(EnumType.STRING)
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    private ExamAttempt attempt;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
