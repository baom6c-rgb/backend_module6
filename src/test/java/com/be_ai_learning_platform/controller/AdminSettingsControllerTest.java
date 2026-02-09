package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.UpdateSystemSettingsRequest;
import com.be_ai_learning_platform.dto.response.SystemSettingsResponse;
import com.be_ai_learning_platform.service.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminSettingsController.class)
@Import(AdminSettingsControllerTest.MockConfig.class)
class AdminSettingsControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    AdminSettingsControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    // ========================= GET SETTINGS =========================
    @Test
    void get_success() throws Exception {
        // given
        when(MockConfig.systemSettingsService.get())
                .thenReturn(any(SystemSettingsResponse.class));

        // when & then
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk());

        verify(MockConfig.systemSettingsService).get();
    }

    @Test
    void get_serviceThrowsException_returnError() throws Exception {
        // given
        when(MockConfig.systemSettingsService.get())
                .thenThrow(new RuntimeException("Settings not found"));

        // when & then
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().is5xxServerError());

        verify(MockConfig.systemSettingsService).get();
    }

    // ========================= UPDATE SETTINGS =========================
    @Test
    void update_success() throws Exception {
        // given
        String requestJson = """
            {
                "maxLoginAttempts": 5,
                "sessionTimeout": 3600,
                "enableRegistration": true
            }
            """;

        when(MockConfig.systemSettingsService.update(any(UpdateSystemSettingsRequest.class)))
                .thenReturn(any(SystemSettingsResponse.class));

        // when & then
        mockMvc.perform(put("/api/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(MockConfig.systemSettingsService).update(any(UpdateSystemSettingsRequest.class));
    }

    @Test
    void update_withDifferentSettings_success() throws Exception {
        // given
        String requestJson = """
            {
                "maxLoginAttempts": 3,
                "sessionTimeout": 7200,
                "enableRegistration": false
            }
            """;

        when(MockConfig.systemSettingsService.update(any(UpdateSystemSettingsRequest.class)))
                .thenReturn(any(SystemSettingsResponse.class));

        // when & then
        mockMvc.perform(put("/api/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(MockConfig.systemSettingsService).update(any(UpdateSystemSettingsRequest.class));
    }

    @Test
    void update_serviceThrowsException_returnError() throws Exception {
        // given
        String requestJson = """
            {
                "maxLoginAttempts": 5,
                "sessionTimeout": 3600,
                "enableRegistration": true
            }
            """;

        when(MockConfig.systemSettingsService.update(any(UpdateSystemSettingsRequest.class)))
                .thenThrow(new RuntimeException("Update failed"));

        // when & then
        mockMvc.perform(put("/api/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().is5xxServerError());

        verify(MockConfig.systemSettingsService).update(any(UpdateSystemSettingsRequest.class));
    }

    // ========================= MOCK CONFIG =========================
    static class MockConfig {

        static final SystemSettingsService systemSettingsService = Mockito.mock(SystemSettingsService.class);

        @Bean
        SystemSettingsService systemSettingsService() {
            return systemSettingsService;
        }
    }
}