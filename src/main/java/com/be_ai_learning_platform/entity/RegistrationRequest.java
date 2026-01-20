package com.be_ai_learning_platform.entity;


import com.be_ai_learning_platform.entity.enums.RequestStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "registration_request")
public class RegistrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    private String studentCode;

    @Column(columnDefinition = "TEXT")
    private String noteFromStudent;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    @ManyToOne
    @JoinColumn(name = "approved_by_admin_id")
    private User approvedBy;

    private LocalDateTime approvedAt;
    private LocalDateTime createdAt = LocalDateTime.now();

    // getter / setter
}
