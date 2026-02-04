package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.AdminAnalyticsFilterRequest;
import com.be_ai_learning_platform.dto.response.AdminAnalyticsAiInsightResponse;
import com.be_ai_learning_platform.dto.response.AtRiskStudentResponse;

public interface AdminAnalyticsAiInsightService {
    AdminAnalyticsAiInsightResponse generateInsights(AdminAnalyticsFilterRequest req);

    // ✅ NEW: xem AI insight riêng 1 học viên
    AtRiskStudentResponse getSingleStudentInsight(
            Long userId,
            Long classId,
            Long moduleId,
            String from,
            String to
    );
}
