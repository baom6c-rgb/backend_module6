package com.be_ai_learning_platform.ai;

import com.be_ai_learning_platform.service.SystemSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Component
public class GeminiStructuredClient {

    private final RestClient restClient;
    private final GeminiProperties props;
    private final SystemSettingsService settingsService;

    public GeminiStructuredClient(RestClient geminiRestClient,
                                  GeminiProperties props,
                                  SystemSettingsService settingsService) {
        this.restClient = geminiRestClient;
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

        // ✅ provider/model/temperature/key lấy từ DB
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

        double temperature = settingsService.getAiTemperature();
        String finalPrompt = prompt + "\n\n" + jsonContract;

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("role", "user", "parts", List.of(Map.of("text", finalPrompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", temperature,
                        "topP", 0.1,
                        "maxOutputTokens", 8192
                )
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> res = restClient.post()
                    // ✅ key via query param (recommended)
                    .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String text = GeminiTextExtractor.extractText(res);
            return sanitizeJsonText(text);

        } catch (RestClientResponseException e) {
            String bodyStr = e.getResponseBodyAsString();
            String brief = bodyStr == null ? "" : bodyStr.trim();
            if (brief.length() > 600) brief = brief.substring(0, 600) + "...";

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Gemini API error " + e.getRawStatusCode() + (brief.isBlank() ? "" : (": " + brief)),
                    e
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI request failed", e);
        }
    }

    public String generateJsonBySchema(String prompt, int numberOfQuestions) {
        if (numberOfQuestions < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numberOfQuestions must be >= 1");
        }
        return generateJson(prompt, jsonContract(numberOfQuestions));
    }

    private String jsonContract(int n) {
        return """
CHỈ TRẢ VỀ JSON THUẦN – KHÔNG markdown, KHÔNG giải thích.
BẮT BUỘC trả JSON ĐẦY ĐỦ, KHÔNG ĐƯỢC CẮT GIỮA CHỪNG.
CHỈ 1 JSON object duy nhất.

FORMAT:
{
  "questions": [
    {
      "questionType": "MCQ",
      "question": "...",
      "options": { "A": "...", "B": "...", "C": "...", "D": "..." },
      "correctAnswer": "A",
      "analysis": "..."
    }
  ]
}

RULES:
- questions có ĐÚNG %d phần tử
- questionType ∈ {"MCQ","ESSAY"}
- KẾT THÚC OUTPUT bằng dấu }
""".formatted(n);
    }

    private String sanitizeJsonText(String text) {
        if (text == null) return "";
        String t = text.trim();

        // remove markdown fences
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "");
            t = t.replaceFirst("\\s*```\\s*$", "");
            t = t.trim();
        }

        // try extract from first '{'
        int start = t.indexOf('{');
        if (start >= 0) t = t.substring(start).trim();

        // if has last '}', cut to it
        int end = t.lastIndexOf('}');
        if (end > 0) {
            t = t.substring(0, end + 1).trim();
        }

        // remove BOM
        if (!t.isEmpty() && t.charAt(0) == '\uFEFF') {
            t = t.substring(1).trim();
        }

        // remove trailing commas
        t = t.replaceAll(",\\s*([}\\]])", "$1");

        // ✅ repair if truncated (missing closing brackets/braces)
        t = repairJsonIfTruncated(t);

        return t;
    }

    private String repairJsonIfTruncated(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return t;

        // If it already ends with '}', assume ok
        if (t.endsWith("}")) return t;

        // Count braces/brackets to close them
        int curly = 0;
        int square = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == '{') curly++;
            else if (c == '}') curly--;
            else if (c == '[') square++;
            else if (c == ']') square--;
        }

        // Close open arrays first, then objects
        StringBuilder sb = new StringBuilder(t);

        while (square > 0) {
            sb.append(']');
            square--;
        }
        while (curly > 0) {
            sb.append('}');
            curly--;
        }

        return sb.toString().trim();
    }
}
