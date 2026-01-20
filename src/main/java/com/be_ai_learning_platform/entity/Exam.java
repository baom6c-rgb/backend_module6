package com.be_ai_learning_platform.entity;

import com.be_ai_learning_platform.entity.enums.ExamType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exam")
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private ExamType type;

    private Integer durationMinutes;
    private Integer passScore = 50;

    private LocalDateTime createdAt = LocalDateTime.now();
}

