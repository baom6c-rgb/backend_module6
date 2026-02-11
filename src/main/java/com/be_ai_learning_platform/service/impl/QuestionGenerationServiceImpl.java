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

    @Override
    public GenerateQuestionsResponse generate(String currentEmail, Long materialId, int ignoredNumberOfQuestions) {

        User me = userRepo.findByEmail(currentEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        LearningMaterial material = materialRepo.findByIdAndUser(materialId, me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        String extracted = material.getExtractedText();
        if (extracted == null || extracted.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Extracted text is empty");
        }

        String trimmed = extracted.length() > MAX_EXTRACTED_CHARS
                ? extracted.substring(0, MAX_EXTRACTED_CHARS)
                : extracted;

        int mcqCount = Math.max(0, settingsService.getMcqQuestionCount());
        int essayCount = Math.max(0, settingsService.getEssayQuestionCount());
        int totalQuestions = mcqCount + essayCount;

        if (totalQuestions <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System settings invalid: totalQuestions must be > 0");
        }

        String prompt = promptBuilder.buildPrompt(trimmed, mcqCount, essayCount);

        // 1) Call AI (with retry for transient/quota)
        String json = callGeminiWithRetry(prompt, totalQuestions);

        // 2) Parse JSON (with retry if JSON bị cắt/ngắt)
        GenerateQuestionsResponse res = parseWithRetry(json, trimmed, materialId, totalQuestions, mcqCount, essayCount);

        // meta
        res.setMaterialId(materialId);
        res.setNumberOfQuestions(totalQuestions);

        // validate business
        QuestionValidator.validate(res, totalQuestions);
        QuestionDistributionValidator.validate(res, mcqCount, essayCount);

        return res;
    }

    @Override
    public GenerateQuestionsResponse generateRetest(String currentEmail, Long materialId, int ignoredNumberOfQuestions, String focusText) {

        User me = userRepo.findByEmail(currentEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        LearningMaterial material = materialRepo.findByIdAndUser(materialId, me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));

        if (material.getStatus() != MaterialStatus.EXTRACTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Material is not extracted yet");
        }

        String extracted = material.getExtractedText();
        if (extracted == null || extracted.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Extracted text is empty");
        }

        String trimmed = extracted.length() > MAX_EXTRACTED_CHARS
                ? extracted.substring(0, MAX_EXTRACTED_CHARS)
                : extracted;

        int mcqCount = Math.max(0, settingsService.getMcqQuestionCount());
        int essayCount = Math.max(0, settingsService.getEssayQuestionCount());
        int totalQuestions = mcqCount + essayCount;

        if (totalQuestions <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "System settings invalid: totalQuestions must be > 0");
        }

        String prompt = promptBuilder.buildRetestPrompt(trimmed, mcqCount, essayCount, focusText);

        String json = callGeminiWithRetry(prompt, totalQuestions);

        GenerateQuestionsResponse res = parseWithRetry(json, trimmed, materialId, totalQuestions, mcqCount, essayCount);

        res.setMaterialId(materialId);
        res.setNumberOfQuestions(totalQuestions);

        QuestionValidator.validate(res, totalQuestions);
        QuestionDistributionValidator.validate(res, mcqCount, essayCount);

        return res;
    }

    private GenerateQuestionsResponse parseWithRetry(
            String json,
            String trimmed,
            Long materialId,
            int totalQuestions,
            int mcqCount,
            int essayCount
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

                    String retryPrompt = promptBuilder.buildPrompt(shorter, mcqCount, essayCount);

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
