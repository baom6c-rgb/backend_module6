package com.be_ai_learning_platform.entity;

import com.be_ai_learning_platform.entity.enums.LoginProvider;
import com.be_ai_learning_platform.entity.enums.RegisterMethod;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "user",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"login_provider", "provider_id"})
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String fullName;
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    private RegisterMethod registerMethod;

    @Enumerated(EnumType.STRING)
    private LoginProvider loginProvider;

    private String providerId;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @ManyToOne
    @JoinColumn(name = "class_id")
    private ClassEntity clazz;

    @ManyToOne
    @JoinColumn(name = "current_module_id")
    private Module currentModule;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;

    private Boolean isDeleted = false;

    // getter / setter
}