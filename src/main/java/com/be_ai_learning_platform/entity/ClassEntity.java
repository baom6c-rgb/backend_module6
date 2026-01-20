package com.be_ai_learning_platform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "class")
public class ClassEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_name", nullable = false, length = 100)
    private String className;

    @Column(columnDefinition = "TEXT")
    private String description;

    // getter / setter
}