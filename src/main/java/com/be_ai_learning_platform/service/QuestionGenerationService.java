package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;

public interface QuestionGenerationService {

    /**
     * ✅ PRACTICE / default flow (vẫn có thể dùng SystemSettings bên trong)
     */
    GenerateQuestionsResponse generate(String currentEmail, Long materialId, int numberOfQuestions);

    /**
     * ✅ ADMIN flow: chọn cơ cấu MCQ + ESSAY
     */
    GenerateQuestionsResponse generate(String currentEmail, Long materialId, int mcqCount, int essayCount);

    /**
     * ✅ Retest flow (vẫn theo SystemSettings)
     */
    GenerateQuestionsResponse generateRetest(String currentEmail, Long materialId, int numberOfQuestions, String focusText);
}