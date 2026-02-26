package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.UpdateSystemSettingsRequest;
import com.be_ai_learning_platform.dto.response.SystemSettingsResponse;
import com.be_ai_learning_platform.service.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@WebMvcTest(AdminSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminSettingsControllerTest.MockConfig.class)
class AdminSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SystemSettingsService systemSettingsService;

    // ========================= GET SETTINGS =========================
    @Test
    void get_success() throws Exception {
        // given
        when(systemSettingsService.get())
                .thenReturn(any(SystemSettingsResponse.class));

        // when & then
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk());

        verify(systemSettingsService).get();
    }

    @Test
    void get_serviceThrowsException_returnError() throws Exception {
        // given
        when(systemSettingsService.get())
                .thenThrow(new RuntimeException("Settings not found"));

        // when & then
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().is5xxServerError());

        verify(systemSettingsService).get();
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

        when(systemSettingsService.update(any(UpdateSystemSettingsRequest.class)))
                .thenReturn(any(SystemSettingsResponse.class));

        // when & then
        mockMvc.perform(put("/api/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(systemSettingsService).update(any(UpdateSystemSettingsRequest.class));
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

        when(systemSettingsService.update(any(UpdateSystemSettingsRequest.class)))
                .thenReturn(any(SystemSettingsResponse.class));

        // when & then
        mockMvc.perform(put("/api/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(systemSettingsService).update(any(UpdateSystemSettingsRequest.class));
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

        when(systemSettingsService.update(any(UpdateSystemSettingsRequest.class)))
                .thenThrow(new RuntimeException("Update failed"));

        // when & then
        mockMvc.perform(put("/api/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().is5xxServerError());

        verify(systemSettingsService).update(any(UpdateSystemSettingsRequest.class));
    }

    // ========================= MOCK CONFIG =========================

    @TestConfiguration
    static class MockConfig {
        @Bean
        SystemSettingsService systemSettingsService() {
            return Mockito.mock(SystemSettingsService.class);
        }
    }

}
