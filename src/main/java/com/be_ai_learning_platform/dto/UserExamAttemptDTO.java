// UserExamAttemptDTO.java
package com.be_ai_learning_platform.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class UserExamAttemptDTO {
    private Long id;
    private String name;        // Tên bài thi (thường lấy từ Exam hoặc Module)
    private String module;      // Tên Module học tập
    private String className;   // Tên lớp học (nếu có)
    private LocalDateTime date; // Ngày submitTime
    private Integer score;
    private Integer totalScore; // Tổng điểm tối đa
    private Integer duration;   // Thời gian làm bài (phút)
    private Integer questions;  // Tổng số câu hỏi
    private Integer correctAnswers; // Số câu đúng
}