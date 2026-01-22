package com.be_ai_learning_platform.entity;

import com.be_ai_learning_platform.entity.enums.FileType;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
    @Data
    @Entity
    @Table(name = "learning_material")
    public class LearningMaterial {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne
        @JoinColumn(name = "user_id", nullable = false)
        private User user;

        private String fileName;

        @Enumerated(EnumType.STRING)
        private FileType fileType;

        private Long fileSize;

        @Lob
        private String extractedText;

        @Enumerated(EnumType.STRING)
        private MaterialStatus status;

        private LocalDateTime createdAt = LocalDateTime.now();
    }


