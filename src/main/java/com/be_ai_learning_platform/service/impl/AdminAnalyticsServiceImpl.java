package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.AdminAnalyticsFilterRequest;
import com.be_ai_learning_platform.dto.response.AdminAnalyticsOverviewResponse;
import com.be_ai_learning_platform.dto.response.AtRiskStudentResponse;
import com.be_ai_learning_platform.dto.response.TimeSeriesPointResponse;
import com.be_ai_learning_platform.repository.AdminAnalyticsRepository;
import com.be_ai_learning_platform.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    private final AdminAnalyticsRepository analyticsRepository;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_DATE_TIME;

    // ===== New score scale =====
    private static final double GOOD_SCORE = 80.0;      // >= 80: TỐT
    private static final double AVERAGE_SCORE = 50.0;   // 50-79.99: TRUNG BÌNH
    private static final long MIN_ATTEMPTS = 3;

    @Override
    public AdminAnalyticsOverviewResponse getOverview(AdminAnalyticsFilterRequest req) {
        LocalDateTime fromTs = parseIso(req.getFrom());
        LocalDateTime toTs = parseIso(req.getTo());
        String keyword = normalize(req.getKeyword());

        AdminAnalyticsRepository.OverviewRow ov = analyticsRepository.getOverview(
                req.getClassId(),
                req.getModuleId(),
                fromTs,
                toTs,
                req.getScoreMin(),
                req.getScoreMax(),
                keyword
        );

        long totalAttempts = ov == null || ov.getTotalAttempts() == null ? 0L : ov.getTotalAttempts();
        long totalStudents = ov == null || ov.getTotalStudents() == null ? 0L : ov.getTotalStudents();
        double avgScore = ov == null || ov.getAvgScore() == null ? 0.0 : ov.getAvgScore();
        long failedAttempts = ov == null || ov.getFailedAttempts() == null ? 0L : ov.getFailedAttempts();

        double failRate = totalAttempts == 0 ? 0.0 : (failedAttempts * 1.0 / totalAttempts);
        double passRate = totalAttempts == 0 ? 0.0 : (1.0 - failRate);

        List<AdminAnalyticsRepository.StudentAggRow> studentAgg = analyticsRepository.getStudentAgg(
                req.getClassId(),
                req.getModuleId(),
                fromTs,
                toTs,
                req.getScoreMin(),
                req.getScoreMax(),
                keyword
        );

        // ✅ Danh sách đầy đủ học viên đã làm bài (không filter theo mức độ)
        List<AtRiskStudentResponse> students = buildStudents(studentAgg);

        List<AtRiskStudentResponse> atRisk = buildAtRisk(studentAgg);

        List<TimeSeriesPointResponse> series = new ArrayList<>();
        for (AdminAnalyticsRepository.TimeSeriesRow r : analyticsRepository.getTimeSeries(
                req.getClassId(), req.getModuleId(), fromTs, toTs
        )) {
            TimeSeriesPointResponse p = new TimeSeriesPointResponse();
            p.setDate(r.getD());
            p.setAttempts(nvl(r.getAttempts()));
            p.setAvgScore(r.getAvgScore() == null ? 0.0 : r.getAvgScore());

            long attempts = nvl(r.getAttempts());
            long failed = nvl(r.getFailed());
            p.setFailRate(attempts == 0 ? 0.0 : (failed * 1.0 / attempts));

            series.add(p);
        }

        AdminAnalyticsOverviewResponse res = new AdminAnalyticsOverviewResponse();
        res.setTotalAttempts(totalAttempts);
        res.setTotalStudents(totalStudents);
        res.setAvgScore(avgScore);
        res.setPassRate(passRate);
        res.setFailRate(failRate);
        res.setStudents(students);
        res.setAtRiskStudents(atRisk);
        res.setTimeSeries(series);
        return res;
    }

    /**
     * Build danh sách đầy đủ học viên (không filter theo mức độ).
     * FE dùng field này để render bảng "Danh sách học viên đã làm bài".
     *
     * Lưu ý: "Tiến độ" được hiểu là passRate = 1 - failRate.
     */
    private List<AtRiskStudentResponse> buildStudents(List<AdminAnalyticsRepository.StudentAggRow> rows) {
        if (rows == null || rows.isEmpty()) return List.of();

        final double FAIL_RATE_HIGH = 0.50;
        List<AtRiskStudentResponse> out = new ArrayList<>();

        for (AdminAnalyticsRepository.StudentAggRow r : rows) {
            long attempts = nvl(r.getAttemptsCount());
            if (attempts <= 0) continue;

            long failed = nvl(r.getFailedCount());
            double failRate = attempts == 0 ? 0.0 : (failed * 1.0 / attempts);
            double passRate = 1.0 - failRate;
            double avgScore = r.getAvgScore() == null ? 0.0 : r.getAvgScore();

            String level = classifyLevel(avgScore); // ✅ TOT/TRUNG_BINH/YEU

            // riskScore dùng để sort ưu tiên (nguy hiểm -> đạt)
            List<String> reasons = new ArrayList<>();
            int riskScore = 0;

            if ("YEU".equals(level)) {
                reasons.add("Điểm dưới 50 (Yếu)");
                riskScore += 60;
            } else if ("TRUNG_BINH".equals(level)) {
                reasons.add("Điểm từ 50 đến dưới 80 (Trung bình)");
                riskScore += 30;
            } else {
                reasons.add("Điểm từ 80 trở lên (Tốt)");
                riskScore += 5;
            }

            if (failRate >= FAIL_RATE_HIGH) {
                reasons.add("Tỷ lệ trượt cao");
                riskScore += 40;
            }

            if (attempts >= 8 && !"TOT".equals(level)) {
                reasons.add("Làm nhiều bài nhưng kết quả chưa cải thiện");
                riskScore += 20;
            }

            if (riskScore > 100) riskScore = 100;

            AtRiskStudentResponse dto = new AtRiskStudentResponse();
            dto.setUserId(r.getUserId());
            dto.setFullName(r.getFullName());
            dto.setEmail(r.getEmail());
            dto.setAttemptsCount(attempts);
            dto.setAvgScore(avgScore);
            dto.setFailRate(failRate);
            dto.setPassRate(passRate);
            dto.setLastAttemptAt(r.getLastAttemptAt() == null ? null : r.getLastAttemptAt().format(ISO));
            dto.setRiskScore(riskScore);
            dto.setRiskLevel(level);
            dto.setReasons(reasons.toArray(new String[0]));

            out.add(dto);
        }

        // sort: ưu tiên riskScore cao, rồi avgScore thấp
        out.sort(Comparator
                .comparing(AtRiskStudentResponse::getRiskScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AtRiskStudentResponse::getAvgScore, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        return out;
    }

    /**
     * At-risk theo thang mới:
     * - >= 80: TỐT (không đưa vào atRiskStudents)
     * - 50-80: TRUNG BÌNH (đưa vào nếu có dấu hiệu cần theo dõi)
     * - < 50: YẾU (đưa vào, ưu tiên cao)
     *
     * riskScore dùng để sort ưu tiên (0..100).
     * riskLevel trả đúng 3 mức: TOT / TRUNG_BINH / YEU.
     */
    private List<AtRiskStudentResponse> buildAtRisk(List<AdminAnalyticsRepository.StudentAggRow> rows) {
        if (rows == null || rows.isEmpty()) return List.of();

        final double FAIL_RATE_HIGH = 0.50;

        List<AtRiskStudentResponse> out = new ArrayList<>();

        for (AdminAnalyticsRepository.StudentAggRow r : rows) {
            long attempts = nvl(r.getAttemptsCount());
            if (attempts < MIN_ATTEMPTS) continue;

            long failed = nvl(r.getFailedCount());
            double failRate = attempts == 0 ? 0.0 : (failed * 1.0 / attempts);
            double passRate = 1.0 - failRate;
            double avgScore = r.getAvgScore() == null ? 0.0 : r.getAvgScore();

            // ===== classification =====
            String level = classifyLevel(avgScore);

            // atRiskStudents: loại TỐT trừ khi failRate quá cao (case bất thường)
            boolean isGood = "TOT".equals(level);
            if (isGood && failRate < FAIL_RATE_HIGH) {
                continue;
            }

            List<String> reasons = new ArrayList<>();
            int riskScore = 0;

            // ===== risk scoring (tập trung vào "cần can thiệp") =====
            if ("YEU".equals(level)) {
                reasons.add("Điểm dưới 50 (Yếu)");
                riskScore += 60;
            } else if ("TRUNG_BINH".equals(level)) {
                reasons.add("Điểm từ 50 đến dưới 80 (Trung bình)");
                riskScore += 30;
            } else {
                // TOT nhưng failRate cao -> flag
                reasons.add("Điểm tốt nhưng tỷ lệ trượt cao bất thường");
                riskScore += 25;
            }

            if (failRate >= FAIL_RATE_HIGH) {
                reasons.add("Tỷ lệ trượt cao");
                riskScore += 40;
            }

            if (attempts >= 8 && !"TOT".equals(level)) {
                reasons.add("Làm nhiều bài nhưng kết quả chưa cải thiện");
                riskScore += 20;
            }

            if (riskScore > 100) riskScore = 100;

            AtRiskStudentResponse dto = new AtRiskStudentResponse();
            dto.setUserId(r.getUserId());
            dto.setFullName(r.getFullName());
            dto.setEmail(r.getEmail());
            dto.setAttemptsCount(attempts);
            dto.setAvgScore(avgScore);
            dto.setFailRate(failRate);
            dto.setPassRate(passRate);
            dto.setLastAttemptAt(r.getLastAttemptAt() == null ? null : r.getLastAttemptAt().format(ISO));
            dto.setRiskScore(riskScore);
            dto.setRiskLevel(level); // ✅ TOT/TRUNG_BINH/YEU
            dto.setReasons(reasons.toArray(new String[0]));

            out.add(dto);
        }

        // sort: ưu tiên riskScore cao, rồi avgScore thấp
        out.sort(Comparator
                .comparing(AtRiskStudentResponse::getRiskScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AtRiskStudentResponse::getAvgScore, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        return out.size() > 20 ? out.subList(0, 20) : out;
    }

    private String classifyLevel(double avgScore) {
        if (avgScore >= GOOD_SCORE) return "TOT";
        if (avgScore >= AVERAGE_SCORE) return "TRUNG_BINH";
        return "YEU";
    }

    private LocalDateTime parseIso(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        return LocalDateTime.parse(s.trim(), ISO);
    }

    private String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private long nvl(Long v) { return v == null ? 0L : v; }
}
