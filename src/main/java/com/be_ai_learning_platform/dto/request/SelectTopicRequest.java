package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SelectTopicRequest {

    @NotBlank
    private String selectionToken;
    @NotEmpty
    private List<String> topicIds;
}