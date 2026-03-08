package com.be_ai_learning_platform.AI;

import com.be_ai_learning_platform.service.SystemSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AiResponsesRouter {

    private final SystemSettingsService settingsService;
    private final GeminiResponsesClient gemini;
    private final OpenRouterResponsesClient openrouter;

    public AiResponsesRouter(SystemSettingsService settingsService,
                             GeminiResponsesClient gemini,
                             OpenRouterResponsesClient openrouter) {
        this.settingsService = settingsService;
        this.gemini = gemini;
        this.openrouter = openrouter;
    }

    public String generateText(String prompt) {
        String provider = settingsService.getAiProvider();
        if ("OPENROUTER".equalsIgnoreCase(provider)) {
            return openrouter.generateText(prompt);
        }
        if ("GEMINI".equalsIgnoreCase(provider)) {
            return gemini.generateText(prompt);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI provider not supported: " + provider);
    }
}
