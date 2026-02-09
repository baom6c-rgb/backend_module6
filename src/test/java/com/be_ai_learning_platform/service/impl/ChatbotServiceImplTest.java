package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.ai.GeminiResponsesClient;
import com.be_ai_learning_platform.dto.response.ChatAskResponse;
import com.be_ai_learning_platform.entity.ChatSession;
import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.repository.ChatMessageRepository;
import com.be_ai_learning_platform.repository.ChatSessionRepository;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceImplTest {

    @Mock private GeminiResponsesClient responsesClient;
    @Mock private UserRepository userRepo;
    @Mock private LearningMaterialRepository materialRepo;
    @Mock private ChatSessionRepository sessionRepo;
    @Mock private ChatMessageRepository messageRepo;

    @InjectMocks
    private ChatbotServiceImpl chatbotService;

    private User mockUser;
    private LearningMaterial mockMaterial;
    private ChatSession mockSession;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@example.com");

        mockMaterial = new LearningMaterial();
        mockMaterial.setId(10L);
        mockMaterial.setExtractedText("Lập trình Java là ngôn ngữ hướng đối tượng. Tính đóng gói và đa hình là quan trọng.");

        mockSession = new ChatSession();
        mockSession.setId(100L);
        mockSession.setUser(mockUser);
        mockSession.setMaterial(mockMaterial);

        // Mock Security Context cho getCurrentUser()
        Authentication auth = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(auth.getName()).thenReturn("test@example.com");
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    @Nested
    @DisplayName("Test logic Validate Keywords (US17)")
    class ValidateKeywordsTest {

        @Test
        @DisplayName("Ném lỗi khi nhập câu hỏi có dấu chấm hỏi hoặc dấu chấm")
        void ask_ShouldFail_WhenInputIsAQuestion() {
            when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
            when(sessionRepo.findById(anyLong())).thenReturn(Optional.of(mockSession));

            assertThatThrownBy(() -> chatbotService.ask(100L, "Java là gì?"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Chỉ nhập từ khóa, không nhập dạng câu hỏi");
        }

        @Test
        @DisplayName("Ném lỗi khi dán nguyên đoạn văn (chứa ký tự xuống dòng)")
        void ask_ShouldFail_WhenInputContainsNewLines() {
            when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
            when(sessionRepo.findById(anyLong())).thenReturn(Optional.of(mockSession));

            assertThatThrownBy(() -> chatbotService.ask(100L, "Dòng 1\nDòng 2"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Không được dán nguyên câu hỏi");
        }
    }

    @Nested
    @DisplayName("Test luồng Xử lý AI & Relevance")
    class AiFlowTest {

        @Test
        @DisplayName("Trả về tin nhắn từ chối khi từ khóa không có trong tài liệu")
        void ask_ShouldReturnOutOfScope_WhenNoHits() {
            // GIVEN
            when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
            when(sessionRepo.findById(anyLong())).thenReturn(Optional.of(mockSession));

            // "Nấu ăn" không có trong extractedText về "Java"
            String keywords = "Cách nấu ăn";

            // WHEN
            ChatAskResponse response = chatbotService.ask(100L, keywords);

            // THEN
            assertThat(response.getAnswer()).contains("không tồn tại trong tài liệu");
            verify(responsesClient, never()).generateText(anyString());
        }

        @Test
        @DisplayName("Gọi Gemini và lọc kết quả thành công khi từ khóa hợp lệ")
        void ask_ShouldSucceed_WhenKeywordsAreRelevant() {
            // GIVEN
            when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
            when(sessionRepo.findById(anyLong())).thenReturn(Optional.of(mockSession));

            String aiRawResponse = """
                    - Java là ngôn ngữ hướng đối tượng
                    - Nó hỗ trợ tính đa hình mạnh mẽ
                    - Đóng gói giúp bảo vệ dữ liệu
                    """;
            when(responsesClient.generateText(anyString())).thenReturn(aiRawResponse);

            // WHEN
            ChatAskResponse response = chatbotService.ask(100L, "Java, Đóng gói");

            // THEN
            assertThat(response.getAnswer()).contains("- Java là ngôn ngữ");
            verify(messageRepo, times(2)).save(any()); // 1 cho User, 1 cho AI
        }

        @Test
        @DisplayName("Chặn phản hồi nếu AI vô tình trả về đáp án (Post-filter)")
        void ask_ShouldBlock_WhenAiReturnsAnswer() {
            // GIVEN
            when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
            when(sessionRepo.findById(anyLong())).thenReturn(Optional.of(mockSession));

            // Giả lập AI vi phạm guardrail, trả về chữ "Đáp án"
            when(responsesClient.generateText(anyString())).thenReturn("- Đáp án đúng là câu A");

            // WHEN
            ChatAskResponse response = chatbotService.ask(100L, "Java");

            // THEN
            assertThat(response.getAnswer()).isEqualTo("Mình không thể đưa ra đáp án. Hãy thử suy nghĩ dựa trên các khái niệm liên quan.");
        }
    }

    @Test
    @DisplayName("startSession - Tạo session mới nếu chưa tồn tại")
    void startSession_ShouldCreateNew_WhenNotFound() {
        // GIVEN
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
        when(materialRepo.findByIdAndUser(anyLong(), any())).thenReturn(Optional.of(mockMaterial));
        when(sessionRepo.findTopByUser_IdAndMaterial_IdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(Optional.empty());
        when(sessionRepo.save(any())).thenReturn(mockSession);

        // WHEN
        var result = chatbotService.startSession(10L);

        // THEN
        assertThat(result.getSessionId()).isEqualTo(100L);
        verify(sessionRepo).save(any(ChatSession.class));
    }
}