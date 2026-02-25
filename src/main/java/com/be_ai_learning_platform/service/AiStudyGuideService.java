package com.be_ai_learning_platform.service;

public interface AiStudyGuideService {

    /**
     * Generate "Gợi ý ôn tập" (study guide) bằng AI.
     *
     * @param userFullName   tên người dùng để greeting (có thể null)
     * @param userResultJson JSON tóm tắt kết quả bài làm (do BE build)
     */
    String generateStudyGuide(String userFullName, String userResultJson);

    /**
     * Fallback khi AI fail/blank.
     */
    String fallbackStudyGuide(String userFullName);
}