package com.be_ai_learning_platform.entity;

import com.be_ai_learning_platform.entity.enums.ExamResult;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam_attempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    private LocalDateTime startTime;
    private LocalDateTime submitTime;
    private Integer score;

    @Enumerated(EnumType.STRING)
    private ExamResult status;
}
