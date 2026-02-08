package com.be_ai_learning_platform.entity;

import com.be_ai_learning_platform.entity.enums.ExamType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam")
@Data
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

    // ✅ NEW: AI đặt tên bài
    @Column(length = 120)
    private String title;

    private Integer durationMinutes;
    private Integer passScore = 75;

    private LocalDateTime createdAt = LocalDateTime.now();
}
