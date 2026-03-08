package com.be_ai_learning_platform.AI;

import com.be_ai_learning_platform.service.SystemSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenRouterStructuredClient {

    private static final int MAX_OUTPUT_TOKENS = 4000;

    private final RestClient restClient;
    private final OpenRouterProperties props;
    private final SystemSettingsService settingsService;

    public OpenRouterStructuredClient(RestClient openRouterRestClient,
                                      OpenRouterProperties props,
                                      SystemSettingsService settingsService) {
        this.restClient = openRouterRestClient;
        this.props = props;
        this.settingsService = settingsService;
    }

    public String generateJson(String prompt, String jsonContract) {
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt is empty");
        }
        if (jsonContract == null || jsonContract.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON contract is empty");
        }

        String provider = settingsService.getAiProvider();
        if (!"OPENROUTER".equalsIgnoreCase(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI provider is not OPENROUTER");
        }

        String model = settingsService.getAiModel();
        if (model == null || model.isBlank()) {
            model = props == null ? null : props.defaultModel();
        }
        if (model == null || model.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OpenRouter model is not configured");
        }

        final String apiKey;
        try {
            apiKey = settingsService.requireAiApiKey("OPENROUTER");
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        }

        double temperature = settingsService.getAiTemperature();

        String finalPrompt = prompt + "\n\n" + jsonContract;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("max_tokens", MAX_OUTPUT_TOKENS);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "Return ONLY valid JSON. No markdown."),
                Map.of("role", "user", "content", finalPrompt)
        ));

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> res = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .headers(h -> {
                        if (props != null && props.httpReferer() != null && !props.httpReferer().isBlank()) {
                            h.add("HTTP-Referer", props.httpReferer().trim());
                        }
                        if (props != null && props.appTitle() != null && !props.appTitle().isBlank()) {
                            h.add("X-OpenRouter-Title", props.appTitle().trim());
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String text = OpenRouterTextExtractor.extractText(res);
            if (text == null || text.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "OpenRouter returned empty content"
                );
            }

            String sanitized = sanitizeJsonText(text);
            if (sanitized == null || sanitized.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "OpenRouter returned blank JSON content"
                );
            }

            return sanitized;

        } catch (RestClientResponseException ex) {
            String msg = ex.getResponseBodyAsString();
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "OpenRouter call failed: HTTP " + ex.getRawStatusCode() + " - " + msg,
                    ex
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenRouter call failed", ex);
        }
    }

    private String sanitizeJsonText(String text) {
        if (text == null) return "";

        String t = text.trim();

        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "");
            t = t.replaceFirst("\\s*```\\s*$", "");
            t = t.trim();
        }

        if (!t.isEmpty() && t.charAt(0) == '\uFEFF') {
            t = t.substring(1).trim();
        }

        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');

        if (start >= 0 && end > start) {
            t = t.substring(start, end + 1).trim();
        }

        t = t.replaceAll(",\\s*([}\\]])", "$1");
        return t;
    }
}