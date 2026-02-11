package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.AI.GeminiStructuredClient;
import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;
import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionGenerationServiceImplTest {

    @Mock private LearningMaterialRepository materialRepo;
    @Mock private UserRepository userRepo;
    @Mock private GeminiStructuredClient ai;
    @Mock private PromptBuilder promptBuilder;

    @InjectMocks
    private QuestionGenerationServiceImpl service;

    private User mockUser;
    private LearningMaterial mockMaterial;
    private final String email = "test@example.com";
    private final Long materialId = 1L;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setEmail(email);

        mockMaterial = new LearningMaterial();
        mockMaterial.setId(materialId);
        mockMaterial.setUser(mockUser);
        mockMaterial.setStatus(MaterialStatus.EXTRACTED);
        mockMaterial.setExtractedText("Nội dung bài học mẫu...");
    }

    @Test
    @DisplayName("Ném lỗi UNAUTHORIZED khi không tìm thấy user")
    void generate_UserNotFound_ThrowsUnauthorized() {
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.generate(email, materialId, 5));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("Ném lỗi CONFLICT khi tài liệu chưa được extract")
    void generate_MaterialNotExtracted_ThrowsConflict() {
        mockMaterial.setStatus(MaterialStatus.EXTRACTED); // Đổi trạng thái
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(materialRepo.findByIdAndUser(materialId, mockUser)).thenReturn(Optional.of(mockMaterial));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.generate(email, materialId, 5));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("not extracted yet"));
    }

    @Test
    @DisplayName("Ném lỗi SERVICE_UNAVAILABLE khi AI bị dính Quota/Rate Limit")
    void generate_AiQuotaExceeded_Throws503() {
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(materialRepo.findByIdAndUser(materialId, mockUser)).thenReturn(Optional.of(mockMaterial));
        when(promptBuilder.buildPrompt(anyString(), anyInt())).thenReturn("prompt");

        // Giả lập lỗi 429 từ AI client
        when(ai.generateJsonBySchema(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Error 429: Resource has exhausted quota"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.generate(email, materialId, 5));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertTrue(ex.getReason().contains("quota/rate limit"));
    }

    @Test
    @DisplayName("Retry thành công khi lần đầu gọi AI bị lỗi network nhẹ")
    void generate_AiRetrySuccess_ReturnsResponse() throws Exception {
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(materialRepo.findByIdAndUser(materialId, mockUser)).thenReturn(Optional.of(mockMaterial));
        when(promptBuilder.buildPrompt(anyString(), anyInt())).thenReturn("prompt");

        String validJson = "{\"questions\": []}"; // JSON giả định

        // Lần 1 lỗi, lần 2 thành công
        when(ai.generateJsonBySchema(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Transient error"))
                .thenReturn(validJson);

        GenerateQuestionsResponse result = service.generate(email, materialId, 5);

        assertNotNull(result);
        verify(ai, times(2)).generateJsonBySchema(anyString(), anyInt());
    }

    @Test
    @DisplayName("Lỗi BAD_GATEWAY khi AI trả JSON sai định dạng sau khi đã retry parse")
    void generate_InvalidJson_ThrowsBadGateway() {
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(materialRepo.findByIdAndUser(materialId, mockUser)).thenReturn(Optional.of(mockMaterial));
        when(promptBuilder.buildPrompt(anyString(), anyInt())).thenReturn("prompt");

        // AI trả về JSON lỗi (thiếu dấu ngoặc...)
        when(ai.generateJsonBySchema(anyString(), anyInt())).thenReturn("{ invalid json");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.generate(email, materialId, 5));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertTrue(ex.getReason().contains("AI output invalid JSON"));
    }
}