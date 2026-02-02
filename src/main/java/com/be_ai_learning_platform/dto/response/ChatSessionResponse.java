package com.be_ai_learning_platform.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionResponse {
    private Long sessionId;
    private Long materialId;
    private LocalDateTime createdAt;
}
