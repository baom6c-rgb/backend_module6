package com.be_ai_learning_platform.entity;

import com.be_ai_learning_platform.entity.enums.ExamType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam")
@Getter // Thêm dòng này
@Setter // Thêm dòng này
@NoArgsConstructor
@AllArgsConstructor
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

