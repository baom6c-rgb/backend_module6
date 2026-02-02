package com.be_ai_learning_platform.dto.response;

import lombok.Data;

@Data
public class ChatAskResponse {
    private Long sessionId;
    private String answer;
}
