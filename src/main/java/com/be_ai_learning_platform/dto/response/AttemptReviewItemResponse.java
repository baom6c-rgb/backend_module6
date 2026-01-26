package com.be_ai_learning_platform.dto.response;

import java.util.Map;

public class AttemptReviewItemResponse {
    private Long questionId;
    private String content;
    private Map<String, String> options;

    private String correctAnswer;   // A/B/C/D
    private String selectedAnswer;  // A/B/C/D hoặc null/blank
    private Boolean isCorrect;

    private String explanation;     // optional (lấy từ Question.analysis nếu có)

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Map<String, String> getOptions() { return options; }
    public void setOptions(Map<String, String> options) { this.options = options; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public String getSelectedAnswer() { return selectedAnswer; }
    public void setSelectedAnswer(String selectedAnswer) { this.selectedAnswer = selectedAnswer; }

    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean correct) { isCorrect = correct; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
