package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.ai.GeminiStructuredClient;
import com.be_ai_learning_platform.dto.request.AdminAnalyticsFilterRequest;
import com.be_ai_learning_platform.dto.response.AdminAnalyticsAiInsightResponse;
import com.be_ai_learning_platform.dto.response.AtRiskStudentResponse;
import com.be_ai_learning_platform.repository.AdminAnalyticsRepository;
import com.be_ai_learning_platform.service.AdminAnalyticsAiInsightService;
import com.be_ai_learning_platform.service.AdminAnalyticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsAiInsightServiceImpl implements AdminAnalyticsAiInsightService {

    private final AdminAnalyticsService adminAnalyticsService; // reuse overview logic
    private final AdminAnalyticsRepository analyticsRepository;
    private final GeminiStructuredClient geminiClient;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_DATE_TIME;

    // cache 20 phút, tối đa 200 key
    private final Cache<String, AdminAnalyticsAiInsightResponse> cache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(java.time.Duration.ofMinutes(20))
            .build();

    @Override
    public AdminAnalyticsAiInsightResponse generateInsights(AdminAnalyticsFilterRequest req) {
        String cacheKey = hashFilter(req);
        AdminAnalyticsAiInsightResponse cached = cache.getIfPresent(cacheKey);
        if (cached != null) return cached;

        // 1) Lấy overview + at-risk đã tính
        var overview = adminAnalyticsService.getOverview(req);
        List<AtRiskStudentResponse> atRisk = overview.getAtRiskStudents() == null ? List.of() : overview.getAtRiskStudents();

        // Nếu không có dữ liệu => trả sớm
        if (overview.getTotalAttempts() == null || overview.getTotalAttempts() == 0) {
            AdminAnalyticsAiInsightResponse empty = fallback("Chưa có dữ liệu bài làm trong khoảng thời gian lọc.", "LOW");
            cache.put(cacheKey, empty);
            return empty;
        }

        // 2) Lấy sample feedback cho top at-risk (tối đa 5 user)
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < Math.min(5, atRisk.size()); i++) {
            if (atRisk.get(i).getUserId() != null) userIds.add(atRisk.get(i).getUserId());
        }

        Map<Long, List<String>> feedbackByUser = new HashMap<>();
        if (!userIds.isEmpty()) {
            var fromTs = parseIso(req.getFrom());
            var toTs = parseIso(req.getTo());
            String keyword = normalize(req.getKeyword());

            List<AdminAnalyticsRepository.FeedbackSampleRow> rows =
                    analyticsRepository.getFeedbackSamplesForUsers(
                            req.getClassId(),
                            req.getModuleId(),
                            fromTs,
                            toTs,
                            req.getScoreMin(),
                            req.getScoreMax(),
                            keyword,
                            userIds
                    );

            // gom: mỗi user lấy tối đa 2 feedback gần nhất (truncate)
            for (var r : rows) {
                if (r.getUserId() == null) continue;
                feedbackByUser.putIfAbsent(r.getUserId(), new ArrayList<>());
                List<String> list = feedbackByUser.get(r.getUserId());
                if (list.size() >= 2) continue;

                String fb = r.getAiFeedback();
                if (fb == null) continue;
                list.add(truncate(fb, 320));
            }
        }

        // 3) Build prompt
        String prompt = buildPrompt(req, overview.getTotalAttempts(), overview.getTotalStudents(),
                overview.getAvgScore(), overview.getPassRate(), overview.getFailRate(),
                atRisk, feedbackByUser);

        // 4) JSON contract cho insights
        String contract = insightsJsonContract();

        try {
            String json = geminiClient.generateJson(prompt, contract);

            AdminAnalyticsAiInsightResponse res = objectMapper.readValue(json, AdminAnalyticsAiInsightResponse.class);
            if (res.getGeneratedAt() == null || res.getGeneratedAt().isBlank()) {
                res.setGeneratedAt(LocalDateTime.now().format(ISO));
            }
            if (res.getConfidence() == null || res.getConfidence().isBlank()) {
                res.setConfidence("MEDIUM");
            }

            cache.put(cacheKey, res);
            return res;

        } catch (Exception e) {
            // không làm sập dashboard
            AdminAnalyticsAiInsightResponse fallback = fallback("Không thể phân tích AI tại thời điểm này. Vui lòng thử lại sau.", "LOW");
            cache.put(cacheKey, fallback);
            return fallback;
        }
    }

    private String buildPrompt(AdminAnalyticsFilterRequest req,
                               Long totalAttempts, Long totalStudents,
                               Double avgScore, Double passRate, Double failRate,
                               List<AtRiskStudentResponse> atRisk,
                               Map<Long, List<String>> feedbackByUser) {

        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là chuyên gia phân tích dữ liệu học tập. Nhiệm vụ: phân tích thống kê để xác định nguyên nhân học viên trượt và gợi ý can thiệp.\n");
        sb.append("Ngữ cảnh: Dashboard Admin của nền tảng luyện thi.\n\n");

        sb.append("Bộ lọc:\n");
        sb.append("- classId: ").append(req.getClassId()).append("\n");
        sb.append("- moduleId: ").append(req.getModuleId()).append("\n");
        sb.append("- from: ").append(req.getFrom()).append("\n");
        sb.append("- to: ").append(req.getTo()).append("\n\n");

        sb.append("Tổng quan:\n");
        sb.append("- totalAttempts: ").append(nvl(totalAttempts)).append("\n");
        sb.append("- totalStudents: ").append(nvl(totalStudents)).append("\n");
        sb.append("- avgScore: ").append(avgScore == null ? 0.0 : round(avgScore)).append("\n");
        sb.append("- passRate: ").append(passRate == null ? 0.0 : round(passRate)).append("\n");
        sb.append("- failRate: ").append(failRate == null ? 0.0 : round(failRate)).append("\n\n");

        sb.append("Top at-risk students (tối đa 5):\n");
        if (atRisk == null || atRisk.isEmpty()) {
            sb.append("- (none)\n");
        } else {
            for (int i = 0; i < Math.min(5, atRisk.size()); i++) {
                AtRiskStudentResponse s = atRisk.get(i);
                sb.append(i + 1).append(") ")
                        .append(s.getFullName()).append(" | ").append(s.getEmail())
                        .append(" | attempts=").append(s.getAttemptsCount())
                        .append(" | avgScore=").append(s.getAvgScore() == null ? 0.0 : round(s.getAvgScore()))
                        .append(" | failRate=").append(s.getFailRate() == null ? 0.0 : round(s.getFailRate()))
                        .append(" | reasons=").append(Arrays.toString(s.getReasons()))
                        .append("\n");

                List<String> samples = feedbackByUser.get(s.getUserId());
                if (samples != null && !samples.isEmpty()) {
                    sb.append("   feedback_samples:\n");
                    for (String fb : samples) {
                        sb.append("   - ").append(fb).append("\n");
                    }
                }
            }
        }

        sb.append("\nYêu cầu output: chỉ phân tích ngắn gọn, actionable, không phán xét cá nhân.\n");
        return sb.toString();
    }

    private String insightsJsonContract() {
        return """
CHỈ TRẢ VỀ JSON THUẦN – KHÔNG markdown, KHÔNG giải thích.
BẮT BUỘC 1 JSON object duy nhất, đầy đủ key.

FORMAT:
{
  "summary": "1-3 câu tổng kết (tiếng Việt)",
  "keyProblems": ["vấn đề 1", "vấn đề 2", "vấn đề 3"],
  "atRiskPatterns": ["pattern 1", "pattern 2"],
  "recommendedActions": ["hành động 1", "hành động 2", "hành động 3"],
  "confidence": "LOW",
  "generatedAt": "2026-02-03T12:00:00"
}

RULES:
- keyProblems: 3-5 gạch đầu dòng, ngắn gọn
- atRiskPatterns: 1-3 gạch đầu dòng
- recommendedActions: 3-6 gạch đầu dòng, cụ thể, có thể thực thi
- confidence ∈ {"LOW","MEDIUM","HIGH"}
- generatedAt: ISO datetime
- KẾT THÚC OUTPUT bằng dấu }
""";
    }

    private AdminAnalyticsAiInsightResponse fallback(String msg, String confidence) {
        AdminAnalyticsAiInsightResponse r = new AdminAnalyticsAiInsightResponse();
        r.setSummary(msg);
        r.setKeyProblems(List.of());
        r.setAtRiskPatterns(List.of());
        r.setRecommendedActions(List.of());
        r.setConfidence(confidence);
        r.setGeneratedAt(LocalDateTime.now().format(ISO));
        return r;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.length() <= max) return t;
        return t.substring(0, max) + "...";
    }

    private String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private LocalDateTime parseIso(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        return LocalDateTime.parse(s.trim(), ISO);
    }

    private long nvl(Long v) { return v == null ? 0L : v; }

    private double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private String hashFilter(AdminAnalyticsFilterRequest req) {
        try {
            String raw = String.valueOf(req.getClassId()) + "|" +
                    String.valueOf(req.getModuleId()) + "|" +
                    String.valueOf(req.getFrom()) + "|" +
                    String.valueOf(req.getTo()) + "|" +
                    String.valueOf(req.getScoreMin()) + "|" +
                    String.valueOf(req.getScoreMax()) + "|" +
                    String.valueOf(req.getKeyword());

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
