package com.be_ai_learning_platform.dto.response;

import com.be_ai_learning_platform.entity.enums.SenderType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessageResponse {
    private Long id;
    private SenderType sender;
    private String content;
    private LocalDateTime createdAt;
}
