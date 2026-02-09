package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.UpdateAiSettingsRequest;
import com.be_ai_learning_platform.dto.request.UpdateSystemSettingsRequest;
import com.be_ai_learning_platform.dto.response.AiSettingsResponse;
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
import java.time.LocalTime;
import java.time.ZoneId;
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

    @Value("${app.settings.default.monthlyReport.enabled:false}")
    private boolean defaultMonthlyReportEnabled;

    /**
     * 0 = last day
     */
    @Value("${app.settings.default.monthlyReport.dayOfMonth:0}")
    private int defaultMonthlyReportDayOfMonth;

    /**
     * HH:mm
     */
    @Value("${app.settings.default.monthlyReport.time:23:59}")
    private String defaultMonthlyReportTime;

    @Value("${app.settings.default.monthlyReport.timeZone:Asia/Bangkok}")
    private String defaultMonthlyReportTimeZone;

    // ===== AI DEFAULTS (chỉ dùng để INIT lần đầu) =====
    @Value("${app.settings.default.ai.provider:GEMINI}")
    private String defaultAiProvider;

    @Value("${app.settings.default.ai.model:gemini-2.5-flash}")
    private String defaultAiModel;

    @Value("${app.settings.default.ai.temperature:0.0}")
    private double defaultAiTemperature;

    @Value("${app.settings.default.ai.enabled:true}")
    private boolean defaultAiEnabled;

    /**
     * Có thể set từ ENV để init lần đầu (không bắt buộc).
     * Sau đó admin có thể đổi trong DB mà không cần restart.
     */
    @Value("${app.settings.default.ai.apiKey:}")
    private String defaultAiApiKey;

    @PostConstruct
    public void initIfMissing() {
        if (repo.existsById(SETTINGS_ID)) return;

        SystemSettings s = new SystemSettings();
        s.setId(SETTINGS_ID);

        // ===== base =====
        s.setPassScore(defaultPassScore);
        s.setMinutesPerQuestion(defaultMinutesPerQuestion);
        s.setRetestCooldownMinutes(defaultRetestCooldownMinutes);
        s.setEmailNotificationsEnabled(defaultEmailEnabled);
        s.setAdminEmails(normalizeEmails(defaultAdminEmails));

        // ===== monthly report defaults =====
        s.setMonthlyReportEnabled(defaultMonthlyReportEnabled);

        int dom = Math.max(0, Math.min(31, defaultMonthlyReportDayOfMonth));
        s.setMonthlyReportDayOfMonth(dom);

        try {
            s.setMonthlyReportTime(LocalTime.parse(defaultMonthlyReportTime));
        } catch (Exception ex) {
            s.setMonthlyReportTime(LocalTime.of(23, 59));
        }

        String tz = (defaultMonthlyReportTimeZone == null || defaultMonthlyReportTimeZone.isBlank())
                ? "Asia/Bangkok"
                : defaultMonthlyReportTimeZone.trim();
        try {
            ZoneId.of(tz);
        } catch (Exception ex) {
            tz = "Asia/Bangkok";
        }
        s.setMonthlyReportTimeZone(tz);

        s.setMonthlyReportLastSentYearMonth(null);

        // ===== AI defaults =====
        s.setAiProvider(normalizeProvider(defaultAiProvider));
        s.setAiModel(normalizeModel(defaultAiModel));
        s.setAiTemperature(clamp01(defaultAiTemperature));
        s.setAiEnabled(defaultAiEnabled);
        s.setAiApiKey(normalizeOptionalSecret(defaultAiApiKey));

        s.setUpdatedAt(LocalDateTime.now());
        repo.save(s);
    }

    @Override
    @Transactional(readOnly = true)
    public SystemSettings getSettings() {
        return repo.findById(SETTINGS_ID)
                .orElseThrow(() -> new IllegalStateException("SystemSettings not initialized"));
    }

    // ===================== for other services =====================

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

    // ===================== Admin API (base settings) =====================

    @Override
    @Transactional(readOnly = true)
    public SystemSettingsResponse get() {
        return toResponse(getSettings());
    }

    @Override
    public SystemSettingsResponse update(UpdateSystemSettingsRequest req) {
        if (req == null) throw new IllegalArgumentException("Request is null");

        // guardrails (base)
        if (req.getMinutesPerQuestion() == null || req.getMinutesPerQuestion() <= 0) {
            throw new IllegalArgumentException("minutesPerQuestion must be > 0");
        }
        if (req.getPassScore() == null || req.getPassScore() < 0 || req.getPassScore() > 100) {
            throw new IllegalArgumentException("passScore must be 0..100");
        }
        if (req.getRetestCooldownMinutes() == null || req.getRetestCooldownMinutes() < 0 || req.getRetestCooldownMinutes() > 1440) {
            throw new IllegalArgumentException("retestCooldownMinutes must be 0..1440");
        }

        // monthly optional validation
        Integer dayOfMonth = req.getMonthlyReportDayOfMonth();
        if (dayOfMonth != null && (dayOfMonth < 0 || dayOfMonth > 31)) {
            throw new IllegalArgumentException("monthlyReportDayOfMonth must be 0..31 (0 = last day)");
        }

        LocalTime monthlyTime = null;
        if (req.getMonthlyReportTime() != null && !req.getMonthlyReportTime().isBlank()) {
            try {
                monthlyTime = LocalTime.parse(req.getMonthlyReportTime());
            } catch (Exception ex) {
                throw new IllegalArgumentException("monthlyReportTime must be in HH:mm format");
            }
        }

        String tz = req.getMonthlyReportTimeZone();
        if (tz != null) {
            tz = tz.trim();
            if (tz.isBlank()) tz = null;
            else {
                try {
                    ZoneId.of(tz);
                } catch (Exception ex) {
                    throw new IllegalArgumentException("monthlyReportTimeZone is invalid IANA zone id");
                }
            }
        }

        SystemSettings s = getSettings();

        // base updates
        s.setPassScore(req.getPassScore());
        s.setMinutesPerQuestion(req.getMinutesPerQuestion());
        s.setRetestCooldownMinutes(req.getRetestCooldownMinutes());
        s.setEmailNotificationsEnabled(Boolean.TRUE.equals(req.getEmailNotificationsEnabled()));
        s.setAdminEmails(normalizeEmails(req.getAdminEmails()));

        // monthly updates (optional)
        if (req.getMonthlyReportEnabled() != null) {
            s.setMonthlyReportEnabled(Boolean.TRUE.equals(req.getMonthlyReportEnabled()));
        }
        if (dayOfMonth != null) {
            s.setMonthlyReportDayOfMonth(dayOfMonth);
        }
        if (monthlyTime != null) {
            s.setMonthlyReportTime(monthlyTime);
        }
        if (tz != null) {
            s.setMonthlyReportTimeZone(tz);
        }

        s.setUpdatedAt(LocalDateTime.now());
        repo.save(s);

        return toResponse(s);
    }

    // ===================== AI Settings (Admin tab: Model AI) =====================

    @Override
    @Transactional(readOnly = true)
    public AiSettingsResponse getAi() {
        return toAiResponse(getSettings());
    }

    @Override
    public AiSettingsResponse updateAi(UpdateAiSettingsRequest req) {
        if (req == null) throw new IllegalArgumentException("Request is null");

        String provider = normalizeProvider(req.aiProvider());
        String model = normalizeModel(req.aiModel());
        Double temp = req.aiTemperature();
        Boolean enabled = req.aiEnabled();

        // currently only GEMINI is implemented
        if (!"GEMINI".equals(provider)) {
            throw new IllegalArgumentException("aiProvider not supported: " + provider);
        }
        if (temp == null) {
            throw new IllegalArgumentException("aiTemperature is required");
        }
        if (temp < 0 || temp > 1) {
            throw new IllegalArgumentException("aiTemperature must be 0..1");
        }
        if (enabled == null) {
            throw new IllegalArgumentException("aiEnabled is required");
        }

        SystemSettings s = getSettings();

        s.setAiProvider(provider);
        s.setAiModel(model);
        s.setAiTemperature(clamp01(temp));
        s.setAiEnabled(Boolean.TRUE.equals(enabled));

        // ✅ only update key if admin enters a new key
        String newKey = normalizeOptionalSecret(req.aiApiKey());
        if (newKey != null) {
            s.setAiApiKey(newKey);
        }

        s.setUpdatedAt(LocalDateTime.now());
        repo.save(s);

        return toAiResponse(s);
    }

    // ===================== For AI clients =====================

    @Override
    @Transactional(readOnly = true)
    public String requireAiApiKey() {
        SystemSettings s = getSettings();
        if (!Boolean.TRUE.equals(s.getAiEnabled())) {
            throw new IllegalStateException("AI is disabled by system settings");
        }
        String key = safeTrim(s.getAiApiKey());
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("AI apiKey is missing (Admin > Settings > Model AI)");
        }
        return key;
    }

    @Override
    @Transactional(readOnly = true)
    public String getAiProvider() {
        return normalizeProvider(getSettings().getAiProvider());
    }

    @Override
    @Transactional(readOnly = true)
    public String getAiModel() {
        return normalizeModel(getSettings().getAiModel());
    }

    @Override
    @Transactional(readOnly = true)
    public double getAiTemperature() {
        Double t = getSettings().getAiTemperature();
        return clamp01(t == null ? 0.0 : t);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAiEnabled() {
        return Boolean.TRUE.equals(getSettings().getAiEnabled());
    }

    // ===================== Mapping =====================

    private SystemSettingsResponse toResponse(SystemSettings s) {
        SystemSettingsResponse r = new SystemSettingsResponse();
        r.setPassScore(s.getPassScore());
        r.setMinutesPerQuestion(s.getMinutesPerQuestion());
        r.setRetestCooldownMinutes(s.getRetestCooldownMinutes());
        r.setEmailNotificationsEnabled(Boolean.TRUE.equals(s.getEmailNotificationsEnabled()));
        r.setAdminEmails(s.getAdminEmails());

        r.setMonthlyReportEnabled(Boolean.TRUE.equals(s.getMonthlyReportEnabled()));
        r.setMonthlyReportDayOfMonth(s.getMonthlyReportDayOfMonth());
        r.setMonthlyReportTime(s.getMonthlyReportTime() == null ? "23:59" : s.getMonthlyReportTime().toString());
        r.setMonthlyReportTimeZone(s.getMonthlyReportTimeZone());
        r.setMonthlyReportLastSentYearMonth(s.getMonthlyReportLastSentYearMonth());
        r.setUpdatedAt(s.getUpdatedAt());
        return r;
    }

    private AiSettingsResponse toAiResponse(SystemSettings s) {
        AiSettingsResponse r = new AiSettingsResponse();
        r.setAiProvider(normalizeProvider(s.getAiProvider()));
        r.setAiApiKeyMasked(maskKey(s.getAiApiKey()));
        r.setAiModel(normalizeModel(s.getAiModel()));
        r.setAiTemperature(clamp01(s.getAiTemperature() == null ? 0.0 : s.getAiTemperature()));
        r.setAiEnabled(Boolean.TRUE.equals(s.getAiEnabled()));
        r.setUpdatedAt(s.getUpdatedAt());
        return r;
    }

    // ===================== Helpers =====================

    private String normalizeEmails(String raw) {
        if (raw == null) return "";
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(e -> !e.isBlank())
                .collect(Collectors.joining(","));
    }

    private String safeTrim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private String normalizeProvider(String raw) {
        String p = safeTrim(raw);
        if (p == null) return "GEMINI";
        return p.toUpperCase();
    }

    private String normalizeModel(String raw) {
        String m = safeTrim(raw);
        if (m == null) return "gemini-1.5-pro";
        return m;
    }

    /**
     * If blank => return null (meaning "no update" in updateAi).
     */
    private String normalizeOptionalSecret(String raw) {
        String t = safeTrim(raw);
        return (t == null) ? null : t;
    }

    private String maskKey(String key) {
        String k = safeTrim(key);
        if (k == null) return null;
        if (k.length() < 8) return "****";
        String head = k.substring(0, 3);
        String tail = k.substring(k.length() - 3);
        return head + "****" + tail;
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
