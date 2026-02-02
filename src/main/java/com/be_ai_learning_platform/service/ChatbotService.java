package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.response.ChatAskResponse;
import com.be_ai_learning_platform.dto.response.ChatMessageResponse;
import com.be_ai_learning_platform.dto.response.ChatSessionResponse;

import java.util.List;

public interface ChatbotService {

    /**
     * Tạo hoặc lấy session chat theo (user, material)
     */
    ChatSessionResponse startSession(Long materialId);

    /**
     * Lấy toàn bộ lịch sử chat của session
     */
    List<ChatMessageResponse> getMessages(Long sessionId);

    /**
     * Gửi keyword để hỏi AI (US17 guardrails)
     */
    ChatAskResponse ask(Long sessionId, String keywords);
}
