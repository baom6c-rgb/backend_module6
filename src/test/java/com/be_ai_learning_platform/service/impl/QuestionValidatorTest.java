package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;
import com.be_ai_learning_platform.dto.response.GeneratedQuestionItemResponse;
import com.be_ai_learning_platform.entity.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionValidatorTest {

    @Test
    @DisplayName("Ném lỗi khi response bị null hoặc không đúng số lượng câu hỏi")
    void shouldThrowExceptionWhenResponseSizeMismatch() {
        GenerateQuestionsResponse res = new GenerateQuestionsResponse();
        res.setQuestions(List.of(new GeneratedQuestionItemResponse()));

        assertThatThrownBy(() -> QuestionValidator.validate(null, 5))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AI must return exactly 5 questions");

        assertThatThrownBy(() -> QuestionValidator.validate(res, 2))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AI must return exactly 2 questions");
    }

    @Test
    @DisplayName("Ném lỗi khi MCQ thiếu tùy chọn A, B, C, D")
    void shouldThrowExceptionWhenMCQMissingOptions() {
        GeneratedQuestionItemResponse question = new GeneratedQuestionItemResponse();
        question.setQuestionType(QuestionType.MCQ);
        question.setQuestion("2 + 2 = ?");
        question.setOptions(Map.of("A", "1", "B", "2")); // Thiếu C, D

        GenerateQuestionsResponse res = new GenerateQuestionsResponse();
        res.setQuestions(List.of(question));

        assertThatThrownBy(() -> QuestionValidator.validate(res, 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_GATEWAY)
                .hasMessageContaining("MCQ options must contain A,B,C,D");
    }

    @Test
    @DisplayName("Ném lỗi khi MCQ có đáp án đúng không hợp lệ")
    void shouldThrowExceptionWhenMCQInvalidCorrectAnswer() {
        GeneratedQuestionItemResponse question = new GeneratedQuestionItemResponse();
        question.setQuestionType(QuestionType.MCQ);
        question.setQuestion("Test?");
        question.setOptions(Map.of("A", "v", "B", "v", "C", "v", "D", "v"));
        question.setCorrectAnswer("E"); // Không nằm trong A,B,C,D

        GenerateQuestionsResponse res = new GenerateQuestionsResponse();
        res.setQuestions(List.of(question));

        assertThatThrownBy(() -> QuestionValidator.validate(res, 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MCQ correctAnswer must be A/B/C/D");
    }

    @Test
    @DisplayName("Ném lỗi khi ESSAY thiếu sampleAnswer hoặc keywords")
    void shouldThrowExceptionWhenEssayMissingRequiredFields() {
        GeneratedQuestionItemResponse question = new GeneratedQuestionItemResponse();
        question.setQuestionType(QuestionType.ESSAY);
        question.setQuestion("Hãy nêu cảm nhận...");
        // Thiếu Sample Answer, Keywords, MaxScore

        GenerateQuestionsResponse res = new GenerateQuestionsResponse();
        res.setQuestions(List.of(question));

        assertThatThrownBy(() -> QuestionValidator.validate(res, 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ESSAY sampleAnswer is required");
    }

    @Test
    @DisplayName("Validate thành công khi dữ liệu đầy đủ và đúng định dạng")
    void shouldPassValidationWhenDataIsValid() {
        // Mock MCQ
        GeneratedQuestionItemResponse mcq = new GeneratedQuestionItemResponse();
        mcq.setQuestionType(QuestionType.MCQ);
        mcq.setQuestion("Capital of France?");
        mcq.setOptions(Map.of("A", "Paris", "B", "London", "C", "Rome", "D", "Berlin"));
        mcq.setCorrectAnswer("a"); // Test trim and uppercase logic

        // Mock ESSAY
        GeneratedQuestionItemResponse essay = new GeneratedQuestionItemResponse();
        essay.setQuestionType(QuestionType.ESSAY);
        essay.setQuestion("Explain Java?");
        essay.setSampleAnswer("Java is a programming language...");
        essay.setKeywords(List.of("OOP", "JVM"));
        essay.setMaxScore(10);

        GenerateQuestionsResponse res = new GenerateQuestionsResponse();
        res.setQuestions(List.of(mcq, essay));

        // Không ném ra exception là pass
        QuestionValidator.validate(res, 2);
    }
}