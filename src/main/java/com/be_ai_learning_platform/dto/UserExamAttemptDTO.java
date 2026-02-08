// src/main/java/com/be_ai_learning_platform/dto/UserExamAttemptDTO.java
package com.be_ai_learning_platform.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserExamAttemptDTO {
    private Long id;

    private String studentName;   // Tên học viên
    private String studentEmail;  // Email để phân biệt nếu trùng tên

    /**
     * Backward compatible: FE cũ đang đọc field "name"
     * => sẽ set = examTitle (AI đặt tên) để không còn "Bài thi PRACTICE".
     */
    private String name;

    /**
     * Field mới chuẩn: tên bài thi AI đặt (Exam.title)
     * FE nên ưu tiên đọc field này.
     */
    private String examTitle;

    private String module;        // Tên Module học tập
    private String className;     // Tên lớp học (nếu có)

    private LocalDateTime date;   // Ngày submitTime
    private Integer score;
    private Integer totalScore;   // Tổng điểm tối đa
    private Integer duration;     // Thời gian làm bài (phút)
    private Integer questions;    // Tổng số câu hỏi
    private Integer correctAnswers; // Số câu đúng
}
