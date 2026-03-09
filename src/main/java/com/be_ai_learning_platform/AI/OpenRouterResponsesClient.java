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
public class OpenRouterResponsesClient {

    private final RestClient restClient;
    private final OpenRouterProperties props;
    private final SystemSettingsService settingsService;

    public OpenRouterResponsesClient(RestClient openRouterRestClient,
                                     OpenRouterProperties props,
                                     SystemSettingsService settingsService) {
        this.restClient = openRouterRestClient;
        this.props = props;
        this.settingsService = settingsService;
    }

    public String generateText(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt is empty");
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
            apiKey = settingsService.requireAiApiKey();
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex);
        }

        double temperature = settingsService.getAiTemperature();

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", temperature,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

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
            return text == null ? "" : text.trim();

        } catch (RestClientResponseException ex) {
            String bodyStr = ex.getResponseBodyAsString();
            String brief = bodyStr == null ? "" : bodyStr.trim();
            if (brief.length() > 600) brief = brief.substring(0, 600) + "...";
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "OpenRouter API error " + ex.getRawStatusCode() + (brief.isBlank() ? "" : (": " + brief)),
                    ex
            );
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenRouter request failed", ex);
        }
    }
}
