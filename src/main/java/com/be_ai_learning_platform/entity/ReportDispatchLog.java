package com.be_ai_learning_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "report_dispatch_log",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_report_type_run_key", columnNames = {"reportType", "runKey"})
        }
)
public class ReportDispatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reportType;

    @Column(length = 120, nullable = false)
    private String runKey;

    private LocalDateTime sentAt;
}
