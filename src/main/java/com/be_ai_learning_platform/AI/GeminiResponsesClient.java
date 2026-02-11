package com.be_ai_learning_platform.AI;

import com.be_ai_learning_platform.service.SystemSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Component
public class GeminiResponsesClient {

    private final RestClient restClient;
    private final GeminiProperties props;
    private final SystemSettingsService settingsService;

    public GeminiResponsesClient(RestClient geminiRestClient,
                                 GeminiProperties props,
                                 SystemSettingsService settingsService) {
        this.restClient = geminiRestClient;
        this.props = props;
        this.settingsService = settingsService;
    }

    public String generateText(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt is empty");
        }

        String provider = settingsService.getAiProvider();
        if (!"GEMINI".equalsIgnoreCase(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI provider is not GEMINI");
        }

        String model = settingsService.getAiModel();
        if (model == null || model.isBlank()) {
            model = (props == null ? null : props.model());
        }
        if (model == null || model.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini model is not configured");
        }

        final String apiKey;
        try {
            apiKey = settingsService.requireAiApiKey();
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", prompt))
                        )
                )
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> res = restClient.post()
                    // ✅ key via query param (recommended + stable)
                    .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String text = GeminiTextExtractor.extractText(res);
            return text == null ? "" : text.trim();

        } catch (RestClientResponseException e) {
            String msg = buildHttpErrorMessage(e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, msg, e);

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request failed", e);
        }
    }

    private String buildHttpErrorMessage(RestClientResponseException e) {
        int status = e.getRawStatusCode();
        String body = e.getResponseBodyAsString();
        String briefBody = (body == null) ? "" : body.trim();
        if (briefBody.length() > 400) briefBody = briefBody.substring(0, 400) + "...";
        return "Gemini API error " + status + (briefBody.isBlank() ? "" : (": " + briefBody));
    }
}
