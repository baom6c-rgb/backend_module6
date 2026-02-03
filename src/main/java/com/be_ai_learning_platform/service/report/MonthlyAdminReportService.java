package com.be_ai_learning_platform.service.report;

import java.time.YearMonth;
import java.time.ZoneId;

public interface MonthlyAdminReportService {

    /**
     * Generate thống kê tháng + export Excel + upload + send email cho admin.
     *
     * @param yearMonth Tháng cần báo cáo (yyyy-MM)
     * @param zone      Timezone để tính range thời gian chính xác
     */
    void generateAndSendMonthlyReport(YearMonth yearMonth, ZoneId zone);
}
