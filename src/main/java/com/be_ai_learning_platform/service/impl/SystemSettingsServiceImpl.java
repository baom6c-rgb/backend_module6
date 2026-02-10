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

    // ===== Practice defaults (NEW) =====
    @Value("${app.settings.default.practice.mcqQuestionCount:8}")
    private int defaultMcqQuestionCount;

    @Value("${app.settings.default.practice.essayQuestionCount:2}")
    private int defaultEssayQuestionCount;

    // ===== Monthly report defaults =====
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

        // ===== practice defaults (NEW) =====
        int mcq = Math.max(0, defaultMcqQuestionCount);
        int essay = Math.max(0, defaultEssayQuestionCount);
        if (mcq + essay <= 0) {
            mcq = 8;
            essay = 2;
        }
        s.setMcqQuestionCount(mcq);
        s.setEssayQuestionCount(essay);

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

    // ===== Practice getters (NEW) =====
    @Override
    @Transactional(readOnly = true)
    public int getMcqQuestionCount() {
        Integer v = getSettings().getMcqQuestionCount();
        return v == null ? 0 : Math.max(0, v);
    }

    @Override
    @Transactional(readOnly = true)
    public int getEssayQuestionCount() {
        Integer v = getSettings().getEssayQuestionCount();
        return v == null ? 0 : Math.max(0, v);
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

        // practice validation (NEW)
        if (req.getMcqQuestionCount() == null || req.getMcqQuestionCount() < 0) {
            throw new IllegalArgumentException("mcqQuestionCount must be >= 0");
        }
        if (req.getEssayQuestionCount() == null || req.getEssayQuestionCount() < 0) {
            throw new IllegalArgumentException("essayQuestionCount must be >= 0");
        }
        if (req.getMcqQuestionCount() + req.getEssayQuestionCount() <= 0) {
            throw new IllegalArgumentException("Total questions must be > 0");
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

        // practice updates (NEW)
        s.setMcqQuestionCount(Math.max(0, req.getMcqQuestionCount()));
        s.setEssayQuestionCount(Math.max(0, req.getEssayQuestionCount()));

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
            throw new IllegalStateException("AI is disabled by admin");
        }
        String key = s.getAiApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("AI api key is empty");
        }
        return key.trim();
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
        return t == null ? 0.0 : clamp01(t);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAiEnabled() {
        return Boolean.TRUE.equals(getSettings().getAiEnabled());
    }

    // ===================== mapping =====================

    private SystemSettingsResponse toResponse(SystemSettings s) {
        SystemSettingsResponse r = new SystemSettingsResponse();

        r.setPassScore(s.getPassScore());
        r.setMinutesPerQuestion(s.getMinutesPerQuestion());
        r.setRetestCooldownMinutes(s.getRetestCooldownMinutes());

        r.setEmailNotificationsEnabled(s.getEmailNotificationsEnabled());
        r.setAdminEmails(s.getAdminEmails());

        // practice (NEW)
        r.setMcqQuestionCount(s.getMcqQuestionCount());
        r.setEssayQuestionCount(s.getEssayQuestionCount());

        // monthly report
        r.setMonthlyReportEnabled(s.getMonthlyReportEnabled());
        r.setMonthlyReportDayOfMonth(s.getMonthlyReportDayOfMonth());
        r.setMonthlyReportTime(s.getMonthlyReportTime() == null ? null : s.getMonthlyReportTime().toString());
        r.setMonthlyReportTimeZone(s.getMonthlyReportTimeZone());
        r.setMonthlyReportLastSentYearMonth(s.getMonthlyReportLastSentYearMonth());

        r.setUpdatedAt(s.getUpdatedAt());
        return r;
    }

    /**
     * ✅ FIX: AiSettingsResponse là POJO (no-args) => set fields bằng setter.
     * - Không trả full secret. Chỉ trả masked key.
     */
    private AiSettingsResponse toAiResponse(SystemSettings s) {
        AiSettingsResponse r = new AiSettingsResponse();

        r.setAiProvider(normalizeProvider(s.getAiProvider()));
        r.setAiModel(normalizeModel(s.getAiModel()));
        r.setAiTemperature(s.getAiTemperature() == null ? 0.0 : clamp01(s.getAiTemperature()));
        r.setAiEnabled(Boolean.TRUE.equals(s.getAiEnabled()));

        r.setAiApiKeyMasked(maskApiKey(s.getAiApiKey()));
        r.setUpdatedAt(s.getUpdatedAt());

        return r;
    }

    // ===================== utils =====================

    private String normalizeEmails(String csv) {
        if (csv == null) return "";
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(x -> !x.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String normalizeProvider(String s) {
        if (s == null || s.isBlank()) return "GEMINI";
        return s.trim().toUpperCase();
    }

    private String normalizeModel(String s) {
        if (s == null || s.isBlank()) return "gemini-2.5-flash";
        return s.trim();
    }

    private double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private String normalizeOptionalSecret(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private String maskApiKey(String key) {
        if (key == null || key.isBlank()) return null;

        String k = key.trim();
        if (k.length() <= 6) return "***";

        return k.substring(0, 3) + "****" + k.substring(k.length() - 3);
    }
}
