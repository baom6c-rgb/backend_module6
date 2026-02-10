package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;
import com.be_ai_learning_platform.entity.enums.QuestionType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class QuestionDistributionValidator {

    private QuestionDistributionValidator() {}

    public static void validate(GenerateQuestionsResponse res, int expectedMcq, int expectedEssay) {
        if (res == null || res.getQuestions() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI response is empty");
        }

        long mcq = res.getQuestions().stream()
                .filter(q -> q != null && q.getQuestionType() == QuestionType.MCQ)
                .count();

        long essay = res.getQuestions().stream()
                .filter(q -> q != null && q.getQuestionType() == QuestionType.ESSAY)
                .count();

        if (mcq != expectedMcq || essay != expectedEssay) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AI returned wrong question distribution"
            );
        }
    }
}
