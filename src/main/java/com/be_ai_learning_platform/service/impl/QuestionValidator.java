package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;
import com.be_ai_learning_platform.dto.response.GeneratedQuestionItemResponse;
import com.be_ai_learning_platform.entity.enums.QuestionType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

public class QuestionValidator {

    public static void validate(GenerateQuestionsResponse res, int n) {
        if (res == null || res.getQuestions() == null || res.getQuestions().size() != n) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI must return exactly " + n + " questions");
        }

        for (GeneratedQuestionItemResponse q : res.getQuestions()) {
            if (q == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Question item is null");
            }

            if (q.getQuestionType() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "questionType is required");
            }
            if (q.getQuestion() == null || q.getQuestion().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Question is blank");
            }

            if (q.getQuestionType() == QuestionType.MCQ) {
                Map<String, String> options = q.getOptions();
                if (options == null || !options.keySet().containsAll(List.of("A", "B", "C", "D"))) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MCQ options must contain A,B,C,D");
                }

                String ca = q.getCorrectAnswer() == null ? "" : q.getCorrectAnswer().trim().toUpperCase();
                if (!List.of("A", "B", "C", "D").contains(ca)) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MCQ correctAnswer must be A/B/C/D");
                }
            }

            if (q.getQuestionType() == QuestionType.ESSAY) {
                if (q.getSampleAnswer() == null || q.getSampleAnswer().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ESSAY sampleAnswer is required");
                }
                if (q.getKeywords() == null || q.getKeywords().isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ESSAY keywords is required");
                }
                if (q.getMaxScore() == null || q.getMaxScore() <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ESSAY maxScore must be > 0");
                }
            }
        }
    }
}
