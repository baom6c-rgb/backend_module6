package com.be_ai_learning_platform.dto.response;

import com.be_ai_learning_platform.entity.enums.QuestionType;

import java.util.List;
import java.util.Map;

public class GeneratedQuestionItemResponse {

    private QuestionType questionType; // MCQ | ESSAY

    // common
    private String question;

    // MCQ only
    private Map<String, String> options; // A,B,C,D
    private String correctAnswer; // "A"|"B"|"C"|"D"
    private String analysis; // optional explanation

    // ESSAY only
    private String sampleAnswer;
    private List<String> keywords;
    private Integer maxScore;

    public GeneratedQuestionItemResponse() {}

    public QuestionType getQuestionType() { return questionType; }
    public void setQuestionType(QuestionType questionType) { this.questionType = questionType; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public Map<String, String> getOptions() { return options; }
    public void setOptions(Map<String, String> options) { this.options = options; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }

    public String getSampleAnswer() { return sampleAnswer; }
    public void setSampleAnswer(String sampleAnswer) { this.sampleAnswer = sampleAnswer; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public Integer getMaxScore() { return maxScore; }
    public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }
}
