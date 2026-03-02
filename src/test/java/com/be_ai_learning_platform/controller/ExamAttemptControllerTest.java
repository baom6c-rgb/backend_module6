package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.UserExamAttemptDTO;
import com.be_ai_learning_platform.entity.ExamAttempt;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.ExamAttemptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@WebMvcTest(ExamAttemptController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ExamAttemptControllerTest.MockConfig.class)
class ExamAttemptControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExamAttemptService examAttemptService;

    @Autowired
    private UserRepository userRepository;

    // ========================= GET MY ATTEMPTS =========================
    @Test
    @WithMockUser(username = "student@test.com")
    void getMyAttempts_success() throws Exception {
        // given
        String email = "student@test.com";
        User user = new User();
        user.setId(1L);
        user.setEmail(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));
        when(examAttemptService.getMyAttempts(1L))
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/exam-attempts/my-attempts"))
                .andExpect(status().isOk());

        verify(userRepository).findByEmail(email);
        verify(examAttemptService).getMyAttempts(1L);
    }

    @Test
    @WithMockUser(username = "student@test.com")
    void getMyAttempts_userNotFound_throwException() throws Exception {
        // given
        String email = "student@test.com";

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/api/exam-attempts/my-attempts"))
                .andExpect(status().is5xxServerError());

        verify(userRepository).findByEmail(email);
        verify(examAttemptService, never()).getMyAttempts(anyLong());
    }

    @Test
    void getMyAttempts_unauthenticated_return401() throws Exception {
        // when & then
        mockMvc.perform(get("/api/exam-attempts/my-attempts"))
                .andExpect(status().isUnauthorized());

        verify(userRepository, never()).findByEmail(anyString());
        verify(examAttemptService, never()).getMyAttempts(anyLong());
    }

    // ========================= GET STATS =========================
    @Test
    void getStats_success() throws Exception {
        // given
        Map<String, Object> mockStats = new HashMap<>();
        mockStats.put("totalExams", 10);
        mockStats.put("averageScore", 85.5);
        mockStats.put("ranking", 5);

        when(examAttemptService.getExamStats(1L))
                .thenReturn(mockStats);

        // when & then
        mockMvc.perform(get("/api/exam-attempts/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExams").value(10))
                .andExpect(jsonPath("$.averageScore").value(85.5))
                .andExpect(jsonPath("$.ranking").value(5));

        verify(examAttemptService).getExamStats(1L);
    }

    @Test
    void getStats_emptyStats_success() throws Exception {
        // given
        when(examAttemptService.getExamStats(1L))
                .thenReturn(new HashMap<>());

        // when & then
        mockMvc.perform(get("/api/exam-attempts/stats"))
                .andExpect(status().isOk());

        verify(examAttemptService).getExamStats(1L);
    }

    // ========================= GET DETAIL =========================
    @Test
    void getDetail_success() throws Exception {
        // given
        Long attemptId = 123L;
        ExamAttempt mockAttempt = new ExamAttempt();

        when(examAttemptService.getAttemptById(attemptId))
                .thenReturn(mockAttempt);

        // when & then
        mockMvc.perform(get("/api/exam-attempts/{id}", attemptId))
                .andExpect(status().isOk());

        verify(examAttemptService).getAttemptById(attemptId);
    }

    @Test
    void getDetail_attemptNotFound_throwException() throws Exception {
        // given
        Long attemptId = 999L;

        when(examAttemptService.getAttemptById(attemptId))
                .thenThrow(new RuntimeException("Attempt not found"));

        // when & then
        mockMvc.perform(get("/api/exam-attempts/{id}", attemptId))
                .andExpect(status().is5xxServerError());

        verify(examAttemptService).getAttemptById(attemptId);
    }

    // ========================= GET ALL ATTEMPTS (ADMIN) =========================
    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void getAllAttempts_asAdmin_success() throws Exception {
        // given
        when(examAttemptService.getAllAttemptsForAdmin())
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/exam-attempts/admin/all-attempts"))
                .andExpect(status().isOk());

        verify(examAttemptService).getAllAttemptsForAdmin();
    }

    @Test
    @WithMockUser(username = "student@test.com", roles = {"STUDENT"})
    void getAllAttempts_asStudent_return403() throws Exception {
        // when & then
        mockMvc.perform(get("/api/exam-attempts/admin/all-attempts"))
                .andExpect(status().isForbidden());

        verify(examAttemptService, never()).getAllAttemptsForAdmin();
    }

    @Test
    void getAllAttempts_unauthenticated_return401() throws Exception {
        // when & then
        mockMvc.perform(get("/api/exam-attempts/admin/all-attempts"))
                .andExpect(status().isUnauthorized());

        verify(examAttemptService, never()).getAllAttemptsForAdmin();
    }

    // ========================= START EXAM =========================
    @Test
    void startExam_success() throws Exception {
        // given
        Long examId = 456L;
        String requestJson = """
            {
                "examId": 456
            }
            """;

        ExamAttempt mockAttempt = new ExamAttempt();
        when(examAttemptService.startExam(1L, examId))
                .thenReturn(mockAttempt);

        // when & then
        mockMvc.perform(post("/api/exam-attempts/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(examAttemptService).startExam(1L, examId);
    }

    @Test
    void startExam_withDifferentExamId_success() throws Exception {
        // given
        Long examId = 789L;
        String requestJson = """
            {
                "examId": 789
            }
            """;

        ExamAttempt mockAttempt = new ExamAttempt();
        when(examAttemptService.startExam(1L, examId))
                .thenReturn(mockAttempt);

        // when & then
        mockMvc.perform(post("/api/exam-attempts/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(examAttemptService).startExam(1L, examId);
    }

    @Test
    void startExam_examNotFound_throwException() throws Exception {
        // given
        Long examId = 999L;
        String requestJson = """
            {
                "examId": 999
            }
            """;

        when(examAttemptService.startExam(1L, examId))
                .thenThrow(new RuntimeException("Exam not found"));

        // when & then
        mockMvc.perform(post("/api/exam-attempts/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().is5xxServerError());

        verify(examAttemptService).startExam(1L, examId);
    }

    // ========================= SUBMIT EXAM =========================
    @Test
    void submitExam_success() throws Exception {
        // given
        Long attemptId = 100L;
        String requestJson = """
            {
                "answers": [
                    {"questionId": 1, "selectedAnswer": "A"},
                    {"questionId": 2, "selectedAnswer": "B"}
                ]
            }
            """;

        ExamAttempt mockAttempt = new ExamAttempt();
        when(examAttemptService.submitExam(eq(attemptId), any()))
                .thenReturn(mockAttempt);

        // when & then
        mockMvc.perform(post("/api/exam-attempts/{id}/submit", attemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(examAttemptService).submitExam(eq(attemptId), any());
    }

    @Test
    void submitExam_withEmptyAnswers_success() throws Exception {
        // given
        Long attemptId = 200L;
        String requestJson = """
            {
                "answers": []
            }
            """;

        ExamAttempt mockAttempt = new ExamAttempt();
        when(examAttemptService.submitExam(eq(attemptId), any()))
                .thenReturn(mockAttempt);

        // when & then
        mockMvc.perform(post("/api/exam-attempts/{id}/submit", attemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(examAttemptService).submitExam(eq(attemptId), any());
    }

    @Test
    void submitExam_attemptNotFound_throwException() throws Exception {
        // given
        Long attemptId = 999L;
        String requestJson = """
            {
                "answers": [{"questionId": 1, "selectedAnswer": "A"}]
            }
            """;

        when(examAttemptService.submitExam(eq(attemptId), any()))
                .thenThrow(new RuntimeException("Attempt not found"));

        // when & then
        mockMvc.perform(post("/api/exam-attempts/{id}/submit", attemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().is5xxServerError());

        verify(examAttemptService).submitExam(eq(attemptId), any());
    }

    @Test
    void submitExam_invalidAnswersFormat_throwException() throws Exception {
        // given
        Long attemptId = 300L;
        String requestJson = """
            {
                "answers": "invalid format"
            }
            """;

        when(examAttemptService.submitExam(eq(attemptId), any()))
                .thenThrow(new RuntimeException("Invalid answers format"));

        // when & then
        mockMvc.perform(post("/api/exam-attempts/{id}/submit", attemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().is5xxServerError());

        verify(examAttemptService).submitExam(eq(attemptId), any());
    }

    // ========================= MOCK CONFIG =========================

    @TestConfiguration
    static class MockConfig {
        @Bean
        ExamAttemptService examAttemptService() {
            return Mockito.mock(ExamAttemptService.class);
        }

        @Bean
        UserRepository userRepository() {
            return Mockito.mock(UserRepository.class);
        }
    }

}
