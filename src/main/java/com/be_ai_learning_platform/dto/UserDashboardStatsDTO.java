package com.be_ai_learning_platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDashboardStatsDTO {
    private String greeting;
    private String suggestion; // Đảm bảo đã có dòng này
    private long completedLessons;
    private double onlineTime;
    private double averageScore;
    private int rank;
    private int totalStudents;
    private long passedLessons;  // Số bài đạt yêu cầu (điểm >= ngưỡng pass)
    private long failedLessons;  // Số bài chưa đạt (điểm < ngưỡng pass)
}
