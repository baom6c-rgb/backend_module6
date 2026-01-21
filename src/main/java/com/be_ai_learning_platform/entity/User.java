package com.be_ai_learning_platform.entity;

import com.be_ai_learning_platform.entity.enums.LoginProvider;
import com.be_ai_learning_platform.entity.enums.RegisterMethod;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "user",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"login_provider", "provider_id"})
        }
)
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash")
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
    private LearningModule currentModule;

    // 🔥 QUAN HỆ ROLE (BẮT BUỘC)
    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
    private List<UserRole> userRoles;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;

    private Boolean isDeleted = false;
}
