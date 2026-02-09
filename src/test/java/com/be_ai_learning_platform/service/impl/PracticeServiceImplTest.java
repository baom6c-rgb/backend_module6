package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.ai.GeminiResponsesClient;
import com.be_ai_learning_platform.dto.request.PracticeGenerateRequest;
import com.be_ai_learning_platform.dto.request.SubmitPracticeRequest;
import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;
import com.be_ai_learning_platform.dto.response.GeneratedQuestionItemResponse;
import com.be_ai_learning_platform.dto.response.SubmitPracticeResponse;
import com.be_ai_learning_platform.entity.*;
import com.be_ai_learning_platform.entity.enums.ExamResult;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import com.be_ai_learning_platform.entity.enums.QuestionType;
import com.be_ai_learning_platform.repository.*;
import com.be_ai_learning_platform.service.QuestionGenerationService;
import com.be_ai_learning_platform.service.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PracticeServiceImplTest {

    @Mock private UserRepository userRepo;
    @Mock private LearningMaterialRepository materialRepo;
    @Mock private ExamRepository examRepo;
    @Mock private ExamAttemptRepository attemptRepo;
    @Mock private ExamQuestionRepository examQuestionRepo;
    @Mock private QuestionRepository questionRepo;
    @Mock private QuestionGenerationService questionGenerationService;
    @Mock private GeminiResponsesClient responsesClient;
    @Mock private SystemSettingsService settingsService;
    @Mock private Cache<String, Object> practiceSessionCache;

    @Spy
    private ObjectMapper om = new ObjectMapper();

    @InjectMocks
    private PracticeServiceImpl practiceService;

    private User mockUser;
    private LearningMaterial mockMaterial;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setEmail("test@gmail.com");
        mockUser.setFullName("Test User");

        mockMaterial = new LearningMaterial();
        mockMaterial.setId(10L);
        mockMaterial.setStatus(MaterialStatus.EXTRACTED);
        mockMaterial.setFileName("Lesson 1.pdf");
    }

    @Test
    @DisplayName("Generate Preview - Thành công khi không có cache")
    void generatePreview_Success_NewGeneration() {
        // Given
        PracticeGenerateRequest req = new PracticeGenerateRequest();
        req.setMaterialId(10L);
        req.setNumberOfQuestions(3);
        req.setPreviewToken("");

        when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
        when(materialRepo.findByIdAndUser(10L, mockUser)).thenReturn(Optional.of(mockMaterial));

        GenerateQuestionsResponse aiResponse = new GenerateQuestionsResponse();
        aiResponse.setQuestions(Collections.nCopies(3, new GeneratedQuestionItemResponse()));
        when(questionGenerationService.generate(anyString(), anyLong(), anyInt())).thenReturn(aiResponse);

        // When
        GenerateQuestionsResponse result = practiceService.generatePreview(mockUser.getEmail(), req);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPreviewToken()).isNotBlank();
        verify(practiceSessionCache).put(anyString(), eq(result));
    }

    @Test
    @DisplayName("Submit V1 - Tính điểm đúng cho bài thi chỉ có MCQ")
    void submit_Success_OnlyMCQ() {
        // Given
        Long attemptId = 100L;
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(attemptId);
        attempt.setUser(mockUser);
        attempt.setStatus(ExamResult.IN_PROGRESS);

        Exam exam = new Exam();
        exam.setPassScore(50);
        attempt.setExam(exam);

        Question q1 = new Question();
        q1.setId(1L);
        q1.setQuestionType(QuestionType.MCQ);
        q1.setCorrectAnswer("A");

        ExamQuestion eq1 = new ExamQuestion();
        eq1.setQuestion(q1);

        SubmitPracticeRequest.AnswerItem ans1 = new SubmitPracticeRequest.AnswerItem();
        ans1.setQuestionId(1L);
        ans1.setSelectedAnswer("A");

        SubmitPracticeRequest req = new SubmitPracticeRequest();
        req.setAnswers(List.of(ans1));

        when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
        when(attemptRepo.findByIdAndUserId(attemptId, mockUser.getId())).thenReturn(Optional.of(attempt));
        when(examQuestionRepo.findAllByExamIdFetchQuestion(any())).thenReturn(List.of(eq1));
        when(settingsService.getPassScore()).thenReturn(50);
        when(responsesClient.generateText(anyString())).thenReturn("AI Feedback");

        // When
        SubmitPracticeResponse response = practiceService.submit(mockUser.getEmail(), attemptId, req);

        // Then
        assertThat(response.getScore()).isEqualTo(100); // 1 câu đúng duy nhất = 100%
        assertThat(attempt.getStatus()).isEqualTo(ExamResult.PASSED);
        verify(attemptRepo).save(attempt);
    }

    @Test
    @DisplayName("Submit V1 - Thất bại nếu Attempt đã nộp trước đó")
    void submit_Fail_AlreadySubmitted() {
        // Given
        ExamAttempt attempt = new ExamAttempt();
        attempt.setStatus(ExamResult.SUBMITTED);
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.of(mockUser));
        when(attemptRepo.findByIdAndUserId(anyLong(), anyLong())).thenReturn(Optional.of(attempt));

        SubmitPracticeRequest req = new SubmitPracticeRequest();
        req.setAnswers(new ArrayList<>());

        // When & Then
        assertThrows(ResponseStatusException.class, () -> {
            practiceService.submit(mockUser.getEmail(), 1L, req);
        });
    }

    @Test
    @DisplayName("Validate Request - Bắn lỗi nếu số lượng câu hỏi vượt quá giới hạn")
    void validateGenerateRequest_Fail_TooManyQuestions() {
        PracticeGenerateRequest req = new PracticeGenerateRequest();
        req.setMaterialId(1L);
        req.setNumberOfQuestions(50); // MAX_AI_FEEDBACK_CHARS = 20

        assertThrows(ResponseStatusException.class, () -> {
            practiceService.generatePreview("test@mail.com", req);
        });
    }
}