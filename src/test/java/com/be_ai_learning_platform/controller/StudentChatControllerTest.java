package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.ChatAskRequest;
import com.be_ai_learning_platform.dto.request.ChatStartRequest;
import com.be_ai_learning_platform.dto.response.ChatAskResponse;
import com.be_ai_learning_platform.dto.response.ChatMessageResponse;
import com.be_ai_learning_platform.dto.response.ChatSessionResponse;
import com.be_ai_learning_platform.service.ChatbotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class StudentChatControllerTest {

    @Mock
    private ChatbotService chatbotService;

    @InjectMocks
    private StudentChatController studentChatController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Nên bắt đầu session chat mới thành công")
    void startSession_Success() {
        // Given
        Long materialId = 1L;
        ChatStartRequest req = new ChatStartRequest();
        req.setMaterialId(materialId);

        ChatSessionResponse expectedResponse = new ChatSessionResponse();
        // Giả sử response có ID session là 100
        when(chatbotService.startSession(materialId)).thenReturn(expectedResponse);

        // When
        ResponseEntity<ChatSessionResponse> response = studentChatController.start(req);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(chatbotService, times(1)).startSession(materialId);
    }

    @Test
    @DisplayName("Nên lấy được danh sách tin nhắn của session")
    void getMessages_Success() {
        // Given
        Long sessionId = 123L;
        List<ChatMessageResponse> mockMessages = List.of(new ChatMessageResponse(), new ChatMessageResponse());
        when(chatbotService.getMessages(sessionId)).thenReturn(mockMessages);

        // When
        ResponseEntity<List<ChatMessageResponse>> response = studentChatController.getMessages(sessionId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        verify(chatbotService, times(1)).getMessages(sessionId);
    }

    @Test
    @DisplayName("Nên gửi câu hỏi cho AI thành công")
    void askAI_Success() {
        // Given
        Long sessionId = 123L;
        String keywords = "Java Spring Boot";
        ChatAskRequest req = new ChatAskRequest();
        req.setKeywords(keywords);

        ChatAskResponse expectedResponse = new ChatAskResponse();
        when(chatbotService.ask(sessionId, keywords)).thenReturn(expectedResponse);

        // When
        ResponseEntity<ChatAskResponse> response = studentChatController.ask(sessionId, req);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(chatbotService, times(1)).ask(sessionId, keywords);
    }
}