package com.be_ai_learning_platform.AI;

import java.util.List;
import java.util.Map;

public class GeminiTextExtractor {

    private GeminiTextExtractor() {}

    @SuppressWarnings("unchecked")
    public static String extractText(Map<String, Object> response) {
        if (response == null) return "";

        Object candObj = response.get("candidates");
        if (!(candObj instanceof List<?> candidates) || candidates.isEmpty()) return "";

        Object firstCand = candidates.get(0);
        if (!(firstCand instanceof Map<?, ?> candMap)) return "";

        Object contentObj = candMap.get("content");
        if (!(contentObj instanceof Map<?, ?> content)) return "";

        Object partsObj = content.get("parts");
        if (!(partsObj instanceof List<?> parts) || parts.isEmpty()) return "";

        Object firstPart = parts.get(0);
        if (!(firstPart instanceof Map<?, ?> partMap)) return "";

        Object text = partMap.get("text");
        return text == null ? "" : text.toString();
    }
}
