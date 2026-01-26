package com.be_ai_learning_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "module")
@Getter
@Setter
public class LearningModule  {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "module_number", nullable = false)
    private Integer moduleNumber;

    private String moduleName;

    @Column(columnDefinition = "TEXT")
    private String description;

    // getter / setter
}