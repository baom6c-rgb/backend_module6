package com.be_ai_learning_platform.entity;

import com.be_ai_learning_platform.entity.enums.ExamResult;
import jakarta.persistence.*;
import lombok.*;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private ClassEntity classroom; // Đổi tên để tránh trùng với phương thức getClass() của Java

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    private LearningModule learningModule;
    private LocalDateTime startTime;
    private LocalDateTime submitTime;
    private Integer score;

    @Enumerated(EnumType.STRING)
    private ExamResult status;
    // ✅ NEW: lưu đáp án user chọn để review
    @Lob
    @Column(name = "answers_json", columnDefinition = "LONGTEXT")
    private String answersJson;
}
