package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.ReportDispatchLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportDispatchLogRepository extends JpaRepository<ReportDispatchLog, Long> {
    boolean existsByReportTypeAndRunKey(String reportType, String runKey);
}
