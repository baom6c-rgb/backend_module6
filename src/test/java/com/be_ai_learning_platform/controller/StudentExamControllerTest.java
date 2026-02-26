package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.GenerateQuestionsRequest;
import com.be_ai_learning_platform.dto.response.GenerateQuestionsResponse;
import com.be_ai_learning_platform.service.QuestionGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@WebMvcTest(StudentExamController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(StudentExamControllerTest.MockConfig.class)
class StudentExamControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuestionGenerationService questionGenerationService;

    // ========================= GENERATE QUESTIONS =========================
    @Test
    @WithMockUser(username = "student@test.com")
    void generate_success() throws Exception {
        // given
        String email = "student@test.com";
        Long materialId = 1L;
        Integer numberOfQuestions = 10;

        String requestJson = """
            {
                "materialId": 1,
                "numberOfQuestions": 10
            }
            """;

        when(questionGenerationService.generate(email, materialId, numberOfQuestions))
                .thenReturn(any(GenerateQuestionsResponse.class));

        // when & then
        mockMvc.perform(post("/api/student/exams/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(questionGenerationService).generate(email, materialId, numberOfQuestions);
    }

    @Test
    @WithMockUser(username = "student@test.com")
    void generate_withDifferentNumberOfQuestions_success() throws Exception {
        // given
        String email = "student@test.com";
        Long materialId = 2L;
        Integer numberOfQuestions = 5;

        String requestJson = """
            {
                "materialId": 2,
                "numberOfQuestions": 5
            }
            """;

        when(questionGenerationService.generate(email, materialId, numberOfQuestions))
                .thenReturn(any(GenerateQuestionsResponse.class));

        // when & then
        mockMvc.perform(post("/api/student/exams/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(questionGenerationService).generate(email, materialId, numberOfQuestions);
    }

    @Test
    @WithMockUser(username = "another@test.com")
    void generate_withDifferentUser_success() throws Exception {
        // given
        String email = "another@test.com";
        Long materialId = 3L;
        Integer numberOfQuestions = 15;

        String requestJson = """
            {
                "materialId": 3,
                "numberOfQuestions": 15
            }
            """;

        when(questionGenerationService.generate(email, materialId, numberOfQuestions))
                .thenReturn(any(GenerateQuestionsResponse.class));

        // when & then
        mockMvc.perform(post("/api/student/exams/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(questionGenerationService).generate(email, materialId, numberOfQuestions);
    }

    @Test
    void generate_unauthenticated_return401() throws Exception {
        // given
        String requestJson = """
            {
                "materialId": 1,
                "numberOfQuestions": 10
            }
            """;

        // when & then
        mockMvc.perform(post("/api/student/exams/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnauthorized());

        verify(questionGenerationService, never()).generate(anyString(), anyLong(), anyInt());
    }

    @Test
    @WithMockUser(username = "student@test.com")
    void generate_serviceThrowsException_returnError() throws Exception {
        // given
        String email = "student@test.com";
        Long materialId = 1L;
        Integer numberOfQuestions = 10;

        String requestJson = """
            {
                "materialId": 1,
                "numberOfQuestions": 10
            }
            """;

        when(questionGenerationService.generate(email, materialId, numberOfQuestions))
                .thenThrow(new RuntimeException("Material not found"));

        // when & then
        mockMvc.perform(post("/api/student/exams/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().is5xxServerError());

        verify(questionGenerationService).generate(email, materialId, numberOfQuestions);
    }

    // ========================= MOCK CONFIG =========================

    @TestConfiguration
    static class MockConfig {
        @Bean
        QuestionGenerationService questionGenerationService() {
            return Mockito.mock(QuestionGenerationService.class);
        }
    }

}
