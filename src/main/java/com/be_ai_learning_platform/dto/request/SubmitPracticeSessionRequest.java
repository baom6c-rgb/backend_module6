package com.be_ai_learning_platform.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class SubmitPracticeSessionRequest {

    @NotNull
    @Valid
    private List<AnswerItem> answers;

    public List<AnswerItem> getAnswers() { return answers; }
    public void setAnswers(List<AnswerItem> answers) { this.answers = answers; }

    public static class AnswerItem {

        @NotBlank
        private String questionKey;

        // MCQ: A/B/C/D
        private String selectedAnswer;

        // ESSAY: free text
        private String textAnswer;

        public String getQuestionKey() { return questionKey; }
        public void setQuestionKey(String questionKey) { this.questionKey = questionKey; }

        public String getSelectedAnswer() { return selectedAnswer; }
        public void setSelectedAnswer(String selectedAnswer) { this.selectedAnswer = selectedAnswer; }

        public String getTextAnswer() { return textAnswer; }
        public void setTextAnswer(String textAnswer) { this.textAnswer = textAnswer; }
    }
}
