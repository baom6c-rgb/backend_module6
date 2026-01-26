package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;
import com.be_ai_learning_platform.dto.response.GeneratedQuestionItemResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

public class QuestionValidator {

    public static void validate(GenerateQuestionsResponse res, int n) {
        if (res.getQuestions() == null || res.getQuestions().size() != n) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI must return exactly " + n + " questions");
        }

        for (GeneratedQuestionItemResponse q : res.getQuestions()) {
            if (q.getQuestion() == null || q.getQuestion().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Question is blank");
            }
            if (q.getOptions() == null || !q.getOptions().keySet().containsAll(List.of("A", "B", "C", "D"))) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Options must contain A,B,C,D");
            }
            if (q.getCorrectAnswer() == null || !List.of("A", "B", "C", "D").contains(q.getCorrectAnswer())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "correctAnswer must be A/B/C/D");
            }
        }
    }
}
