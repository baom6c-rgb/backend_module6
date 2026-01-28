package com.be_ai_learning_platform.dto.response;

import com.be_ai_learning_platform.entity.enums.QuestionType;

import java.util.Map;

public class AttemptQuestionResponse {
    private Long questionId;
    private QuestionType questionType; // MCQ | ESSAY
    private String content;
    private Map<String, String> options; // MCQ only

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public QuestionType getQuestionType() { return questionType; }
    public void setQuestionType(QuestionType questionType) { this.questionType = questionType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Map<String, String> getOptions() { return options; }
    public void setOptions(Map<String, String> options) { this.options = options; }
}
