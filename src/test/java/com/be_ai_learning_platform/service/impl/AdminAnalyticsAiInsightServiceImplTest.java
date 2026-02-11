package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.AI.GeminiStructuredClient;
import com.be_ai_learning_platform.dto.request.AdminAnalyticsFilterRequest;
import com.be_ai_learning_platform.dto.response.AdminAnalyticsAiInsightResponse;
import com.be_ai_learning_platform.dto.response.AdminAnalyticsOverviewResponse;
import com.be_ai_learning_platform.dto.response.AtRiskStudentResponse;
import com.be_ai_learning_platform.repository.AdminAnalyticsRepository;
import com.be_ai_learning_platform.service.AdminAnalyticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsAiInsightServiceImplTest {

    @Mock
    private AdminAnalyticsService adminAnalyticsService;
    @Mock
    private AdminAnalyticsRepository analyticsRepository;
    @Mock
    private GeminiStructuredClient geminiClient;

    @Spy // Dùng Spy để ObjectMapper hoạt động thật như trong code
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AdminAnalyticsAiInsightServiceImpl aiInsightService;

    private AdminAnalyticsFilterRequest filterRequest;

    @BeforeEach
    void setUp() {
        filterRequest = new AdminAnalyticsFilterRequest();
        filterRequest.setFrom("2023-01-01");
        filterRequest.setTo("2023-12-31");
    }

    @Test
    @DisplayName("generateInsights - Trả về dữ liệu từ Gemini thành công")
    void generateInsights_Success() throws Exception {
        // GIVEN
        AdminAnalyticsOverviewResponse overview = new AdminAnalyticsOverviewResponse();
        overview.setTotalAttempts(100L);
        overview.setTotalStudents(10L);
        overview.setAvgScore(75.0);
        overview.setFailRate(0.1);
        overview.setPassRate(0.9);
        overview.setAtRiskStudents(List.of(new AtRiskStudentResponse()));

        when(adminAnalyticsService.getOverview(any())).thenReturn(overview);

        String mockJsonResponse = """
            {
              "summary": "Kết quả học tập tốt",
              "keyProblems": ["Vấn đề A"],
              "atRiskPatterns": ["Pattern B"],
              "recommendedActions": ["Hành động C"],
              "confidence": "HIGH",
              "generatedAt": "2026-02-09T10:00:00"
            }
            """;
        when(geminiClient.generateJson(anyString(), anyString())).thenReturn(mockJsonResponse);

        // WHEN
        AdminAnalyticsAiInsightResponse result = aiInsightService.generateInsights(filterRequest);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.getSummary()).isEqualTo("Kết quả học tập tốt");
        assertThat(result.getConfidence()).isEqualTo("HIGH");
        verify(geminiClient, times(1)).generateJson(anyString(), anyString());
    }

    @Test
    @DisplayName("generateInsights - Trả về fallback khi không có dữ liệu bài tập")
    void generateInsights_NoDataFallback() {
        // GIVEN
        AdminAnalyticsOverviewResponse overview = new AdminAnalyticsOverviewResponse();
        overview.setTotalAttempts(0L); // Không có bài làm

        when(adminAnalyticsService.getOverview(any())).thenReturn(overview);

        // WHEN
        AdminAnalyticsAiInsightResponse result = aiInsightService.generateInsights(filterRequest);

        // THEN
        assertThat(result.getSummary()).contains("Chưa có dữ liệu");
        assertThat(result.getConfidence()).isEqualTo("LOW");
        verifyNoInteractions(geminiClient);
    }

    @Test
    @DisplayName("getSingleStudentInsight - Thành công khi có dữ liệu từ DB và AI")
    void getSingleStudentInsight_Success() throws Exception {
        // GIVEN
        Long userId = 1L;
        AdminAnalyticsRepository.StudentAggByUserRow mockRow = mock(AdminAnalyticsRepository.StudentAggByUserRow.class);
        when(mockRow.getUserId()).thenReturn(userId);
        when(mockRow.getFullName()).thenReturn("Nguyen Van A");
        when(mockRow.getAttemptsCount()).thenReturn(5L);
        when(mockRow.getAvgScore()).thenReturn(45.0); // Rủi ro thấp điểm

        when(analyticsRepository.getStudentAggByUser(anyLong(), any(), any(), any(), any())).thenReturn(mockRow);

        String mockAiResponse = """
            {
              "insightSummary": "Cần cố gắng hơn",
              "strengths": ["Chăm chỉ"],
              "weakTopics": ["Java Core"],
              "recommendedNextSteps": ["Học lại OOP"]
            }
            """;
        when(geminiClient.generateJson(anyString(), anyString())).thenReturn(mockAiResponse);

        // WHEN
        AtRiskStudentResponse result = aiInsightService.getSingleStudentInsight(userId, null, null, null, null);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(result.getInsightSummary()).isEqualTo("Cần cố gắng hơn");
        assertThat(result.getWeakTopics()).contains("Java Core");
        verify(analyticsRepository).getStudentAggByUser(eq(userId), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getSingleStudentInsight - Chạy Heuristic Fallback khi Gemini lỗi")
    void getSingleStudentInsight_AiErrorFallback() throws Exception {
        // GIVEN
        Long userId = 1L;
        AdminAnalyticsRepository.StudentAggByUserRow mockRow = mock(AdminAnalyticsRepository.StudentAggByUserRow.class);
        when(mockRow.getUserId()).thenReturn(userId);
        when(mockRow.getAvgScore()).thenReturn(30.0);

        when(analyticsRepository.getStudentAggByUser(any(), any(), any(), any(), any())).thenReturn(mockRow);
        when(geminiClient.generateJson(anyString(), anyString())).thenThrow(new RuntimeException("Gemini Down"));

        // WHEN
        AtRiskStudentResponse result = aiInsightService.getSingleStudentInsight(userId, null, null, null, null);

        // THEN
        assertThat(result).isNotNull();
        // Kiểm tra xem summary có được tạo bởi hàm applyStudentHeuristic không
        assertThat(result.getInsightSummary()).contains("30.0");
        assertThat(result.getRecommendedNextSteps()).isNotEmpty();
    }

    @Test
    @DisplayName("Test cache hoạt động - Gọi generateInsights 2 lần cùng filter chỉ gọi service 1 lần")
    void generateInsights_CacheTest() throws Exception {
        // GIVEN
        AdminAnalyticsOverviewResponse overview = new AdminAnalyticsOverviewResponse();
        overview.setTotalAttempts(50L);
        when(adminAnalyticsService.getOverview(any())).thenReturn(overview);
        when(geminiClient.generateJson(anyString(), anyString())).thenReturn("{}");

        // WHEN
        aiInsightService.generateInsights(filterRequest);
        aiInsightService.generateInsights(filterRequest); // Lần 2

        // THEN
        // verify overview chỉ được gọi 1 lần dù generateInsights gọi 2 lần
        verify(adminAnalyticsService, times(1)).getOverview(any());
    }
}