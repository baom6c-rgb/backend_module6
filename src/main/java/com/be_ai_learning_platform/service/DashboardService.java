package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.UserDashboardStatsDTO;

public interface DashboardService {
    UserDashboardStatsDTO getStats(Long userId);
}