package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.ChatAskRequest;
import com.be_ai_learning_platform.dto.request.ChatStartRequest;
import com.be_ai_learning_platform.dto.response.ChatAskResponse;
import com.be_ai_learning_platform.dto.response.ChatMessageResponse;
import com.be_ai_learning_platform.dto.response.ChatSessionResponse;
import com.be_ai_learning_platform.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student/chat")
@RequiredArgsConstructor
public class StudentChatController {

    private final ChatbotService chatbotService;

    /**
     * Create or reuse a chat session for current user + material.
     * POST /api/student/chat/session
     */
    @PostMapping("/session")
    public ResponseEntity<ChatSessionResponse> start(@RequestBody ChatStartRequest req) {
        return ResponseEntity.ok(chatbotService.startSession(req.getMaterialId()));
    }

    /**
     * Get full message history of a session.
     * GET /api/student/chat/session/{sessionId}
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(@PathVariable Long sessionId) {
        return ResponseEntity.ok(chatbotService.getMessages(sessionId));
    }

    /**
     * Ask AI with keyword-only input.
     * POST /api/student/chat/session/{sessionId}/ask
     */
    @PostMapping("/session/{sessionId}/ask")
    public ResponseEntity<ChatAskResponse> ask(@PathVariable Long sessionId, @RequestBody ChatAskRequest req) {
        return ResponseEntity.ok(chatbotService.ask(sessionId, req.getKeywords()));
    }
}
