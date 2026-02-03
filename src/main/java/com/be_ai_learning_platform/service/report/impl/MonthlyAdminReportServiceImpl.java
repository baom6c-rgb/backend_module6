package com.be_ai_learning_platform.service.report.impl;

import com.be_ai_learning_platform.entity.ExamAttempt;
import com.be_ai_learning_platform.entity.SystemSettings;
import com.be_ai_learning_platform.repository.ExamAttemptRepository;
import com.be_ai_learning_platform.repository.SystemSettingsRepository;
import com.be_ai_learning_platform.service.mail.MailService;
import com.be_ai_learning_platform.service.report.MonthlyAdminReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyAdminReportServiceImpl implements MonthlyAdminReportService {

    private static final Long SETTINGS_ID = 1L;

    private final ExamAttemptRepository examAttemptRepository;
    private final SystemSettingsRepository settingsRepository;
    private final MailService mailService;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    @Value("${app.report.storageDir:uploads/reports}")
    private String storageDir;

    @Override
    public void generateAndSendMonthlyReport(YearMonth month, ZoneId zone) {
        SystemSettings settings = settingsRepository.findById(SETTINGS_ID).orElse(null);
        if (settings == null) return;

        // email notification master switch
        if (!Boolean.TRUE.equals(settings.getEmailNotificationsEnabled())) {
            log.info("Email notifications disabled => skip monthly report");
            return;
        }

        // recipients from settings
        List<String> recipients = parseEmails(settings.getAdminEmails());
        if (recipients.isEmpty()) {
            log.warn("No adminEmails configured => skip monthly report");
            return;
        }

        ZoneId z = (zone != null ? zone : safeZone(settings.getMonthlyReportTimeZone()));

        // Range theo tháng (zone-aware)
        ZonedDateTime startZ = month.atDay(1).atStartOfDay(z);
        ZonedDateTime endZ = month.plusMonths(1).atDay(1).atStartOfDay(z).minusNanos(1);
        LocalDateTime from = startZ.toLocalDateTime();
        LocalDateTime to = endZ.toLocalDateTime();

        // ===== US21 statistics (DB) =====
        long totalAttempts = examAttemptRepository.countSubmittedInRange(from, to);

        Object[] topAttempt = firstOrNull(examAttemptRepository.findTopStudentByAttemptCountInRange(from, to));
        Object[] bestAvg = firstOrNull(examAttemptRepository.findBestStudentByAvgScoreInRange(from, to));
        Object[] worstAvg = firstOrNull(examAttemptRepository.findWorstStudentByAvgScoreInRange(from, to));

        StudentStat top = mapTopAttempt(topAttempt); // user + attempt_count
        StudentStat best = mapAvgScore(bestAvg);     // user + avg_score
        StudentStat worst = mapAvgScore(worstAvg);   // user + avg_score

        // fetch attempts for excel export (with user/exam/module/class)
        List<ExamAttempt> attempts = examAttemptRepository.findSubmittedAttemptsInRangeWithDetails(from, to);

        // export excel
        File excel = exportExcel(month, attempts);
        String downloadUrl = buildDownloadUrl(excel);

        // email body (HTML)
        String subject = String.format("Báo cáo tháng %s - AI Learning Platform", month);
        String bodyHtml = buildEmailBodyHtml(month, totalAttempts, top, best, worst, downloadUrl);

        // send mail (HTML + attachment)
        mailService.sendMonthlyReportEmail(recipients, subject, bodyHtml, excel);

        log.info("Monthly report sent to {} for {}", recipients, month);
    }

    // ===================== Excel =====================

    private File exportExcel(YearMonth month, List<ExamAttempt> attempts) {
        try {
            File dir = new File(storageDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException("Cannot create storageDir: " + storageDir);
            }

            String filename = "monthly_report_" + month + ".xlsx";
            File out = new File(dir, filename);

            try (XSSFWorkbook wb = new XSSFWorkbook()) {
                Sheet sheet = wb.createSheet("Report");

                int r = 0;
                Row header = sheet.createRow(r++);
                header.createCell(0).setCellValue("AttemptId");
                header.createCell(1).setCellValue("UserId");
                header.createCell(2).setCellValue("Email");
                header.createCell(3).setCellValue("FullName");
                header.createCell(4).setCellValue("ExamId");
                header.createCell(5).setCellValue("ExamType"); // ✅ Exam không có title
                header.createCell(6).setCellValue("StartTime");
                header.createCell(7).setCellValue("SubmitTime");
                header.createCell(8).setCellValue("Score(0-100)");
                header.createCell(9).setCellValue("AnswersJson");
                header.createCell(10).setCellValue("AiFeedback");

                for (ExamAttempt a : attempts) {
                    Row row = sheet.createRow(r++);

                    row.createCell(0).setCellValue(a.getId() == null ? "" : String.valueOf(a.getId()));
                    row.createCell(1).setCellValue(a.getUser() == null ? "" : String.valueOf(a.getUser().getId()));
                    row.createCell(2).setCellValue(a.getUser() == null ? "" : safe(a.getUser().getEmail()));
                    row.createCell(3).setCellValue(a.getUser() == null ? "" : safe(a.getUser().getFullName()));

                    row.createCell(4).setCellValue(a.getExam() == null ? "" : String.valueOf(a.getExam().getId()));
                    row.createCell(5).setCellValue(
                            a.getExam() == null || a.getExam().getType() == null
                                    ? ""
                                    : String.valueOf(a.getExam().getType())
                    );

                    row.createCell(6).setCellValue(a.getStartTime() == null ? "" : a.getStartTime().toString());
                    row.createCell(7).setCellValue(a.getSubmitTime() == null ? "" : a.getSubmitTime().toString());
                    row.createCell(8).setCellValue(a.getScore() == null ? 0 : a.getScore());

                    // chi tiết bài làm + feedback
                    row.createCell(9).setCellValue(a.getAnswersJson() == null ? "" : a.getAnswersJson());
                    row.createCell(10).setCellValue(a.getAiFeedback() == null ? "" : a.getAiFeedback());
                }

                for (int i = 0; i <= 10; i++) sheet.autoSizeColumn(i);

                try (FileOutputStream fos = new FileOutputStream(out)) {
                    wb.write(fos);
                }
            }

            return out;
        } catch (Exception ex) {
            throw new RuntimeException("Export excel failed", ex);
        }
    }

    private String buildDownloadUrl(File excel) {
        // file nằm trong uploads/reports => expose qua static resource /uploads/**
        String path = excel.getPath().replace("\\", "/");
        String base = backendUrl.replaceAll("/$", "");

        if (path.startsWith("uploads/")) {
            return base + "/" + path;
        }
        return base + "/uploads/reports/" + excel.getName();
    }

    // ===================== Email body =====================

    private String buildEmailBodyHtml(
            YearMonth month,
            long totalAttempts,
            StudentStat top,
            StudentStat best,
            StudentStat worst,
            String downloadUrl
    ) {
        return """
                <h2>Báo cáo tháng %s</h2>
                <ul>
                  <li><b>Tổng số bài làm trong tháng:</b> %d</li>
                  <li><b>Học viên làm nhiều nhất:</b> %s</li>
                  <li><b>Học viên kết quả tốt nhất (AVG):</b> %s</li>
                  <li><b>Học viên kết quả kém nhất (AVG):</b> %s</li>
                </ul>
                <p><b>Tải file Excel (kết quả + feedback chi tiết):</b>
                   <a href="%s">%s</a>
                </p>
                <p>(File Excel cũng được đính kèm trong email này.)</p>
                """.formatted(
                month,
                totalAttempts,
                top == null ? "—" : top.prettyMostAttempts(),
                best == null ? "—" : best.prettyAvgScore(),
                worst == null ? "—" : worst.prettyAvgScore(),
                downloadUrl, downloadUrl
        );
    }

    // ===================== Mapping helpers =====================

    /**
     * topAttempt query returns: [user_id, email, full_name, attempt_count]
     */
    private StudentStat mapTopAttempt(Object[] row) {
        if (row == null) return null;
        StudentStat s = new StudentStat();
        s.userId = toLong(row[0]);
        s.email = toStr(row[1]);
        s.fullName = toStr(row[2]);
        s.attemptCount = toLong(row[3]);
        return s;
    }

    /**
     * avgScore query returns: [user_id, email, full_name, avg_score]
     */
    private StudentStat mapAvgScore(Object[] row) {
        if (row == null) return null;
        StudentStat s = new StudentStat();
        s.userId = toLong(row[0]);
        s.email = toStr(row[1]);
        s.fullName = toStr(row[2]);
        s.avgScore = toDouble(row[3]);
        return s;
    }

    private Object[] firstOrNull(List<Object[]> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return null; }
    }

    private static String toStr(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    // ===================== Utilities =====================

    private List<String> parseEmails(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptyList();
        String[] parts = csv.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String e = p.trim();
            if (!e.isBlank()) out.add(e);
        }
        return out;
    }

    private ZoneId safeZone(String zoneId) {
        try {
            if (zoneId == null || zoneId.isBlank()) return ZoneId.of("Asia/Bangkok");
            return ZoneId.of(zoneId.trim());
        } catch (Exception e) {
            return ZoneId.of("Asia/Bangkok");
        }
    }

    // ===================== DTO internal =====================

    private static class StudentStat {
        Long userId;
        String email;
        String fullName;

        // for top attempts
        Long attemptCount;

        // for best/worst avg
        Double avgScore;

        String prettyMostAttempts() {
            String name = (fullName == null || fullName.isBlank()) ? "—" : fullName;
            String em = (email == null || email.isBlank()) ? "—" : email;
            long cnt = (attemptCount == null) ? 0 : attemptCount;
            return "%s (%s) - %d bài".formatted(name, em, cnt);
        }

        String prettyAvgScore() {
            String name = (fullName == null || fullName.isBlank()) ? "—" : fullName;
            String em = (email == null || email.isBlank()) ? "—" : email;
            double v = (avgScore == null) ? 0 : avgScore;
            return "%s (%s) - %.2f".formatted(name, em, v);
        }
    }
}
