package com.be_ai_learning_platform.dto.request;

import lombok.Data;

@Data
public class SelectClassRequest {
    private Long userId;
    private Long classId;
    private Long moduleId;
}
