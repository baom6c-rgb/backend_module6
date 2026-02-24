package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.response.TopicOptionResponse;

import java.util.List;

public interface TopicSelectionService {

    /**
     * Detect whether extracted material contains multiple distinct topics/lessons.
     *
     * @return detection result with isMulti and topic options. If isMulti=false, topics may be empty.
     */
    TopicSelectionResult detectTopics(String currentEmail, Long materialId);

    /**
     * Resolve focusText by selectionToken + topicId (stored server-side).
     */
    String resolveFocusText(String currentEmail, String selectionToken, String topicId);

    /**
     * Resolve focusText for multiple topicIds. The service will merge and clamp the result.
     */
    String resolveFocusText(String currentEmail, String selectionToken, List<String> topicIds);

    /**
     * Resolve materialId bound to selectionToken.
     */
    Long resolveMaterialId(String currentEmail, String selectionToken);

    /**
     * Simple DTO to avoid introducing many files.
     */
    class TopicSelectionResult {
        public boolean isMulti;
        public String selectionToken;
        public List<TopicOptionResponse> topics;
    }
}
