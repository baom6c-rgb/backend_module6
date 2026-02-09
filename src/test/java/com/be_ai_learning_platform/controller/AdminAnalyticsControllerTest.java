package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.AdminAnalyticsFilterRequest;
import com.be_ai_learning_platform.dto.response.AdminAnalyticsAiInsightResponse;
import com.be_ai_learning_platform.dto.response.AdminAnalyticsOverviewResponse;
import com.be_ai_learning_platform.dto.response.AtRiskStudentResponse;
import com.be_ai_learning_platform.service.AdminAnalyticsAiInsightService;
import com.be_ai_learning_platform.service.AdminAnalyticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAnalyticsController.class)
@Import(AdminAnalyticsControllerTest.MockConfig.class)
class AdminAnalyticsControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    AdminAnalyticsControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    // ========================= OVERVIEW =========================
    @Test
    void overview_success() throws Exception {
        // given
        AdminAnalyticsFilterRequest req = new AdminAnalyticsFilterRequest();
        req.setClassId(1L);
        req.setModuleId(2L);

        AdminAnalyticsOverviewResponse expectedResponse = new AdminAnalyticsOverviewResponse();
        // Set expected response data as needed

        Mockito.when(MockConfig.adminAnalyticsService.getOverview(any()))
                .thenReturn(expectedResponse);

        // when & then
        mockMvc.perform(post("/api/admin/analytics/overview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Mockito.verify(MockConfig.adminAnalyticsService).getOverview(any(AdminAnalyticsFilterRequest.class));
    }

    @Test
    void overview_withNullFilters_success() throws Exception {
        // given
        AdminAnalyticsFilterRequest req = new AdminAnalyticsFilterRequest();

        AdminAnalyticsOverviewResponse expectedResponse = new AdminAnalyticsOverviewResponse();

        Mockito.when(MockConfig.adminAnalyticsService.getOverview(any()))
                .thenReturn(expectedResponse);

        // when & then
        mockMvc.perform(post("/api/admin/analytics/overview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ========================= AI INSIGHTS =========================
    @Test
    void aiInsights_success() throws Exception {
        // given
        AdminAnalyticsFilterRequest req = new AdminAnalyticsFilterRequest();
        req.setClassId(1L);
        req.setModuleId(2L);

        AdminAnalyticsAiInsightResponse expectedResponse = new AdminAnalyticsAiInsightResponse();
        expectedResponse.setSummary("Overall analysis summary");
        expectedResponse.setKeyProblems(Arrays.asList("Problem 1", "Problem 2"));
        expectedResponse.setAtRiskPatterns(Arrays.asList("Pattern 1", "Pattern 2"));
        expectedResponse.setRecommendedActions(Arrays.asList("Action 1", "Action 2"));
        expectedResponse.setConfidence("HIGH");

        Mockito.when(MockConfig.aiInsightService.generateInsights(any()))
                .thenReturn(expectedResponse);

        // when & then
        mockMvc.perform(post("/api/admin/analytics/ai-insights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Overall analysis summary"))
                .andExpect(jsonPath("$.confidence").value("HIGH"));

        Mockito.verify(MockConfig.aiInsightService).generateInsights(any(AdminAnalyticsFilterRequest.class));
    }

    @Test
    void aiInsights_withEmptyData_success() throws Exception {
        // given
        AdminAnalyticsFilterRequest req = new AdminAnalyticsFilterRequest();

        AdminAnalyticsAiInsightResponse expectedResponse = new AdminAnalyticsAiInsightResponse();
        expectedResponse.setSummary("No data available");
        expectedResponse.setKeyProblems(List.of());
        expectedResponse.setAtRiskPatterns(List.of());
        expectedResponse.setRecommendedActions(List.of());
        expectedResponse.setConfidence("LOW");

        Mockito.when(MockConfig.aiInsightService.generateInsights(any()))
                .thenReturn(expectedResponse);

        // when & then
        mockMvc.perform(post("/api/admin/analytics/ai-insights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("No data available"))
                .andExpect(jsonPath("$.confidence").value("LOW"));
    }

    // ========================= STUDENT AI INSIGHT =========================
    @Test
    void studentAiInsight_withHighRiskScore_returnHighConfidence() throws Exception {
        // given
        Long userId = 1L;
        Long classId = 10L;
        Long moduleId = 20L;

        AtRiskStudentResponse student = new AtRiskStudentResponse();
        student.setUserId(userId);
        student.setRiskScore(85);
        student.setInsightSummary("Student is at high risk");
        student.setReasons(new String[]{"Low attendance", "Poor grades"});
        student.setWeakTopics(new String[]{"Math", "Physics"});
        student.setRecommendedNextSteps(new String[]{"Schedule tutoring", "Review basics"});

        Mockito.when(MockConfig.aiInsightService.getSingleStudentInsight(
                        anyLong(), anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(student);

        // when & then
        mockMvc.perform(get("/api/admin/analytics/students/{userId}/ai-insight", userId)
                        .param("classId", classId.toString())
                        .param("moduleId", moduleId.toString())
                        .param("from", "2024-01-01T00:00:00")
                        .param("to", "2024-12-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Student is at high risk"))
                .andExpect(jsonPath("$.confidence").value("HIGH"))
                .andExpect(jsonPath("$.keyProblems[0]").value("Low attendance"))
                .andExpect(jsonPath("$.keyProblems[1]").value("Poor grades"))
                .andExpect(jsonPath("$.atRiskPatterns[0]").value("Math"))
                .andExpect(jsonPath("$.atRiskPatterns[1]").value("Physics"))
                .andExpect(jsonPath("$.recommendedActions[0]").value("Schedule tutoring"))
                .andExpect(jsonPath("$.students[0].userId").value(userId))
                .andExpect(jsonPath("$.generatedAt").exists());

        Mockito.verify(MockConfig.aiInsightService).getSingleStudentInsight(
                userId, classId, moduleId, "2024-01-01T00:00:00", "2024-12-31T23:59:59"
        );
    }

    @Test
    void studentAiInsight_withMediumRiskScore_returnMediumConfidence() throws Exception {
        // given
        Long userId = 2L;

        AtRiskStudentResponse student = new AtRiskStudentResponse();
        student.setUserId(userId);
        student.setRiskScore(65);
        student.setInsightSummary("Student shows some concerns");
        student.setReasons(new String[]{"Inconsistent performance"});
        student.setWeakTopics(new String[]{"Algebra"});
        student.setRecommendedNextSteps(new String[]{"Monitor progress"});

        Mockito.when(MockConfig.aiInsightService.getSingleStudentInsight(
                        anyLong(), any(), any(), any(), any()))
                .thenReturn(student);

        // when & then
        mockMvc.perform(get("/api/admin/analytics/students/{userId}/ai-insight", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value("MEDIUM"))
                .andExpect(jsonPath("$.summary").value("Student shows some concerns"));
    }

    @Test
    void studentAiInsight_withLowRiskScore_returnLowConfidence() throws Exception {
        // given
        Long userId = 3L;

        AtRiskStudentResponse student = new AtRiskStudentResponse();
        student.setUserId(userId);
        student.setRiskScore(30);
        student.setInsightSummary("Student is performing well");
        student.setReasons(new String[]{});
        student.setWeakTopics(new String[]{});
        student.setRecommendedNextSteps(new String[]{"Continue current approach"});

        Mockito.when(MockConfig.aiInsightService.getSingleStudentInsight(
                        anyLong(), any(), any(), any(), any()))
                .thenReturn(student);

        // when & then
        mockMvc.perform(get("/api/admin/analytics/students/{userId}/ai-insight", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value("LOW"))
                .andExpect(jsonPath("$.summary").value("Student is performing well"));
    }

    @Test
    void studentAiInsight_withNullStudent_returnEmptyData() throws Exception {
        // given
        Long userId = 4L;

        Mockito.when(MockConfig.aiInsightService.getSingleStudentInsight(
                        anyLong(), any(), any(), any(), any()))
                .thenReturn(null);

        // when & then
        mockMvc.perform(get("/api/admin/analytics/students/{userId}/ai-insight", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").isEmpty())
                .andExpect(jsonPath("$.confidence").value("LOW"))
                .andExpect(jsonPath("$.keyProblems").isEmpty())
                .andExpect(jsonPath("$.atRiskPatterns").isEmpty())
                .andExpect(jsonPath("$.recommendedActions").isEmpty())
                .andExpect(jsonPath("$.students").isEmpty())
                .andExpect(jsonPath("$.generatedAt").exists());
    }

    @Test
    void studentAiInsight_withNullArrays_returnEmptyLists() throws Exception {
        // given
        Long userId = 5L;

        AtRiskStudentResponse student = new AtRiskStudentResponse();
        student.setUserId(userId);
        student.setRiskScore(50);
        student.setInsightSummary("Limited data available");
        student.setReasons(null);
        student.setWeakTopics(null);
        student.setRecommendedNextSteps(null);

        Mockito.when(MockConfig.aiInsightService.getSingleStudentInsight(
                        anyLong(), any(), any(), any(), any()))
                .thenReturn(student);

        // when & then
        mockMvc.perform(get("/api/admin/analytics/students/{userId}/ai-insight", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Limited data available"))
                .andExpect(jsonPath("$.confidence").value("MEDIUM"))
                .andExpect(jsonPath("$.keyProblems").isEmpty())
                .andExpect(jsonPath("$.atRiskPatterns").isEmpty())
                .andExpect(jsonPath("$.recommendedActions").isEmpty())
                .andExpect(jsonPath("$.students[0].userId").value(userId));
    }

    @Test
    void studentAiInsight_withoutQueryParams_success() throws Exception {
        // given
        Long userId = 6L;

        AtRiskStudentResponse student = new AtRiskStudentResponse();
        student.setUserId(userId);
        student.setRiskScore(70);
        student.setInsightSummary("General insight");
        student.setReasons(new String[]{"Reason"});
        student.setWeakTopics(new String[]{"Topic"});
        student.setRecommendedNextSteps(new String[]{"Action"});

        Mockito.when(MockConfig.aiInsightService.getSingleStudentInsight(
                        anyLong(), any(), any(), any(), any()))
                .thenReturn(student);

        // when & then
        mockMvc.perform(get("/api/admin/analytics/students/{userId}/ai-insight", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value("MEDIUM"));

        Mockito.verify(MockConfig.aiInsightService).getSingleStudentInsight(
                userId, null, null, null, null
        );
    }

    @Test
    void studentAiInsight_withNullRiskScore_returnLowConfidence() throws Exception {
        // given
        Long userId = 7L;

        AtRiskStudentResponse student = new AtRiskStudentResponse();
        student.setUserId(userId);
        student.setRiskScore(null);
        student.setInsightSummary("Risk score unavailable");
        student.setReasons(new String[]{"Unknown"});
        student.setWeakTopics(new String[]{});
        student.setRecommendedNextSteps(new String[]{"Collect more data"});

        Mockito.when(MockConfig.aiInsightService.getSingleStudentInsight(
                        anyLong(), any(), any(), any(), any()))
                .thenReturn(student);

        // when & then
        mockMvc.perform(get("/api/admin/analytics/students/{userId}/ai-insight", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value("LOW"))
                .andExpect(jsonPath("$.summary").value("Risk score unavailable"));
    }

    // ========================= MOCK CONFIG =========================
    static class MockConfig {

        static final AdminAnalyticsService adminAnalyticsService = Mockito.mock(AdminAnalyticsService.class);
        static final AdminAnalyticsAiInsightService aiInsightService = Mockito.mock(AdminAnalyticsAiInsightService.class);

        @Bean
        AdminAnalyticsService adminAnalyticsService() {
            return adminAnalyticsService;
        }

        @Bean
        AdminAnalyticsAiInsightService adminAnalyticsAiInsightService() {
            return aiInsightService;
        }
    }
}