package com.be_ai_learning_platform.AI;

import com.be_ai_learning_platform.service.SystemSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AiStructuredRouter {

    private final SystemSettingsService settingsService;
    private final GeminiStructuredClient gemini;
    private final OpenRouterStructuredClient openrouter;

    public AiStructuredRouter(SystemSettingsService settingsService,
                              GeminiStructuredClient gemini,
                              OpenRouterStructuredClient openrouter) {
        this.settingsService = settingsService;
        this.gemini = gemini;
        this.openrouter = openrouter;
    }

    public String generateJson(String prompt, String jsonContract) {
        String provider = settingsService.getAiProvider();

        if ("OPENROUTER".equalsIgnoreCase(provider)) {
            return openrouter.generateJson(prompt, jsonContract);
        }

        if ("GEMINI".equalsIgnoreCase(provider)) {
            return gemini.generateJson(prompt, jsonContract);
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "AI provider not supported: " + provider
        );
    }

    /**
     * Backward-compatible cho các service cũ đang gọi:
     * ai.generateJsonBySchema(prompt, totalQuestions)
     *
     * PromptBuilder của project đã tự chứa schema JSON rất chi tiết,
     * nên ở đây chỉ thêm contract tối thiểu để ép model trả JSON sạch.
     */
    public String generateJsonBySchema(String prompt, int totalQuestions) {
        String jsonContract = buildMinimalJsonContract(totalQuestions);
        return generateJson(prompt, jsonContract);
    }

    private String buildMinimalJsonContract(int totalQuestions) {
        return """
IMPORTANT:
- Return ONLY valid JSON.
- Do NOT wrap JSON in markdown.
- Do NOT add any explanation before or after JSON.
- The JSON must follow exactly the schema already described in the prompt.
- The top-level object must contain "questions".
- The "questions" array must contain exactly %d items.
- "questionType" must be either "MCQ" or "ESSAY".
- For MCQ, include: questionType, question, options, correctAnswer, analysis.
- For ESSAY, include: questionType, question, sampleAnswer, keywords, maxScore.
- For MCQ, "options" must be an object with keys: "A", "B", "C", "D".
- For ESSAY, "keywords" is REQUIRED and must contain 3 to 6 non-empty items.
""".formatted(totalQuestions);
    }
}