package com.be_ai_learning_platform.ai;

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

    public GeminiStructuredClient(RestClient geminiRestClient, GeminiProperties props) {
        this.restClient = geminiRestClient;
        this.props = props;
    }

    /**
     * REST v1 generateContent không support responseSchema/responseMimeType.
     * -> Ép JSON bằng prompt contract + sanitize output.
     */
    public String generateJsonBySchema(String prompt, int numberOfQuestions) {
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt is empty");
        }
        if (numberOfQuestions < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "numberOfQuestions must be >= 1");
        }
        if (props == null || props.model() == null || props.model().isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Gemini model is not configured");
        }

        String finalPrompt = prompt + "\n\n" + jsonContract(numberOfQuestions);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("role", "user", "parts", List.of(Map.of("text", finalPrompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "topP", 0.1,
                        // ✅ giảm output để hạn chế bị cắt JSON
                        "maxOutputTokens", 8192
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request failed", e);
        }
    }

    private String jsonContract(int n) {
        return """
CHỈ TRẢ VỀ JSON THUẦN – KHÔNG markdown, KHÔNG giải thích.

BẮT BUỘC trả JSON ĐẦY ĐỦ, KHÔNG ĐƯỢC CẮT GIỮA CHỪNG.
CHỈ 1 JSON object duy nhất.

FORMAT (bắt buộc đúng key):
{
  "questions": [
    {
      "questionType": "MCQ",
      "question": "...",
      "options": { "A": "...", "B": "...", "C": "...", "D": "..." },
      "correctAnswer": "A",
      "analysis": "..."
    },
    {
      "questionType": "ESSAY",
      "question": "...",
      "sampleAnswer": "...",
      "keywords": ["...","...","..."],
      "maxScore": 10
    }
  ]
}

RULES:
- questions có ĐÚNG %d phần tử
- questionType ∈ {"MCQ","ESSAY"}
- MCQ:
  - options luôn có A,B,C,D (string không rỗng)
  - correctAnswer ∈ {A,B,C,D}
  - analysis ngắn gọn 1-2 câu
- ESSAY:
  - sampleAnswer 2-4 câu
  - keywords >= 3
  - maxScore = 10
- KẾT THÚC OUTPUT bằng dấu }
""".formatted(n);
    }

    /**
     * Remove code fences + lấy đoạn JSON object từ { ... } + fix vài lỗi hay gặp.
     */
    private String sanitizeJsonText(String text) {
        if (text == null) return "";
        String t = text.trim();

        // strip ``` fences
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "");
            t = t.replaceFirst("\\s*```\\s*$", "");
            t = t.trim();
        }

        // extract JSON object
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            t = t.substring(start, end + 1).trim();
        }

        // remove BOM
        if (!t.isEmpty() && t.charAt(0) == '\uFEFF') {
            t = t.substring(1).trim();
        }

        // remove trailing commas before } or ]
        t = t.replaceAll(",\\s*([}\\]])", "$1");

        return t;
    }
}
