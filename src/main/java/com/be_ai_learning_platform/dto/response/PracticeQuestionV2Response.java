package com.be_ai_learning_platform.dto.response;

import com.be_ai_learning_platform.entity.enums.QuestionType;

import java.util.Map;

public class PracticeQuestionV2Response {
    private String questionKey;
    private QuestionType questionType;
    private String content;
    private Map<String, String> options; // MCQ only

    public String getQuestionKey() { return questionKey; }
    public void setQuestionKey(String questionKey) { this.questionKey = questionKey; }

    public QuestionType getQuestionType() { return questionType; }
    public void setQuestionType(QuestionType questionType) { this.questionType = questionType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Map<String, String> getOptions() { return options; }
    public void setOptions(Map<String, String> options) { this.options = options; }
}
