package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.ai.GeminiStructuredClient;
import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;
import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.QuestionGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionGenerationServiceImpl implements QuestionGenerationService {

    // ✅ Giảm input để tiết kiệm token + giảm nguy cơ model trả cắt ngang
    private static final int MAX_EXTRACTED_CHARS = 6000;

    // ✅ Retry nhẹ cho lỗi gọi AI (network/transient)
    private static final int CALL_RETRY_TIMES = 1;
    private static final long CALL_RETRY_BACKOFF_MS = 700;

    // ✅ Retry riêng cho lỗi parse JSON (Gemini hay trả cắt ngang JSON)
    private static final int PARSE_RETRY_TIMES = 1;
    private static final int PARSE_RETRY_TRIMMED_CHARS = 3000;

    private final LearningMaterialRepository materialRepo;
    private final UserRepository userRepo;
    private final GeminiStructuredClient ai;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QuestionGenerationServiceImpl(
            LearningMaterialRepository materialRepo,
            UserRepository userRepo,
            GeminiStructuredClient ai,
            PromptBuilder promptBuilder
    ) {
        this.materialRepo = materialRepo;
        this.userRepo = userRepo;
        this.ai = ai;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public GenerateQuestionsResponse generate(String currentEmail, Long materialId, int numberOfQuestions) {

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

        String prompt = promptBuilder.buildPrompt(trimmed, numberOfQuestions);

        // 1) Call AI (with retry for transient/quota)
        String json = callGeminiWithRetry(prompt, numberOfQuestions);

        // 2) Parse JSON (with retry if JSON bị cắt/ngắt)
        GenerateQuestionsResponse res = parseWithRetry(json, trimmed, materialId, numberOfQuestions);

        // meta
        res.setMaterialId(materialId);
        res.setNumberOfQuestions(numberOfQuestions);

        // validate business
        QuestionValidator.validate(res, numberOfQuestions);

        return res;
    }

    private GenerateQuestionsResponse parseWithRetry(
            String json,
            String trimmed,
            Long materialId,
            int numberOfQuestions
    ) {
        Exception last = null;
        String currentJson = json;

        for (int i = 0; i <= PARSE_RETRY_TIMES; i++) {
            try {
                return objectMapper.readValue(currentJson, GenerateQuestionsResponse.class);
            } catch (Exception e) {
                last = e;

                // nếu còn lượt retry parse -> gọi lại AI với input ngắn hơn
                if (i < PARSE_RETRY_TIMES) {
                    String shorter = trimmed.length() > PARSE_RETRY_TRIMMED_CHARS
                            ? trimmed.substring(0, PARSE_RETRY_TRIMMED_CHARS)
                            : trimmed;

                    String retryPrompt = promptBuilder.buildPrompt(shorter, numberOfQuestions);

                    // call lại (vẫn có guard quota)
                    currentJson = callGeminiWithRetry(retryPrompt, numberOfQuestions);
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

        // should never reach
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI output invalid JSON", last);
    }

    private String callGeminiWithRetry(String prompt, int numberOfQuestions) {
        Exception last = null;

        for (int i = 0; i <= CALL_RETRY_TIMES; i++) {
            try {
                return ai.generateJsonBySchema(prompt, numberOfQuestions);
            } catch (Exception e) {
                last = e;

                String msg = safeMsg(e);

                // ✅ Quota / rate-limit -> 503 để FE show toast “thử lại sau”
                if (isQuotaOrRateLimit(msg)) {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "AI service is temporarily unavailable (quota/rate limit). Please try again later.",
                            e
                    );
                }

                // transient -> retry nhẹ
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
