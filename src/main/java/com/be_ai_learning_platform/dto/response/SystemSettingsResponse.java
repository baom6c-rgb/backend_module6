package com.be_ai_learning_platform.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SystemSettingsResponse {

    private Integer passScore;
    private Double minutesPerQuestion;
    private Integer retestCooldownMinutes;

    private Boolean emailNotificationsEnabled;
    private String adminEmails;

    // ===== Monthly Admin Report =====
    private Boolean monthlyReportEnabled;
    /** 1..31, 0 = last day */
    private Integer monthlyReportDayOfMonth;
    /** HH:mm */
    private String monthlyReportTime;
    /** IANA zone id, e.g. Asia/Ho_Chi_Minh */
    private String monthlyReportTimeZone;
    /** YYYY-MM đã gửi gần nhất */
    private String monthlyReportLastSentYearMonth;

    private LocalDateTime updatedAt;
}
