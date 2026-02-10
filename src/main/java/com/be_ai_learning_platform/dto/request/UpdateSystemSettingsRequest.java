package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateSystemSettingsRequest {

    @NotNull
    @Min(0)
    @Max(100)
    private Integer passScore;

    @NotNull
    @Min(0)
    private Double minutesPerQuestion;

    @NotNull
    @Min(0)
    @Max(1440)
    private Integer retestCooldownMinutes; // minutes

    @NotNull
    private Boolean emailNotificationsEnabled;

    @NotNull
    private String adminEmails; // CSV: a@x.com,b@y.com

    // ===== Practice question distribution (NEW) =====
    @NotNull
    @Min(0)
    @Max(200)
    private Integer mcqQuestionCount;

    @NotNull
    @Min(0)
    @Max(200)
    private Integer essayQuestionCount;

    // ===== Monthly Admin Report =====
    private Boolean monthlyReportEnabled;

    /**
     * Ngày gửi trong tháng (1..31). Nếu = 0 -> ngày cuối cùng của tháng.
     */
    @Min(0)
    @Max(31)
    private Integer monthlyReportDayOfMonth;

    /**
     * Giờ gửi (HH:mm) theo timezone monthlyReportTimeZone.
     * Ví dụ: 23:59
     */
    private String monthlyReportTime;

    /**
     * Timezone IANA. Ví dụ: Asia/Bangkok
     */
    private String monthlyReportTimeZone;
}
