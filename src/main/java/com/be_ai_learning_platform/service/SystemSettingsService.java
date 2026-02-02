package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.UpdateSystemSettingsRequest;
import com.be_ai_learning_platform.dto.response.SystemSettingsResponse;
import com.be_ai_learning_platform.entity.SystemSettings;

public interface SystemSettingsService {

    // internal (entity)
    SystemSettings getSettings();

    // admin API
    SystemSettingsResponse get();
    SystemSettingsResponse update(UpdateSystemSettingsRequest req);

    // for other services
    int getPassScore();
    double getMinutesPerQuestion();
    boolean isEmailNotificationEnabled();
    String[] getAdminEmails();
}
