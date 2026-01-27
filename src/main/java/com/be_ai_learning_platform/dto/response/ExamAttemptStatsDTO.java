package com.be_ai_learning_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamAttemptStatsDTO {
    private Integer totalTests; // Tổng số bài test đã làm
    private Double averageScore; // Điểm trung bình
    private Integer passedTests; // Số bài đạt yêu cầu (>= 75)
    private Integer totalQuestions; // Tổng số câu hỏi
    private Integer totalCorrectAnswers; // Tổng số câu trả lời đúng
    private Double accuracyRate; // Tỷ lệ đúng (%)
    private Integer rank; // Xếp hạng (có thể tính sau)
    private Integer totalStudents; // Tổng số học viên
}