package com.be_ai_learning_platform.entity;

import com.be_ai_learning_platform.entity.enums.QuestionType;
import jakarta.persistence.*;
import lombok.Data;


@Data
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "material_id", nullable = false)
    private LearningMaterial material;

    @Enumerated(EnumType.STRING)
    private QuestionType questionType;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Integer difficulty;
    private String correctAnswer;

    @Column(columnDefinition = "TEXT")
    private String analysis;

    @Lob
    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;
}
