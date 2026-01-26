package com.be_ai_learning_platform.dto.response;

import java.util.Map;

public class AttemptQuestionResponse {
    private Long questionId;
    private String content;
    private Map<String, String> options; // A-D

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Map<String, String> getOptions() { return options; }
    public void setOptions(Map<String, String> options) { this.options = options; }
}
