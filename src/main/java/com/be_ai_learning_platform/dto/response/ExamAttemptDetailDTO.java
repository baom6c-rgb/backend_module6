package com.be_ai_learning_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamAttemptDetailDTO {
    private Long id;
    private Long examId;
    private String examName;
    private String module;
    private String className;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalQuestions;
    private Integer correctAnswers;
    private Integer score;
    private Integer totalScore;
    private Integer duration; // Thời gian làm bài thực tế (phút)
    private String status; // "Giỏi", "Khá", "Cần cải thiện"
    private List<AttemptQuestionDTO> questions; // Danh sách câu hỏi và câu trả lời
}