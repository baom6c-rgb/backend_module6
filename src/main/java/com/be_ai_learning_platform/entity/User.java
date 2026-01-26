package com.be_ai_learning_platform.entity;

import com.be_ai_learning_platform.entity.enums.LoginProvider;
import com.be_ai_learning_platform.entity.enums.RegisterMethod;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
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

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "approve_token", length = 100)
    private String approveToken;

    private String fullName;
    private String avatarUrl;

    // ✅ NEW (optional)
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    // ✅ NEW (optional)
    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "reset_password_token")
    private String resetPasswordToken;

    @Column(name = "token_expiry_date")
    private LocalDateTime tokenExpiryDate;

    @Enumerated(EnumType.STRING)
    private RegisterMethod registerMethod;

    @Enumerated(EnumType.STRING)
    private LoginProvider loginProvider;

    private String providerId;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @ManyToOne
    @JoinColumn(name = "class_id")
    private ClassEntity className;

    @ManyToOne
    @JoinColumn(name = "current_module_id")
    private LearningModule learningModule;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserRole> userRoles = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;

    private Boolean isDeleted = false;
}
