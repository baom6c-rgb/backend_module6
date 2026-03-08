package com.be_ai_learning_platform.AI;

import java.util.List;
import java.util.Map;

public final class OpenRouterTextExtractor {

    private OpenRouterTextExtractor() {
    }

    @SuppressWarnings("unchecked")
    public static String extractText(Map<String, Object> res) {
        if (res == null) return "";

        Object choicesObj = res.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) return "";

        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) return "";

        Object msgObj = firstMap.get("message");
        if (!(msgObj instanceof Map<?, ?> msg)) return "";

        Object content = msg.get("content");
        return content == null ? "" : String.valueOf(content);
    }
}
