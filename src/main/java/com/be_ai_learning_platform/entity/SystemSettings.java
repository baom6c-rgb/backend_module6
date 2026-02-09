package com.be_ai_learning_platform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "system_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSettings {

    @Id
    private Long id;

    // ===== Base settings =====
    @Column(nullable = false)
    private Integer passScore;

    @Column(nullable = false)
    private Double minutesPerQuestion;

    @Column(nullable = false)
    private Integer retestCooldownMinutes;

    @Column(nullable = false)
    private Boolean emailNotificationsEnabled;

    /**
     * CSV: a@x.com,b@y.com
     */
    @Column(columnDefinition = "TEXT")
    private String adminEmails;

    // ===== Monthly Admin Report =====
    @Column(nullable = false)
    private Boolean monthlyReportEnabled = false;

    /**
     * 1..31, 0 = last day
     */
    @Column(nullable = false)
    private Integer monthlyReportDayOfMonth = 0;

    /**
     * HH:mm
     */
    @Column(nullable = false)
    private LocalTime monthlyReportTime = LocalTime.of(23, 59);

    /**
     * IANA timezone, e.g. Asia/Ho_Chi_Minh
     */
    @Column(nullable = false)
    private String monthlyReportTimeZone = "Asia/Ho_Chi_Minh";

    /**
     * YYYY-MM đã gửi gần nhất (để chống gửi trùng)
     */
    private String monthlyReportLastSentYearMonth;

    private LocalDateTime updatedAt;
}
