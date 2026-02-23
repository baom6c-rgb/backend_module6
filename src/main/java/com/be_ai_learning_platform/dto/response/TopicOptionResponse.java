package com.be_ai_learning_platform.dto.response;

import lombok.Data;

import java.util.List;

/**
 * Topic option returned when BE detects the uploaded material contains multiple lessons/topics.
 * FE should ask user to pick ONE topic, then call /v2/select-topic.
 */
@Data
public class TopicOptionResponse {
    /** stable id within selectionToken, e.g. T1, T2 */
    private String id;

    /** short title for displaying */
    private String title;

    /** 1-2 lines summary */
    private String summary;

    /** keywords to help user pick quickly */
    private List<String> keywords;
}
