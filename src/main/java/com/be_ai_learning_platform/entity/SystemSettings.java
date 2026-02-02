package com.be_ai_learning_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_settings")
@Getter
@Setter
public class SystemSettings {

    @Id
    private Long id = 1L; // singleton row

    // ===== PRACTICE =====
    @Column(nullable = false)
    private Integer passScore; // ví dụ: 80

    @Column(nullable = false)
    private Double minutesPerQuestion; // ví dụ: 2.3

    // ===== EMAIL =====
    @Column(nullable = false)
    private Boolean emailNotificationsEnabled;

    @Column(nullable = false)
    private String adminEmails; // CSV: a@x.com,b@y.com

    // ===== AUDIT =====
    private LocalDateTime updatedAt;
}
