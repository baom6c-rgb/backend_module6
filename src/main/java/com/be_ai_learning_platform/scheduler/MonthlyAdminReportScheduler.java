package com.be_ai_learning_platform.scheduler;

import com.be_ai_learning_platform.entity.ReportDispatchLog;
import com.be_ai_learning_platform.entity.SystemSettings;
import com.be_ai_learning_platform.repository.ReportDispatchLogRepository;
import com.be_ai_learning_platform.repository.SystemSettingsRepository;
import com.be_ai_learning_platform.service.report.MonthlyAdminReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyAdminReportScheduler {

    private static final Long SETTINGS_ID = 1L;
    private static final String REPORT_TYPE = "MONTHLY_ADMIN_REPORT";

    private final SystemSettingsRepository settingsRepository;
    private final ReportDispatchLogRepository dispatchLogRepository;
    private final MonthlyAdminReportService monthlyAdminReportService;

    // chạy mỗi phút
    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        SystemSettings s = settingsRepository.findById(SETTINGS_ID).orElse(null);
        if (s == null) return;

        // master switches
        if (!Boolean.TRUE.equals(s.getMonthlyReportEnabled())) return;
        if (!Boolean.TRUE.equals(s.getEmailNotificationsEnabled())) return;

        ZoneId zone = safeZone(s.getMonthlyReportTimeZone());
        ZonedDateTime now = ZonedDateTime.now(zone);

        LocalTime reportTime = (s.getMonthlyReportTime() == null) ? LocalTime.of(23, 59) : s.getMonthlyReportTime();
        if (now.toLocalTime().isBefore(reportTime)) return;

        // ==== match day-of-month ====
        Integer configured = s.getMonthlyReportDayOfMonth();
        int dayCfg = (configured == null) ? 0 : Math.max(0, Math.min(31, configured)); // 0..31
        int todayDom = now.getDayOfMonth();
        int lastDom = YearMonth.from(now).lengthOfMonth();

        boolean matchDay = (dayCfg == 0) ? (todayDom == lastDom) : (todayDom == dayCfg);
        if (!matchDay) return;

        // ==== idempotent per occurrence (YYYY-MM-DD @ HH:mm @ TZ) ====
        LocalDate today = now.toLocalDate();
        String runKey = today + "@" + reportTime + "@" + zone.getId(); // e.g. 2026-02-28@23:59@Asia/Bangkok

        if (dispatchLogRepository.existsByReportTypeAndRunKey(REPORT_TYPE, runKey)) {
            return; // đã gửi trong slot này rồi
        }

        try {
            // report content theo tháng hiện tại
            YearMonth currentMonth = YearMonth.from(now);
            monthlyAdminReportService.generateAndSendMonthlyReport(currentMonth, zone);

            ReportDispatchLog logRow = new ReportDispatchLog();
            logRow.setReportType(REPORT_TYPE);
            logRow.setRunKey(runKey);
            logRow.setSentAt(LocalDateTime.now());
            dispatchLogRepository.save(logRow);

            // OPTIONAL: chỉ để hiển thị "đã gửi gần nhất", không block nữa
            s.setMonthlyReportLastSentYearMonth(currentMonth.toString());
            s.setUpdatedAt(LocalDateTime.now());
            settingsRepository.save(s);

            log.info("Monthly admin report sent: {}", runKey);
        } catch (Exception ex) {
            log.error("Monthly admin report failed", ex);
        }
    }

    private ZoneId safeZone(String zoneId) {
        try {
            if (zoneId == null || zoneId.isBlank()) return ZoneId.of("Asia/Bangkok");
            return ZoneId.of(zoneId.trim());
        } catch (Exception e) {
            return ZoneId.of("Asia/Bangkok");
        }
    }
}
