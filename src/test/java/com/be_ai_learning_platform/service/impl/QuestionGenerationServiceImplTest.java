package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.AI.GeminiStructuredClient;
import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;
import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionGenerationServiceImplTest {

    @Mock private LearningMaterialRepository materialRepo;
    @Mock private UserRepository userRepo;
    @Mock private SystemSettingsService settingsService;

    private GeminiStructuredClient ai;   // manual mock (flexible)
    private PromptBuilder promptBuilder; // manual mock (flexible)

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

        // settings: để distribution validator pass với JSON toàn MCQ
        when(settingsService.getMcqQuestionCount()).thenReturn(5);
        when(settingsService.getEssayQuestionCount()).thenReturn(0);

        // PromptBuilder: trả "prompt" cho mọi method return String
        promptBuilder = mock(PromptBuilder.class, invocation -> {
            if (invocation.getMethod().getReturnType() == String.class) return "prompt";
            return RETURNS_DEFAULTS.answer(invocation);
        });

        // AI default: trả JSON hợp lệ
        ai = mock(GeminiStructuredClient.class, invocation -> {
            if (invocation.getMethod().getReturnType() == String.class) {
                return buildValidMcqOnlyQuestionsJson(5);
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private QuestionGenerationServiceImpl newService(GeminiStructuredClient aiClient) {
        return new QuestionGenerationServiceImpl(materialRepo, userRepo, aiClient, promptBuilder, settingsService);
    }

    // ===================== TESTS =====================

    @Test
    @DisplayName("UNAUTHORIZED khi không tìm thấy user")
    void generate_userNotFound_throws401() {
        // arrange
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.empty());
        QuestionGenerationServiceImpl service = newService(ai);

        // act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generate(email, materialId, 999));

        // assert
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(userRepo).findByEmail(email);
        verifyNoInteractions(materialRepo);
    }

    @Test
    @DisplayName("NOT_FOUND khi không tìm thấy material theo user")
    void generate_materialNotFound_throws404() {
        // arrange
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(materialRepo.findByIdAndUser(materialId, mockUser)).thenReturn(Optional.empty());
        QuestionGenerationServiceImpl service = newService(ai);

        // act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generate(email, materialId, 999));

        // assert
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(userRepo).findByEmail(email);
        verify(materialRepo).findByIdAndUser(materialId, mockUser);
        verifyNoMoreInteractions(userRepo, materialRepo);
    }

    @Test
    @DisplayName("CONFLICT khi material chưa EXTRACTED")
    void generate_materialNotExtracted_throws409() {
        // arrange
        mockMaterial.setStatus(MaterialStatus.UPLOADED);

        when(userRepo.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(materialRepo.findByIdAndUser(materialId, mockUser)).thenReturn(Optional.of(mockMaterial));
        QuestionGenerationServiceImpl service = newService(ai);

        // act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generate(email, materialId, 999));

        // assert
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(userRepo).findByEmail(email);
        verify(materialRepo).findByIdAndUser(materialId, mockUser);
        verifyNoMoreInteractions(userRepo, materialRepo);
    }

    @Test
    @DisplayName("SERVICE_UNAVAILABLE khi AI dính quota/rate limit (429)")
    void generate_aiQuotaExceeded_throws503() {
        // arrange
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(materialRepo.findByIdAndUser(materialId, mockUser)).thenReturn(Optional.of(mockMaterial));

        GeminiStructuredClient ai429 = mock(GeminiStructuredClient.class, invocation -> {
            if (invocation.getMethod().getReturnType() == String.class) {
                throw new RuntimeException("Error 429: Resource has exhausted quota");
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });

        QuestionGenerationServiceImpl service = newService(ai429);

        // act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generate(email, materialId, 999));

        // assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    @DisplayName("Retry thành công khi lần đầu gọi AI lỗi transient (AI called >= 2)")
    void generate_transientError_thenRetrySuccess_returnsResponse() {
        // arrange
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(materialRepo.findByIdAndUser(materialId, mockUser)).thenReturn(Optional.of(mockMaterial));

        AtomicInteger calls = new AtomicInteger(0);
        String validJson = buildValidMcqOnlyQuestionsJson(5);

        GeminiStructuredClient aiRetry = mock(GeminiStructuredClient.class, invocation -> {
            if (invocation.getMethod().getReturnType() == String.class) {
                int n = calls.incrementAndGet();
                if (n == 1) throw new RuntimeException("Transient network error");
                return validJson;
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });

        QuestionGenerationServiceImpl service = newService(aiRetry);

        // act
        GenerateQuestionsResponse result = service.generate(email, materialId, 999);

        // assert
        assertNotNull(result);
        assertNotNull(result.getQuestions());
        assertEquals(5, result.getQuestions().size());
        assertTrue(calls.get() >= 2);
    }

    @Test
    @DisplayName("BAD_GATEWAY khi AI trả JSON sai định dạng (parse fail + retry parse -> vẫn fail)")
    void generate_invalidJson_throws502_andRetryOnce() {
        // arrange
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(mockUser));
        when(materialRepo.findByIdAndUser(materialId, mockUser)).thenReturn(Optional.of(mockMaterial));

        AtomicInteger calls = new AtomicInteger(0);

        GeminiStructuredClient aiBadJson = mock(GeminiStructuredClient.class, invocation -> {
            if (invocation.getMethod().getReturnType() == String.class) {
                calls.incrementAndGet();
                return "{ invalid json";
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });

        QuestionGenerationServiceImpl service = newService(aiBadJson);

        // act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generate(email, materialId, 999));

        // assert
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        assertTrue(calls.get() >= 2, "Parse retry should trigger a second AI call");
    }

    // ===================== helpers =====================

    /**
     * JSON hợp lệ tối thiểu - toàn MCQ (phù hợp settings mcq=5, essay=0)
     */
    private static String buildValidMcqOnlyQuestionsJson(int n) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"questions\":[");
        for (int i = 1; i <= n; i++) {
            if (i > 1) sb.append(",");
            sb.append("{")
                    .append("\"questionType\":\"MCQ\",")
                    .append("\"question\":\"Q").append(i).append("\",")
                    .append("\"options\":{\"A\":\"A").append(i).append("\",\"B\":\"B").append(i)
                    .append("\",\"C\":\"C").append(i).append("\",\"D\":\"D").append(i).append("\"},")
                    .append("\"correctAnswer\":\"A\",")
                    .append("\"analysis\":\"analysis ").append(i).append("\"")
                    .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
}