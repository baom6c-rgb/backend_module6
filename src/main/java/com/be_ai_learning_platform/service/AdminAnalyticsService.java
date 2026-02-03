package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.AdminAnalyticsFilterRequest;
import com.be_ai_learning_platform.dto.response.AdminAnalyticsOverviewResponse;

public interface AdminAnalyticsService {
    AdminAnalyticsOverviewResponse getOverview(AdminAnalyticsFilterRequest req);
}
