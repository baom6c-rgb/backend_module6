package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.AI.AiStructuredRouter;
import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;
import com.be_ai_learning_platform.dto.response.GeneratedQuestionItemResponse;
import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import com.be_ai_learning_platform.entity.enums.QuestionType;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.validator.ProgrammingContentValidator;
import com.be_ai_learning_platform.service.QuestionGenerationService;
import com.be_ai_learning_platform.service.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class QuestionGenerationServiceImpl implements QuestionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(QuestionGenerationServiceImpl.class);

    private static final int MAX_EXTRACTED_CHARS = 6000;

    private static final int CALL_RETRY_TIMES = 1;
    private static final long CALL_RETRY_BACKOFF_MS = 700;

    private static final int PARSE_RETRY_TIMES = 1;
    private static final int PARSE_RETRY_TRIMMED_CHARS = 3000;

    private final LearningMaterialRepository materialRepo;
    private final UserRepository userRepo;
    private final AiStructuredRouter ai;
    private final PromptBuilder promptBuilder;
    private final SystemSettingsService settingsService;
    private final ProgrammingContentValidator programmingContentValidator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public QuestionGenerationServiceImpl(
            LearningMaterialRepository materialRepo,
            UserRepository userRepo,
            AiStructuredRouter ai,
            PromptBuilder promptBuilder,
            SystemSettingsService settingsService,
            ProgrammingContentValidator programmingContentValidator
    ) {
        this.materialRepo = materialRepo;
        this.userRepo = userRepo;
        this.ai = ai;
        this.promptBuilder = promptBuilder;
        this.settingsService = settingsService;
        this.programmingContentValidator = programmingContentValidator;
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
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "System settings invalid: totalQuestions must be > 0"
            );
        }

        String prompt = promptBuilder.buildPrompt(trimmed, mcqCount, essayCount);
        String json = callAiWithRetry(prompt, totalQuestions);

        GenerateQuestionsResponse res = parseWithRetry(
                json,
                trimmed,
                totalQuestions,
                mcqCount,
                essayCount,
                false,
                null
        );

        return normalizeAndValidateResponse(res, materialId, totalQuestions, mcqCount, essayCount);
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
        String json = callAiWithRetry(prompt, totalQuestions);

        GenerateQuestionsResponse res = parseWithRetry(
                json,
                trimmed,
                totalQuestions,
                mcqCount,
                essayCount,
                false,
                null
        );

        return normalizeAndValidateResponse(res, materialId, totalQuestions, mcqCount, essayCount);
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
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "System settings invalid: totalQuestions must be > 0"
            );
        }

        String prompt = promptBuilder.buildRetestPrompt(trimmed, mcqCount, essayCount, focusText);
        String json = callAiWithRetry(prompt, totalQuestions);

        GenerateQuestionsResponse res = parseWithRetry(
                json,
                trimmed,
                totalQuestions,
                mcqCount,
                essayCount,
                true,
                focusText
        );

        return normalizeAndValidateResponse(res, materialId, totalQuestions, mcqCount, essayCount);
    }

    // ===================== COMMON HELPERS =====================

    private GenerateQuestionsResponse normalizeAndValidateResponse(
            GenerateQuestionsResponse res,
            Long materialId,
            int totalQuestions,
            int mcqCount,
            int essayCount
    ) {
        if (res == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI returned empty response");
        }

        GenerateQuestionsResponse normalized = normalizeQuestionDistribution(res, mcqCount, essayCount);

        normalized.setMaterialId(materialId);
        normalized.setNumberOfQuestions(totalQuestions);

        QuestionValidator.validate(normalized, totalQuestions);
        QuestionDistributionValidator.validate(normalized, mcqCount, essayCount);

        return normalized;
    }

    private String trimExtracted(LearningMaterial material) {
        String extracted = material.getExtractedText();
        if (extracted == null || extracted.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Extracted text is empty");
        }

        try {
            programmingContentValidator.validateOnly(extracted);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
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

                log.warn("AI JSON parse failed (attempt {}/{}): {}", i + 1, PARSE_RETRY_TIMES + 1, safeMsg(e));

                if (i < PARSE_RETRY_TIMES) {
                    String shorter = trimmed.length() > PARSE_RETRY_TRIMMED_CHARS
                            ? trimmed.substring(0, PARSE_RETRY_TRIMMED_CHARS)
                            : trimmed;

                    String retryPrompt = isRetest
                            ? promptBuilder.buildRetestPrompt(shorter, mcqCount, essayCount, focusText)
                            : promptBuilder.buildPrompt(shorter, mcqCount, essayCount);

                    currentJson = callAiWithRetry(retryPrompt, totalQuestions);
                    continue;
                }

                String preview = currentJson == null ? "" : currentJson;
                preview = preview.length() > 800 ? preview.substring(0, 800) + "..." : preview;

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Đầu ra JSON không hợp lệ của AI: " + preview,
                        last
                );
            }
        }

        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI output invalid JSON", last);
    }

    private String callAiWithRetry(String prompt, int totalQuestions) {
        Exception last = null;

        for (int i = 0; i <= CALL_RETRY_TIMES; i++) {
            try {
                return ai.generateJsonBySchema(prompt, totalQuestions);
            } catch (Exception e) {
                last = e;

                String msg = extractDeepestMessage(e);
                log.error("AI request failed (attempt {}/{}): {}", i + 1, CALL_RETRY_TIMES + 1, msg, e);

                if (isQuotaOrRateLimit(msg)) {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Dịch vụ AI hiện tạm thời không khả dụng do quota/rate limit: " + msg,
                            e
                    );
                }

                if (i < CALL_RETRY_TIMES) {
                    sleep(CALL_RETRY_BACKOFF_MS);
                    continue;
                }

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "AI request failed: " + shorten(msg, 500),
                        e
                );
            }
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "AI request failed: " + shorten(extractDeepestMessage(last), 500),
                last
        );
    }

    /**
     * Salvage distribution:
     * - ưu tiên lấy đúng số MCQ / ESSAY theo yêu cầu
     * - nếu AI trả dư thì cắt bớt
     * - nếu AI trả thiếu đúng loại thì báo lỗi rõ ràng
     */
    private GenerateQuestionsResponse normalizeQuestionDistribution(
            GenerateQuestionsResponse res,
            int requestedMcq,
            int requestedEssay
    ) {
        List<GeneratedQuestionItemResponse> original = res.getQuestions();
        if (original == null || original.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI returned no questions");
        }

        List<GeneratedQuestionItemResponse> cleaned = original.stream()
                .filter(Objects::nonNull)
                .toList();

        List<GeneratedQuestionItemResponse> mcqList = new ArrayList<>();
        List<GeneratedQuestionItemResponse> essayList = new ArrayList<>();
        List<GeneratedQuestionItemResponse> unknownList = new ArrayList<>();

        for (GeneratedQuestionItemResponse q : cleaned) {
            QuestionType type = q.getQuestionType();
            if (type == QuestionType.MCQ) {
                mcqList.add(q);
            } else if (type == QuestionType.ESSAY) {
                essayList.add(q);
            } else {
                unknownList.add(q);
            }
        }

        for (GeneratedQuestionItemResponse q : unknownList) {
            QuestionType guessed = guessQuestionType(q);
            if (guessed == QuestionType.MCQ) {
                q.setQuestionType(QuestionType.MCQ);
                mcqList.add(q);
            } else if (guessed == QuestionType.ESSAY) {
                q.setQuestionType(QuestionType.ESSAY);
                essayList.add(q);
            }
        }

        if (mcqList.size() < requestedMcq || essayList.size() < requestedEssay) {
            String msg = "AI returned wrong question distribution"
                    + " (requested MCQ=" + requestedMcq
                    + ", ESSAY=" + requestedEssay
                    + " but got MCQ=" + mcqList.size()
                    + ", ESSAY=" + essayList.size() + ")";
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, msg);
        }

        List<GeneratedQuestionItemResponse> normalized = new ArrayList<>();
        normalized.addAll(mcqList.subList(0, requestedMcq));
        normalized.addAll(essayList.subList(0, requestedEssay));

        GenerateQuestionsResponse out = new GenerateQuestionsResponse();
        out.setExamTitle(res.getExamTitle());
        out.setPreviewToken(res.getPreviewToken());
        out.setQuestions(normalized);

        return out;
    }

    private QuestionType guessQuestionType(GeneratedQuestionItemResponse q) {
        if (q == null) return null;

        boolean hasOptions = q.getOptions() != null && !q.getOptions().isEmpty();
        boolean hasCorrectAnswer = q.getCorrectAnswer() != null && !q.getCorrectAnswer().isBlank();
        boolean hasAnalysis = q.getAnalysis() != null && !q.getAnalysis().isBlank();

        if (hasOptions || hasCorrectAnswer || hasAnalysis) {
            return QuestionType.MCQ;
        }

        boolean hasSampleAnswer = q.getSampleAnswer() != null && !q.getSampleAnswer().isBlank();
        boolean hasKeywords = q.getKeywords() != null && !q.getKeywords().isEmpty();
        boolean hasMaxScore = q.getMaxScore() != null;

        if (hasSampleAnswer || hasKeywords || hasMaxScore) {
            return QuestionType.ESSAY;
        }

        return null;
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
            return e == null || e.getMessage() == null ? "" : e.getMessage();
        } catch (Exception ignore) {
            return "";
        }
    }

    private String extractDeepestMessage(Throwable t) {
        if (t == null) return "Unknown AI error";

        Throwable cur = t;
        String lastMsg = null;

        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && !msg.isBlank()) {
                lastMsg = msg.trim();
            }
            cur = cur.getCause();
        }

        return lastMsg == null || lastMsg.isBlank() ? "Unknown AI error" : lastMsg;
    }

    private String shorten(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max) + "...";
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}