package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.UpdateAiSettingsRequest;
import com.be_ai_learning_platform.dto.request.UpdateSystemSettingsRequest;
import com.be_ai_learning_platform.dto.response.AiSettingsResponse;
import com.be_ai_learning_platform.dto.response.SystemSettingsResponse;
import com.be_ai_learning_platform.entity.SystemSettings;

public interface SystemSettingsService {

    // internal (entity)
    SystemSettings getSettings();

    // admin API
    SystemSettingsResponse get();
    SystemSettingsResponse update(UpdateSystemSettingsRequest req);

    // ===== AI settings (Admin tab: Model AI) =====
    AiSettingsResponse getAi();
    AiSettingsResponse updateAi(UpdateAiSettingsRequest req);
    AiSettingsResponse clearAiApiKey();

    // ===== for AI clients =====
    String requireAiApiKey();
    String getAiProvider();
    String getAiModel();
    double getAiTemperature();
    boolean isAiEnabled();

    // for other services
    int getPassScore();
    double getMinutesPerQuestion();
    int getRetestCooldownMinutes();
    boolean isEmailNotificationEnabled();
    String[] getAdminEmails();

    // ===== Practice settings (NEW) =====
    int getMcqQuestionCount();
    int getEssayQuestionCount();
}
