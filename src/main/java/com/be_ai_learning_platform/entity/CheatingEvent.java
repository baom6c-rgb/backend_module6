package com.be_ai_learning_platform.entity;

import com.be_ai_learning_platform.entity.enums.CheatingEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "cheating_event",
        indexes = {
                @Index(name = "idx_cheating_event_assignment", columnList = "assignment_id"),
                @Index(name = "idx_cheating_event_student", columnList = "student_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheatingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private ExamAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheatingEventType type;

    @Lob
    @Column(name = "meta_json", columnDefinition = "LONGTEXT")
    private String metaJson;

    private LocalDateTime createdAt = LocalDateTime.now();
}
