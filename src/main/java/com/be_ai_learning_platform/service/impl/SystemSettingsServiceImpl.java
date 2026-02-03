package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.UpdateSystemSettingsRequest;
import com.be_ai_learning_platform.dto.response.SystemSettingsResponse;
import com.be_ai_learning_platform.entity.SystemSettings;
import com.be_ai_learning_platform.repository.SystemSettingsRepository;
import com.be_ai_learning_platform.service.SystemSettingsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SystemSettingsServiceImpl implements SystemSettingsService {

    private static final Long SETTINGS_ID = 1L;

    private final SystemSettingsRepository repo;

    // ===== DEFAULT (chỉ dùng để INIT lần đầu) =====
    @Value("${app.settings.default.passScore:80}")
    private int defaultPassScore;

    @Value("${app.settings.default.minutesPerQuestion:2.0}")
    private double defaultMinutesPerQuestion;

    @Value("${app.settings.default.retestCooldownMinutes:30}")
    private int defaultRetestCooldownMinutes;

    @Value("${app.settings.default.emailNotificationsEnabled:true}")
    private boolean defaultEmailEnabled;

    @Value("${app.settings.default.adminEmails:}")
    private String defaultAdminEmails;

    @PostConstruct
    public void initIfMissing() {
        if (repo.existsById(SETTINGS_ID)) return;

        SystemSettings s = new SystemSettings();
        s.setId(SETTINGS_ID);
        s.setPassScore(defaultPassScore);
        s.setMinutesPerQuestion(defaultMinutesPerQuestion);
        s.setRetestCooldownMinutes(defaultRetestCooldownMinutes);
        s.setEmailNotificationsEnabled(defaultEmailEnabled);
        s.setAdminEmails(normalizeEmails(defaultAdminEmails));
        s.setUpdatedAt(LocalDateTime.now());

        repo.save(s);
    }

    @Override
    @Transactional(readOnly = true)
    public SystemSettings getSettings() {
        return repo.findById(SETTINGS_ID)
                .orElseThrow(() -> new IllegalStateException("SystemSettings not initialized"));
    }

    // ===================== API for other services =====================

    @Override
    @Transactional(readOnly = true)
    public int getPassScore() {
        return getSettings().getPassScore();
    }

    @Override
    @Transactional(readOnly = true)
    public double getMinutesPerQuestion() {
        return getSettings().getMinutesPerQuestion();
    }

    @Override
    @Transactional(readOnly = true)
    public int getRetestCooldownMinutes() {
        Integer v = getSettings().getRetestCooldownMinutes();
        return v == null ? 0 : Math.max(0, v);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmailNotificationEnabled() {
        return Boolean.TRUE.equals(getSettings().getEmailNotificationsEnabled());
    }

    @Override
    @Transactional(readOnly = true)
    public String[] getAdminEmails() {
        String raw = getSettings().getAdminEmails();
        if (raw == null || raw.isBlank()) return new String[0];

        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }

    // ===================== Admin API (Controller) =====================

    @Override
    @Transactional(readOnly = true)
    public SystemSettingsResponse get() {
        return toResponse(getSettings());
    }

    @Override
    public SystemSettingsResponse update(UpdateSystemSettingsRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Request is null");
        }

        // business guardrails
        if (req.getMinutesPerQuestion() == null || req.getMinutesPerQuestion() <= 0) {
            throw new IllegalArgumentException("minutesPerQuestion must be > 0");
        }
        if (req.getPassScore() == null || req.getPassScore() < 0 || req.getPassScore() > 100) {
            throw new IllegalArgumentException("passScore must be 0..100");
        }
        if (req.getRetestCooldownMinutes() == null || req.getRetestCooldownMinutes() < 0 || req.getRetestCooldownMinutes() > 1440) {
            throw new IllegalArgumentException("retestCooldownMinutes must be 0..1440");
        }

        SystemSettings s = getSettings();

        s.setPassScore(req.getPassScore());
        s.setMinutesPerQuestion(req.getMinutesPerQuestion());
        s.setRetestCooldownMinutes(req.getRetestCooldownMinutes());
        s.setEmailNotificationsEnabled(Boolean.TRUE.equals(req.getEmailNotificationsEnabled()));
        s.setAdminEmails(normalizeEmails(req.getAdminEmails()));
        s.setUpdatedAt(LocalDateTime.now());

        repo.save(s);
        return toResponse(s);
    }

    // ===================== Mapping =====================

    private SystemSettingsResponse toResponse(SystemSettings s) {
        SystemSettingsResponse r = new SystemSettingsResponse();
        r.setPassScore(s.getPassScore());
        r.setMinutesPerQuestion(s.getMinutesPerQuestion());
        r.setRetestCooldownMinutes(s.getRetestCooldownMinutes());
        r.setEmailNotificationsEnabled(Boolean.TRUE.equals(s.getEmailNotificationsEnabled()));
        r.setAdminEmails(s.getAdminEmails());
        r.setUpdatedAt(s.getUpdatedAt());
        return r;
    }

    private String normalizeEmails(String raw) {
        if (raw == null) return "";
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(e -> !e.isBlank())
                .collect(Collectors.joining(","));
    }
}
