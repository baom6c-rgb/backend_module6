// AdminAnalyticsAiInsightServiceImpl.java
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnalyticsAiInsightServiceImpl implements AdminAnalyticsAiInsightService {

    private final AdminAnalyticsService adminAnalyticsService;
    private final AdminAnalyticsRepository analyticsRepository;
    private final GeminiStructuredClient geminiClient;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter ISO_DT = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_DATE;

    private final Cache<String, AdminAnalyticsAiInsightResponse> cache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(java.time.Duration.ofMinutes(20))
            .build();

    // =========================
    // GLOBAL: POST /api/admin/analytics/ai-insights
    // =========================
    @Override
    public AdminAnalyticsAiInsightResponse generateInsights(AdminAnalyticsFilterRequest req) {
        String cacheKey = hashFilter(req);
        AdminAnalyticsAiInsightResponse cached = cache.getIfPresent(cacheKey);
        if (cached != null) return cached;

        var overview = adminAnalyticsService.getOverview(req);
        List<AtRiskStudentResponse> atRisk =
                overview.getAtRiskStudents() == null ? List.of() : overview.getAtRiskStudents();

        Long totalAttempts = overview.getTotalAttempts();
        Long totalStudents = overview.getTotalStudents();
        Double avgScore = overview.getAvgScore();
        Double failRate = overview.getFailRate();

        if (totalAttempts == null || totalAttempts == 0) {
            AdminAnalyticsAiInsightResponse empty = fallbackNoData(
                    "Chưa có dữ liệu bài làm trong khoảng thời gian/bộ lọc hiện tại."
            );
            cache.put(cacheKey, empty);
            return empty;
        }

        // Top students for snapshot (max 5) - dùng từ overview (đã sort nguy hiểm)
        List<AtRiskStudentResponse> top = atRisk.subList(0, Math.min(5, atRisk.size()));

        // Sample feedback của top userIds (nếu có)
        List<Long> userIds = new ArrayList<>();
        for (AtRiskStudentResponse s : top) {
            if (s != null && s.getUserId() != null) userIds.add(s.getUserId());
        }

        Map<Long, List<String>> feedbackByUser = new HashMap<>();
        if (!userIds.isEmpty()) {
            LocalDateTime fromTs = parseFlexibleStart(req.getFrom());
            LocalDateTime toTs = parseFlexibleEnd(req.getTo());
            String keyword = normalize(req.getKeyword());

            try {
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

                for (var r : rows) {
                    if (r.getUserId() == null) continue;
                    feedbackByUser.putIfAbsent(r.getUserId(), new ArrayList<>());
                    List<String> list = feedbackByUser.get(r.getUserId());

                    // mỗi user lấy tối đa 2 sample
                    if (list.size() >= 2) continue;

                    String fb = r.getAiFeedback();
                    if (fb == null || fb.trim().isEmpty()) continue;
                    list.add(truncate(fb, 320));
                }
            } catch (Exception ex) {
                log.warn("[AI-INSIGHTS][GLOBAL] getFeedbackSamplesForUsers failed: {}", ex.getMessage());
            }
        }

        String prompt = buildGlobalPrompt(
                req,
                totalAttempts,
                totalStudents,
                avgScore,
                overview.getPassRate(),
                failRate,
                top,
                feedbackByUser
        );

        String contract = globalJsonContract();

        try {
            String json = geminiClient.generateJson(prompt, contract);
            AdminAnalyticsAiInsightResponse res = objectMapper.readValue(json, AdminAnalyticsAiInsightResponse.class);

            // defaults để FE không bị null
            if (res.getGeneratedAt() == null || res.getGeneratedAt().isBlank()) {
                res.setGeneratedAt(LocalDateTime.now().format(ISO_DT));
            }
            if (res.getConfidence() == null || res.getConfidence().isBlank()) {
                res.setConfidence(calcConfidence(totalAttempts, totalStudents));
            }
            if (res.getKeyProblems() == null) res.setKeyProblems(List.of());
            if (res.getAtRiskPatterns() == null) res.setAtRiskPatterns(List.of());
            if (res.getRecommendedActions() == null) res.setRecommendedActions(List.of());

            // students[]: nếu AI trả rỗng -> set top snapshot
            if (res.getStudents() == null || res.getStudents().isEmpty()) {
                res.setStudents(top);
            } else if (res.getStudents().size() > 5) {
                res.setStudents(res.getStudents().subList(0, 5));
            }

            // normalize riskLevel
            for (AtRiskStudentResponse s : res.getStudents()) {
                if (s == null) continue;
                s.setRiskLevel(normalizeRiskLevel(s.getRiskLevel()));
            }

            // summary rỗng -> heuristic
            if (res.getSummary() == null || res.getSummary().isBlank()) {
                res.setSummary(buildGlobalSummary(totalAttempts, totalStudents, avgScore, failRate));
            }

            cache.put(cacheKey, res);
            return res;

        } catch (Exception e) {
            log.warn("[AI-INSIGHTS][GLOBAL] Gemini failed -> heuristic fallback. err={}", e.getMessage());

            AdminAnalyticsAiInsightResponse fb = heuristicGlobalFallback(
                    totalAttempts,
                    totalStudents,
                    avgScore,
                    failRate,
                    top
            );
            cache.put(cacheKey, fb);
            return fb;
        }
    }

    // =========================
    // STUDENT: GET /api/admin/analytics/students/{userId}/ai-insight
    // (Service interface vẫn trả AtRiskStudentResponse)
    // =========================
    @Override
    public AtRiskStudentResponse getSingleStudentInsight(Long userId, Long classId, Long moduleId, String from, String to) {
        if (userId == null) return null;

        LocalDateTime fromTs = parseFlexibleStart(from);
        LocalDateTime toTs = parseFlexibleEnd(to);

        // ✅ FIX DỨT ĐIỂM: KHÔNG phụ thuộc at-risk list nữa
        AdminAnalyticsRepository.StudentAggByUserRow agg =
                analyticsRepository.getStudentAggByUser(userId, classId, moduleId, fromTs, toTs);

        if (agg == null || agg.getUserId() == null) {
            AtRiskStudentResponse empty = new AtRiskStudentResponse();
            empty.setUserId(userId);
            empty.setRiskLevel("LOW");
            empty.setRiskScore(0);
            empty.setReasons(new String[]{"Không có dữ liệu làm bài theo bộ lọc hiện tại."});
            empty.setInsightSummary("Chưa có dữ liệu bài làm để AI phân tích theo bộ lọc hiện tại.");
            empty.setWeakTopics(new String[0]);
            empty.setRecommendedNextSteps(new String[]{
                    "Thử mở rộng khoảng thời gian (from/to).",
                    "Thử bỏ lọc lớp/module để kiểm tra dữ liệu tổng."
            });
            // ✅ strengths là List<String>
            empty.setStrengths(List.of(
                    "Chưa đủ dữ liệu để xác định điểm mạnh rõ ràng theo bộ lọc hiện tại."
            ));
            return empty;
        }

        // map metrics
        long attempts = nzLong(agg.getAttemptsCount());
        double avg = nzDouble(agg.getAvgScore());
        long failed = nzLong(agg.getFailedCount());
        double failRate = attempts > 0 ? (double) failed / (double) attempts : 0.0;

        AtRiskStudentResponse student = new AtRiskStudentResponse();
        student.setUserId(agg.getUserId());
        student.setFullName(agg.getFullName());
        student.setEmail(agg.getEmail());
        student.setAttemptsCount(attempts);
        student.setAvgScore(avg);
        student.setFailRate(failRate);
        student.setLastAttemptAt(agg.getLastAttemptAt() == null ? null : agg.getLastAttemptAt().format(ISO_DT));

        Risk risk = computeRisk(attempts, avg, failRate);
        student.setRiskLevel(risk.level);
        student.setRiskScore(risk.score);
        student.setReasons(risk.reasons.toArray(new String[0]));

        // lấy latest feedback (max 3)
        List<AdminAnalyticsRepository.LatestFeedbackRow> rows = List.of();
        try {
            rows = analyticsRepository.getLatestFeedbackByUser(userId, classId, moduleId, fromTs, toTs);
        } catch (Exception ex) {
            log.warn("[AI-INSIGHTS][STUDENT] getLatestFeedbackByUser failed: {}", ex.getMessage());
        }

        // nếu không có feedback (ai_feedback null) -> vẫn trả heuristic, không fail
        if (rows == null) rows = List.of();

        String prompt = buildSingleStudentPrompt(student, rows);
        String contract = singleStudentJsonContract();

        try {
            String json = geminiClient.generateJson(prompt, contract);

            @SuppressWarnings("unchecked")
            Map<String, Object> ai = objectMapper.readValue(json, Map.class);

            student.setInsightSummary(safeStr(ai.get("insightSummary")));
            student.setWeakTopics(toStringArray(ai.get("weakTopics")));
            student.setRecommendedNextSteps(toStringArray(ai.get("recommendedNextSteps")));
            // ✅ strengths dùng List<String> giống style list-based
            student.setStrengths(safeList(ai.get("strengths")));
            student.setRiskLevel(normalizeRiskLevel(student.getRiskLevel()));

            // strengths rỗng -> heuristic
            if (student.getStrengths() == null || student.getStrengths().isEmpty()) {
                student.setStrengths(buildHeuristicStrengths(student));
            }

            // nếu AI trả rỗng -> heuristic
            if (student.getInsightSummary() == null || student.getInsightSummary().isBlank()) {
                applyStudentHeuristic(student);
            }

            return student;

        } catch (Exception e) {
            log.warn("[AI-INSIGHTS][STUDENT] Gemini failed -> heuristic fallback. err={}", e.getMessage());
            applyStudentHeuristic(student);
            return student;
        }
    }

    // =========================
    // GLOBAL prompt/contract
    // =========================

    private String buildGlobalPrompt(AdminAnalyticsFilterRequest req,
                                     Long totalAttempts,
                                     Long totalStudents,
                                     Double avgScore,
                                     Double passRate,
                                     Double failRate,
                                     List<AtRiskStudentResponse> top,
                                     Map<Long, List<String>> feedbackByUser) {

        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là chuyên gia phân tích dữ liệu học tập. Nhiệm vụ: phân tích thống kê và đề xuất hành động cho Admin.\n\n");

        sb.append("Bộ lọc:\n");
        sb.append("- classId: ").append(req.getClassId()).append("\n");
        sb.append("- moduleId: ").append(req.getModuleId()).append("\n");
        sb.append("- from: ").append(req.getFrom()).append("\n");
        sb.append("- to: ").append(req.getTo()).append("\n");
        sb.append("- scoreMin: ").append(req.getScoreMin()).append("\n");
        sb.append("- scoreMax: ").append(req.getScoreMax()).append("\n");
        sb.append("- keyword: ").append(req.getKeyword()).append("\n\n");

        sb.append("Tổng quan:\n");
        sb.append("- totalAttempts: ").append(totalAttempts).append("\n");
        sb.append("- totalStudents: ").append(totalStudents).append("\n");
        sb.append("- avgScore: ").append(avgScore == null ? 0.0 : round(avgScore)).append("\n");
        sb.append("- passRate: ").append(passRate == null ? 0.0 : round(passRate)).append("\n");
        sb.append("- failRate: ").append(failRate == null ? 0.0 : round(failRate)).append("\n\n");

        sb.append("Top học viên rủi ro (tối đa 5):\n");
        if (top == null || top.isEmpty()) {
            sb.append("- (none)\n");
        } else {
            for (int i = 0; i < top.size(); i++) {
                AtRiskStudentResponse s = top.get(i);
                if (s == null) continue;

                sb.append(i + 1).append(") ")
                        .append(s.getFullName()).append(" | ").append(s.getEmail())
                        .append(" | attempts=").append(nzLong(s.getAttemptsCount()))
                        .append(" | avgScore=").append(round(nzDouble(s.getAvgScore())))
                        .append(" | failRate=").append(round(nzDouble(s.getFailRate())))
                        .append(" | risk=").append(s.getRiskLevel()).append("(").append(s.getRiskScore()).append(")")
                        .append(" | reasons=").append(Arrays.toString(s.getReasons()))
                        .append("\n");

                List<String> samples = feedbackByUser.get(s.getUserId());
                if (samples != null && !samples.isEmpty()) {
                    sb.append("   feedback_samples:\n");
                    for (String fb : samples) sb.append("   - ").append(fb).append("\n");
                }
            }
        }

        sb.append("\nYêu cầu output: ngắn gọn, actionable, không phán xét cá nhân.\n");
        return sb.toString();
    }

    private String globalJsonContract() {
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
- keyProblems: 3-5 items
- atRiskPatterns: 1-3 items
- recommendedActions: 3-6 items, cụ thể
- confidence ∈ {"LOW","MEDIUM","HIGH"}
- generatedAt: ISO datetime
- KẾT THÚC OUTPUT bằng dấu }
""";
    }

    // =========================
    // STUDENT prompt/contract
    // =========================

    private String buildSingleStudentPrompt(AtRiskStudentResponse student,
                                            List<AdminAnalyticsRepository.LatestFeedbackRow> latest) {

        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ giảng/mentor kỹ thuật. Nhiệm vụ: phân tích học viên dựa vào kết quả làm bài và feedback gần nhất.\n");
        sb.append("Trả về JSON đúng contract.\n\n");

        sb.append("Thông tin học viên:\n");
        sb.append("- fullName: ").append(student.getFullName()).append("\n");
        sb.append("- email: ").append(student.getEmail()).append("\n");
        sb.append("- attemptsCount: ").append(nzLong(student.getAttemptsCount())).append("\n");
        sb.append("- avgScore: ").append(round(nzDouble(student.getAvgScore()))).append("\n");
        sb.append("- failRate: ").append(round(nzDouble(student.getFailRate()))).append("\n");
        sb.append("- riskLevel: ").append(student.getRiskLevel()).append("\n");
        sb.append("- riskScore: ").append(student.getRiskScore()).append("\n");
        sb.append("- reasons: ").append(Arrays.toString(student.getReasons())).append("\n\n");

        sb.append("Feedback gần nhất (tối đa 3):\n");
        if (latest == null || latest.isEmpty()) {
            sb.append("- (none)\n");
        } else {
            for (var r : latest) {
                sb.append("- score=").append(r.getScore())
                        .append(" | submitTime=").append(r.getSubmitTime())
                        .append(" | aiFeedback=").append(truncate(r.getAiFeedback(), 420))
                        .append("\n");
            }
        }

        sb.append("\nYêu cầu: ngắn gọn, nêu rõ điểm yếu, điểm mạnh, và bước tiếp theo.\n");
        return sb.toString();
    }

    private String singleStudentJsonContract() {
        return """
CHỈ TRẢ VỀ JSON THUẦN – KHÔNG markdown, KHÔNG giải thích.
BẮT BUỘC 1 JSON object duy nhất, đầy đủ key.

FORMAT:
{
  "insightSummary": "1-2 câu (tiếng Việt)",
  "strengths": ["điểm mạnh 1", "điểm mạnh 2"],
  "weakTopics": ["chủ đề yếu 1", "chủ đề yếu 2"],
  "recommendedNextSteps": ["bước 1", "bước 2", "bước 3"]
}

RULES:
- strengths: 2-4 items (nếu thiếu dữ liệu thì để rỗng [])
- weakTopics: 2-5 items
- recommendedNextSteps: 3-6 items, actionable
- KẾT THÚC OUTPUT bằng dấu }
""";
    }

    // =========================
    // Heuristic fallbacks
    // =========================

    private AdminAnalyticsAiInsightResponse fallbackNoData(String msg) {
        AdminAnalyticsAiInsightResponse r = new AdminAnalyticsAiInsightResponse();
        r.setSummary(msg);
        r.setKeyProblems(List.of("Không có dữ liệu bài làm theo bộ lọc hiện tại."));
        r.setAtRiskPatterns(List.of());
        r.setRecommendedActions(List.of(
                "Thử mở rộng thời gian (from/to).",
                "Thử bỏ lọc lớp/module để kiểm tra dữ liệu toàn hệ thống."
        ));
        r.setStudents(List.of());
        r.setConfidence("LOW");
        r.setGeneratedAt(LocalDateTime.now().format(ISO_DT));
        return r;
    }

    private AdminAnalyticsAiInsightResponse heuristicGlobalFallback(
            Long totalAttempts,
            Long totalStudents,
            Double avgScore,
            Double failRate,
            List<AtRiskStudentResponse> top
    ) {
        double a = nzDouble(avgScore);
        double f = nzDouble(failRate);

        List<String> problems = new ArrayList<>();
        if (a < 50) problems.add("Điểm trung bình < 50 (nền tảng yếu).");
        if (f >= 0.7) problems.add("Tỷ lệ trượt cao (>= 70%).");
        if (top != null && !top.isEmpty()) problems.add("Có nhóm học viên rủi ro cao cần can thiệp theo cá nhân.");
        if (problems.isEmpty()) problems.add("Chưa phát hiện vấn đề nghiêm trọng theo bộ lọc hiện tại.");

        List<String> patterns = List.of(
                "Nhóm rủi ro thường làm nhiều bài nhưng điểm vẫn thấp.",
                "Lỗi lặp lại cho thấy chưa nắm chắc khái niệm cốt lõi."
        );

        List<String> actions = List.of(
                "Tổ chức chữa đề theo chủ đề sai nhiều: vì sao sai + cách suy luận đúng.",
                "Tách nhóm học viên rủi ro cao để học bù theo chủ đề yếu.",
                "Thiết kế bài luyện theo cấp độ (cơ bản → nâng cao) và bắt buộc review sau bài."
        );

        AdminAnalyticsAiInsightResponse r = new AdminAnalyticsAiInsightResponse();
        r.setSummary(buildGlobalSummary(totalAttempts, totalStudents, avgScore, failRate));
        r.setKeyProblems(problems);
        r.setAtRiskPatterns(patterns);
        r.setRecommendedActions(actions);
        r.setStudents(top == null ? List.of() : top);
        r.setConfidence(calcConfidence(totalAttempts, totalStudents));
        r.setGeneratedAt(LocalDateTime.now().format(ISO_DT));
        return r;
    }

    private String buildGlobalSummary(Long totalAttempts, Long totalStudents, Double avgScore, Double failRate) {
        return String.format(
                "Trong bộ lọc hiện tại có %d học viên (%d bài làm). Điểm TB %.1f, tỷ lệ trượt %.0f%%.",
                nzLong(totalStudents),
                nzLong(totalAttempts),
                round(nzDouble(avgScore)),
                round(nzDouble(failRate) * 100)
        );
    }

    private void applyStudentHeuristic(AtRiskStudentResponse s) {
        s.setInsightSummary(String.format(
                "%s đang có điểm TB %.1f và tỷ lệ trượt %.0f%%. Nên tập trung củng cố kiến thức nền và luyện giải theo chủ đề yếu.",
                s.getFullName() == null ? "Học viên" : s.getFullName(),
                round(nzDouble(s.getAvgScore())),
                round(nzDouble(s.getFailRate()) * 100)
        ));

        // ✅ strengths is List<String>
        if (s.getStrengths() == null || s.getStrengths().isEmpty()) {
            s.setStrengths(buildHeuristicStrengths(s));
        }

        s.setWeakTopics(new String[]{
                "Nắm chắc khái niệm cốt lõi và giải thích được “tại sao”.",
                "Tránh học thuộc máy móc, cần hiểu luồng và ứng dụng thực tế.",
                "Kỹ năng phân tích đề và loại trừ đáp án sai."
        });

        s.setRecommendedNextSteps(new String[]{
                "Ôn lại lý thuyết nền và tự diễn giải lại bằng lời/viết.",
                "Làm bài mức cơ bản trước, sau đó tăng độ khó dần.",
                "Sau mỗi bài: review câu sai, ghi lại nguyên nhân sai + cách làm đúng."
        });

        s.setRiskLevel(normalizeRiskLevel(s.getRiskLevel()));
        if (s.getReasons() == null || s.getReasons().length == 0) {
            Risk risk = computeRisk(nzLong(s.getAttemptsCount()), nzDouble(s.getAvgScore()), nzDouble(s.getFailRate()));
            s.setReasons(risk.reasons.toArray(new String[0]));
            s.setRiskScore(risk.score);
            s.setRiskLevel(risk.level);
        }
    }

    // ✅ return List<String> (không đổi flow khác)
    private List<String> buildHeuristicStrengths(AtRiskStudentResponse s) {
        List<String> strengths = new ArrayList<>();

        double avg = nzDouble(s.getAvgScore());
        double failRate = nzDouble(s.getFailRate());
        long attempts = nzLong(s.getAttemptsCount());

        if (avg >= 75) {
            strengths.add("Điểm trung bình cao và ổn định");
        } else if (avg >= 60) {
            strengths.add("Có nền tảng khá, khả năng tiếp thu tốt");
        }

        if (failRate <= 0.2 && attempts > 0) {
            strengths.add("Tỷ lệ đạt tốt");
        } else if (failRate <= 0.35 && attempts > 0) {
            strengths.add("Tỷ lệ trượt không cao, có tiềm năng cải thiện nhanh");
        }

        if (attempts >= 5) {
            strengths.add("Chăm luyện tập và có kỷ luật học tập");
        }

        // lastAttemptAt là String ISO trong DTO, chỉ parse nếu có
        try {
            String last = s.getLastAttemptAt();
            if (last != null && !last.isBlank()) {
                LocalDateTime lastTs = LocalDateTime.parse(last.trim(), ISO_DT);
                if (lastTs.isAfter(LocalDateTime.now().minusDays(7))) {
                    strengths.add("Duy trì nhịp học đều trong thời gian gần đây");
                }
            }
        } catch (Exception ignored) {
        }

        if (strengths.isEmpty()) {
            strengths.add("Có nền tảng cơ bản, cần tăng dần độ khó để cải thiện");
        }

        // limit 4 items
        if (strengths.size() > 4) strengths = strengths.subList(0, 4);

        return strengths;
    }

    // ✅ safeList(Object) -> List<String> để parse "strengths" từ AI
    private List<String> safeList(Object o) {
        if (o == null) return List.of();
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object x : list) {
                if (x == null) continue;
                String s = String.valueOf(x).trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return List.of();
        return List.of(s);
    }

    // =========================
    // Risk
    // =========================

    private Risk computeRisk(long attempts, double avgScore, double failRate) {
        int score = 0;

        // avgScore
        if (avgScore < 40) score += 55;
        else if (avgScore < 50) score += 45;
        else if (avgScore < 60) score += 30;
        else if (avgScore < 70) score += 15;

        // failRate
        if (failRate >= 0.9) score += 35;
        else if (failRate >= 0.7) score += 25;
        else if (failRate >= 0.5) score += 15;
        else if (failRate >= 0.3) score += 8;

        // attempts pattern
        if (attempts >= 8 && avgScore < 60) score += 10;
        else if (attempts >= 5 && avgScore < 55) score += 8;

        score = Math.max(0, Math.min(100, score));

        String level = score >= 80 ? "HIGH" : (score >= 50 ? "MEDIUM" : "LOW");

        List<String> reasons = new ArrayList<>();
        if (avgScore < 50) reasons.add("Điểm trung bình < 50 (Yếu)");
        if (failRate >= 0.7) reasons.add("Tỷ lệ trượt cao");
        if (attempts >= 5 && avgScore < 60) reasons.add("Làm nhiều bài nhưng điểm vẫn thấp");
        if (reasons.isEmpty()) reasons.add("Chưa phát hiện vấn đề nghiêm trọng theo bộ lọc hiện tại.");

        return new Risk(level, score, reasons);
    }

    private record Risk(String level, int score, List<String> reasons) {
    }

    // =========================
    // Date parsing (fix yyyy-MM-dd)
    // =========================

    private LocalDateTime parseFlexibleStart(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String t = s.trim();

        // yyyy-MM-dd
        if (t.length() == 10) {
            try {
                LocalDate d = LocalDate.parse(t, ISO_DATE);
                return d.atStartOfDay();
            } catch (DateTimeParseException ignored) {
            }
        }

        // ISO datetime
        try {
            return LocalDateTime.parse(t, ISO_DT);
        } catch (DateTimeParseException ignored) {
        }

        // fallback
        try {
            return LocalDateTime.parse(t);
        } catch (Exception ignored) {
        }

        return null;
    }

    private LocalDateTime parseFlexibleEnd(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String t = s.trim();

        // yyyy-MM-dd => end of day
        if (t.length() == 10) {
            try {
                LocalDate d = LocalDate.parse(t, ISO_DATE);
                return d.atTime(23, 59, 59);
            } catch (DateTimeParseException ignored) {
            }
        }

        // ISO datetime
        try {
            return LocalDateTime.parse(t, ISO_DT);
        } catch (DateTimeParseException ignored) {
        }

        // fallback
        try {
            return LocalDateTime.parse(t);
        } catch (Exception ignored) {
        }

        return null;
    }

    // =========================
    // Utils
    // =========================

    private String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.length() <= max) return t;
        return t.substring(0, max) + "...";
    }

    private String safeStr(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private String[] toStringArray(Object o) {
        if (o == null) return new String[0];
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object x : list) {
                if (x == null) continue;
                String s = String.valueOf(x).trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out.toArray(new String[0]);
        }
        return new String[0];
    }

    private String normalizeRiskLevel(String level) {
        if (level == null) return "LOW";
        String x = level.trim().toUpperCase();
        if (x.contains("HIGH")) return "HIGH";
        if (x.contains("MED")) return "MEDIUM";
        if (x.contains("LOW")) return "LOW";
        return "LOW";
    }

    private String calcConfidence(Long totalAttempts, Long totalStudents) {
        long a = nzLong(totalAttempts);
        long s = nzLong(totalStudents);
        if (s >= 10 && a >= 50) return "HIGH";
        if (s >= 3 && a >= 10) return "MEDIUM";
        return "LOW";
    }

    private long nzLong(Long v) {
        return v == null ? 0L : v;
    }

    private double nzDouble(Double v) {
        return v == null ? 0.0 : v;
    }

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
