package com.be_ai_learning_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttemptQuestionDTO {
    private Long questionId;
    private String questionText;
    private String questionType; // "MULTIPLE_CHOICE", "TRUE_FALSE", etc.
    private List<String> options; // Các lựa chọn
    private String userAnswer; // Câu trả lời của user
    private String correctAnswer; // Đáp án đúng
    private Boolean isCorrect; // Trả lời đúng hay sai
    private Integer points; // Điểm của câu hỏi này
}