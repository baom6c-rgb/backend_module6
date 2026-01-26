package com.be_ai_learning_platform.dto.response;

import java.util.Map;

public class GeneratedQuestionItemResponse {
    private String question;
    private Map<String, String> options; // A,B,C,D
    private String correctAnswer; // "A"|"B"|"C"|"D"

    public GeneratedQuestionItemResponse() {}

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public Map<String, String> getOptions() { return options; }
    public void setOptions(Map<String, String> options) { this.options = options; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
}
