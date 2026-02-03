package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.AdminAnalyticsFilterRequest;
import com.be_ai_learning_platform.dto.response.AdminAnalyticsAiInsightResponse;
import com.be_ai_learning_platform.dto.response.AdminAnalyticsOverviewResponse;
import com.be_ai_learning_platform.service.AdminAnalyticsAiInsightService;
import com.be_ai_learning_platform.service.AdminAnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5175")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;
    private final AdminAnalyticsAiInsightService aiInsightService;

    @PostMapping("/overview")
    public ResponseEntity<AdminAnalyticsOverviewResponse> overview(@Valid @RequestBody AdminAnalyticsFilterRequest req) {
        return ResponseEntity.ok(adminAnalyticsService.getOverview(req));
    }

    // ✅ NEW: AI Insights
    @PostMapping("/ai-insights")
    public ResponseEntity<AdminAnalyticsAiInsightResponse> aiInsights(@Valid @RequestBody AdminAnalyticsFilterRequest req) {
        return ResponseEntity.ok(aiInsightService.generateInsights(req));
    }
}
