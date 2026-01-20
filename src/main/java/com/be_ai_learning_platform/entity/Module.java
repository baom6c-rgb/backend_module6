package com.be_ai_learning_platform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "module")
public class Module {

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