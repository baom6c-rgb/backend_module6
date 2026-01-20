package com.be_ai_learning_platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "answer")
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    private String label; // A, B, C, D

    @Column(columnDefinition = "TEXT")
    private String content;

    private Boolean isCorrect = false;
}

