package com.be_ai_learning_platform.ai;

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

    public GeminiResponsesClient(RestClient geminiRestClient, GeminiProperties props) {
        this.restClient = geminiRestClient;
        this.props = props;
    }

    /**
     * Generate plain text using Gemini generateContent.
     * Keeps a simple signature to match old OpenAI usage in services.
     */
    public String generateText(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt is empty");
        }
        if (props == null || props.model() == null || props.model().isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gemini model is not configured");
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
                    .uri("/v1/models/{model}:generateContent", props.model())
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String text = GeminiTextExtractor.extractText(res);
            return text == null ? "" : text.trim();

        } catch (RestClientResponseException e) {
            // Gemini trả lỗi HTTP (401/403/429/5xx...)
            String msg = buildHttpErrorMessage(e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, msg, e);

        } catch (Exception e) {
            // Lỗi khác (parse, network, null pointer...)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request failed", e);
        }
    }

    private String buildHttpErrorMessage(RestClientResponseException e) {
        int status = e.getRawStatusCode();
        String body = e.getResponseBodyAsString();
        String briefBody = (body == null) ? "" : body.trim();
        if (briefBody.length() > 400) briefBody = briefBody.substring(0, 400) + "...";

        // Message ngắn gọn nhưng đủ debug
        return "Gemini API error " + status + (briefBody.isBlank() ? "" : (": " + briefBody));
    }
}
