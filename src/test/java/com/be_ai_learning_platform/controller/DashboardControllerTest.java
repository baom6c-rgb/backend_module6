package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.UserDashboardStatsDTO;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.DashboardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import(DashboardControllerTest.MockConfig.class)
class DashboardControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    DashboardControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    // ========================= GET DASHBOARD STATS =========================
    @Test
    @WithMockUser(username = "student@test.com")
    void getDashboardStats_success() throws Exception {
        // given
        String email = "student@test.com";
        User user = new User();
        user.setId(1L);
        user.setEmail(email);

        when(MockConfig.userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));
        when(MockConfig.dashboardService.getStats(1L))
                .thenReturn(any(UserDashboardStatsDTO.class));

        // when & then
        mockMvc.perform(get("/api/users/dashboard/stats"))
                .andExpect(status().isOk());

        verify(MockConfig.userRepository).findByEmail(email);
        verify(MockConfig.dashboardService).getStats(1L);
    }

    @Test
    @WithMockUser(username = "student@test.com")
    void getDashboardStats_userNotFound_return500() throws Exception {
        // given
        String email = "student@test.com";

        when(MockConfig.userRepository.findByEmail(email))
                .thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/api/users/dashboard/stats"))
                .andExpect(status().isInternalServerError());

        verify(MockConfig.userRepository).findByEmail(email);
        verify(MockConfig.dashboardService, never()).getStats(anyLong());
    }

    @Test
    @WithMockUser(username = "student@test.com")
    void getDashboardStats_serviceThrowsException_return500() throws Exception {
        // given
        String email = "student@test.com";
        User user = new User();
        user.setId(1L);
        user.setEmail(email);

        when(MockConfig.userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));
        when(MockConfig.dashboardService.getStats(1L))
                .thenThrow(new RuntimeException("Database error"));

        // when & then
        mockMvc.perform(get("/api/users/dashboard/stats"))
                .andExpect(status().isInternalServerError());

        verify(MockConfig.userRepository).findByEmail(email);
        verify(MockConfig.dashboardService).getStats(1L);
    }

    @Test
    void getDashboardStats_unauthenticated_return401() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users/dashboard/stats"))
                .andExpect(status().isUnauthorized());

        verify(MockConfig.userRepository, never()).findByEmail(anyString());
        verify(MockConfig.dashboardService, never()).getStats(anyLong());
    }

    @Test
    @WithMockUser(username = "another@test.com")
    void getDashboardStats_differentUser_success() throws Exception {
        // given
        String email = "another@test.com";
        User user = new User();
        user.setId(99L);
        user.setEmail(email);

        when(MockConfig.userRepository.findByEmail(email))
                .thenReturn(Optional.of(user));
        when(MockConfig.dashboardService.getStats(99L))
                .thenReturn(any(UserDashboardStatsDTO.class));

        // when & then
        mockMvc.perform(get("/api/users/dashboard/stats"))
                .andExpect(status().isOk());

        verify(MockConfig.userRepository).findByEmail(email);
        verify(MockConfig.dashboardService).getStats(99L);
    }

    // ========================= GET DASHBOARD STATS BY USER ID =========================
    @Test
    void getDashboardStatsByUserId_success() throws Exception {
        // given
        Long userId = 1L;

        when(MockConfig.dashboardService.getStats(userId))
                .thenReturn(any(UserDashboardStatsDTO.class));

        // when & then
        mockMvc.perform(get("/api/users/dashboard/stats-by-id")
                        .param("userId", "1"))
                .andExpect(status().isOk());

        verify(MockConfig.dashboardService).getStats(userId);
    }

    @Test
    void getDashboardStatsByUserId_differentUserId_success() throws Exception {
        // given
        Long userId = 50L;

        when(MockConfig.dashboardService.getStats(userId))
                .thenReturn(any(UserDashboardStatsDTO.class));

        // when & then
        mockMvc.perform(get("/api/users/dashboard/stats-by-id")
                        .param("userId", "50"))
                .andExpect(status().isOk());

        verify(MockConfig.dashboardService).getStats(userId);
    }

    @Test
    void getDashboardStatsByUserId_serviceThrowsException_return400() throws Exception {
        // given
        Long userId = 999L;

        when(MockConfig.dashboardService.getStats(userId))
                .thenThrow(new RuntimeException("User not found"));

        // when & then
        mockMvc.perform(get("/api/users/dashboard/stats-by-id")
                        .param("userId", "999"))
                .andExpect(status().isBadRequest());

        verify(MockConfig.dashboardService).getStats(userId);
    }

    @Test
    void getDashboardStatsByUserId_invalidUserId_return400() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users/dashboard/stats-by-id")
                        .param("userId", "invalid"))
                .andExpect(status().isBadRequest());

        verify(MockConfig.dashboardService, never()).getStats(anyLong());
    }

    @Test
    void getDashboardStatsByUserId_missingUserId_return400() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users/dashboard/stats-by-id"))
                .andExpect(status().isBadRequest());

        verify(MockConfig.dashboardService, never()).getStats(anyLong());
    }

    // ========================= MOCK CONFIG =========================
    static class MockConfig {

        static final DashboardService dashboardService = Mockito.mock(DashboardService.class);
        static final UserRepository userRepository = Mockito.mock(UserRepository.class);

        @Bean
        DashboardService dashboardService() {
            return dashboardService;
        }

        @Bean
        UserRepository userRepository() {
            return userRepository;
        }
    }
}