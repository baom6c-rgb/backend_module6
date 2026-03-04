package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.AI.GeminiStructuredClient;
import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;
import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.QuestionGenerationService;
import com.be_ai_learning_platform.service.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionGenerationServiceImpl implements QuestionGenerationService {

    private static final int MAX_EXTRACTED_CHARS = 6000;

    private static final int CALL_RETRY_TIMES = 1;
    private static final long CALL_RETRY_BACKOFF_MS = 700;

    private static final int PARSE_RETRY_TIMES = 1;
    private static final int PARSE_RETRY_TRIMMED_CHARS = 3000;

    private final LearningMaterialRepository materialRepo;
    private final UserRepository userRepo;
    private final GeminiStructuredClient ai;
    private final PromptBuilder promptBuilder;
    private final SystemSettingsService settingsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public QuestionGenerationServiceImpl(
            LearningMaterialRepository materialRepo,
            UserRepository userRepo,
            GeminiStructuredClient ai,
            PromptBuilder promptBuilder,
            SystemSettingsService settingsService
    ) {
        this.materialRepo = materialRepo;
        this.userRepo = userRepo;
        this.ai = ai;
        this.promptBuilder = promptBuilder;
        this.settingsService = settingsService;
    }

    // ===================== PRACTICE / DEFAULT =====================
    @Override
    public GenerateQuestionsResponse generate(String currentEmail, Long materialId, int ignoredNumberOfQuestions) {

        User me = userRepo.findByEmail(currentEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        LearningMaterial material = materialRepo.findByIdAndUser(materialId, me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        String trimmed = trimExtracted(material);

        int mcqCount = Math.max(0, settingsService.getMcqQuestionCount());
        int essayCount = Math.max(0, settingsService.getEssayQuestionCount());
        int totalQuestions = mcqCount + essayCount;

        if (totalQuestions <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System settings invalid: totalQuestions must be > 0");
        }

        String prompt = promptBuilder.buildPrompt(trimmed, mcqCount, essayCount);

        String json = callGeminiWithRetry(prompt, totalQuestions);

        GenerateQuestionsResponse res = parseWithRetry(
                json,
                trimmed,
                totalQuestions,
                mcqCount,
                essayCount,
                false,
                null
        );

        res.setMaterialId(materialId);
        res.setNumberOfQuestions(totalQuestions);

        QuestionValidator.validate(res, totalQuestions);
        QuestionDistributionValidator.validate(res, mcqCount, essayCount);

        return res;
    }

    // ===================== ADMIN (MCQ + ESSAY) =====================
    @Override
    public GenerateQuestionsResponse generate(String currentEmail, Long materialId, int mcqCount, int essayCount) {

        User me = userRepo.findByEmail(currentEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        LearningMaterial material = materialRepo.findByIdAndUser(materialId, me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        if (mcqCount < 0 || essayCount < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mcqCount/essayCount must be >= 0");
        }

        int totalQuestions = mcqCount + essayCount;
        if (totalQuestions <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total questions must be > 0");
        }

        String trimmed = trimExtracted(material);

        String prompt = promptBuilder.buildPrompt(trimmed, mcqCount, essayCount);

        String json = callGeminiWithRetry(prompt, totalQuestions);

        GenerateQuestionsResponse res = parseWithRetry(
                json,
                trimmed,
                totalQuestions,
                mcqCount,
                essayCount,
                false,
                null
        );

        res.setMaterialId(materialId);
        res.setNumberOfQuestions(totalQuestions);

        QuestionValidator.validate(res, totalQuestions);
        QuestionDistributionValidator.validate(res, mcqCount, essayCount);

        return res;
    }

    // ===================== RETEST =====================
    @Override
    public GenerateQuestionsResponse generateRetest(String currentEmail, Long materialId, int ignoredNumberOfQuestions, String focusText) {

        User me = userRepo.findByEmail(currentEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        LearningMaterial material = materialRepo.findByIdAndUser(materialId, me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        String trimmed = trimExtracted(material);

        int mcqCount = Math.max(0, settingsService.getMcqQuestionCount());
        int essayCount = Math.max(0, settingsService.getEssayQuestionCount());
        int totalQuestions = mcqCount + essayCount;

        if (totalQuestions <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System settings invalid: totalQuestions must be > 0");
        }

        String prompt = promptBuilder.buildRetestPrompt(trimmed, mcqCount, essayCount, focusText);

        String json = callGeminiWithRetry(prompt, totalQuestions);

        GenerateQuestionsResponse res = parseWithRetry(
                json,
                trimmed,
                totalQuestions,
                mcqCount,
                essayCount,
                true,
                focusText
        );

        res.setMaterialId(materialId);
        res.setNumberOfQuestions(totalQuestions);

        QuestionValidator.validate(res, totalQuestions);
        QuestionDistributionValidator.validate(res, mcqCount, essayCount);

        return res;
    }

    // ===================== COMMON HELPERS =====================

    private String trimExtracted(LearningMaterial material) {
        String extracted = material.getExtractedText();
        if (extracted == null || extracted.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Extracted text is empty");
        }

        return extracted.length() > MAX_EXTRACTED_CHARS
                ? extracted.substring(0, MAX_EXTRACTED_CHARS)
                : extracted;
    }

    private GenerateQuestionsResponse parseWithRetry(
            String json,
            String trimmed,
            int totalQuestions,
            int mcqCount,
            int essayCount,
            boolean isRetest,
            String focusText
    ) {
        Exception last = null;
        String currentJson = json;

        for (int i = 0; i <= PARSE_RETRY_TIMES; i++) {
            try {
                return objectMapper.readValue(currentJson, GenerateQuestionsResponse.class);
            } catch (Exception e) {
                last = e;

                if (i < PARSE_RETRY_TIMES) {
                    String shorter = trimmed.length() > PARSE_RETRY_TRIMMED_CHARS
                            ? trimmed.substring(0, PARSE_RETRY_TRIMMED_CHARS)
                            : trimmed;

                    String retryPrompt = isRetest
                            ? promptBuilder.buildRetestPrompt(shorter, mcqCount, essayCount, focusText)
                            : promptBuilder.buildPrompt(shorter, mcqCount, essayCount);

                    currentJson = callGeminiWithRetry(retryPrompt, totalQuestions);
                    continue;
                }

                String preview = currentJson == null ? "" : currentJson;
                preview = preview.length() > 800 ? preview.substring(0, 800) + "..." : preview;
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI output invalid JSON (after retry): " + preview,
                        last
                );
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI output invalid JSON", last);
    }

    private String callGeminiWithRetry(String prompt, int totalQuestions) {
        Exception last = null;

        for (int i = 0; i <= CALL_RETRY_TIMES; i++) {
            try {
                return ai.generateJsonBySchema(prompt, totalQuestions);
            } catch (Exception e) {
                last = e;

                String msg = safeMsg(e);

                if (isQuotaOrRateLimit(msg)) {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "AI service is temporarily unavailable (quota/rate limit). Please try again later.",
                            e
                    );
                }

                if (i < CALL_RETRY_TIMES) {
                    sleep(CALL_RETRY_BACKOFF_MS);
                    continue;
                }

                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI request failed", e);
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI request failed", last);
    }

    private boolean isQuotaOrRateLimit(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return m.contains("429")
                || m.contains("quota")
                || m.contains("rate limit")
                || m.contains("exceeded your current quota")
                || m.contains("limit: 0");
    }

    private String safeMsg(Exception e) {
        try {
            return e.getMessage() == null ? "" : e.getMessage();
        } catch (Exception ignore) {
            return "";
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}