package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.AdminAnalyticsFilterRequest;
import com.be_ai_learning_platform.dto.response.AdminAnalyticsAiInsightResponse;
import com.be_ai_learning_platform.dto.response.AdminAnalyticsOverviewResponse;
import com.be_ai_learning_platform.dto.response.AtRiskStudentResponse;
import com.be_ai_learning_platform.service.AdminAnalyticsAiInsightService;
import com.be_ai_learning_platform.service.AdminAnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5175")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;
    private final AdminAnalyticsAiInsightService aiInsightService;

    private static final DateTimeFormatter ISO_DT = DateTimeFormatter.ISO_DATE_TIME;

    @PostMapping("/overview")
    public ResponseEntity<AdminAnalyticsOverviewResponse> overview(@Valid @RequestBody AdminAnalyticsFilterRequest req) {
        return ResponseEntity.ok(adminAnalyticsService.getOverview(req));
    }

    // ✅ AI Insights (summary + students[])
    @PostMapping("/ai-insights")
    public ResponseEntity<AdminAnalyticsAiInsightResponse> aiInsights(@Valid @RequestBody AdminAnalyticsFilterRequest req) {
        return ResponseEntity.ok(aiInsightService.generateInsights(req));
    }

    // ✅ Student AI insight -> đổi return type sang AdminAnalyticsAiInsightResponse
    // GET /api/admin/analytics/students/{userId}/ai-insight?classId=&moduleId=&from=&to=
    @GetMapping("/students/{userId}/ai-insight")
    public ResponseEntity<AdminAnalyticsAiInsightResponse> studentAiInsight(
            @PathVariable Long userId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long moduleId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        AtRiskStudentResponse s = aiInsightService.getSingleStudentInsight(userId, classId, moduleId, from, to);

        AdminAnalyticsAiInsightResponse res = new AdminAnalyticsAiInsightResponse();
        res.setSummary(s == null ? null : s.getInsightSummary());
        res.setKeyProblems(s == null ? List.of() : (s.getReasons() == null ? List.of() : Arrays.asList(s.getReasons())));
        res.setAtRiskPatterns(s == null ? List.of() : (s.getWeakTopics() == null ? List.of() : Arrays.asList(s.getWeakTopics())));
        res.setRecommendedActions(s == null ? List.of() : (s.getRecommendedNextSteps() == null ? List.of() : Arrays.asList(s.getRecommendedNextSteps())));
        res.setStudents(s == null ? List.of() : List.of(s));

        // confidence heuristic: dựa trên riskScore nếu có
        String conf = "LOW";
        if (s != null && s.getRiskScore() != null) {
            if (s.getRiskScore() >= 80) conf = "HIGH";
            else if (s.getRiskScore() >= 50) conf = "MEDIUM";
        }
        res.setConfidence(conf);
        res.setGeneratedAt(LocalDateTime.now().format(ISO_DT));

        return ResponseEntity.ok(res);
    }
}
