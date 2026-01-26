package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;

public interface QuestionGenerationService {
    GenerateQuestionsResponse generate(String currentEmail, Long materialId, int numberOfQuestions);
}
