package com.be_ai_learning_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "role")
@Getter
@Setter
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name; // ADMIN, STUDENT

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // getter / setter
}
