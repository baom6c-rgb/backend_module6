package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.entity.ClassEntity;
import com.be_ai_learning_platform.service.ClassService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@WebMvcTest(ClassController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ClassControllerTest.MockConfig.class)
class ClassControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClassService classService;

    // ========================= GET ALL CLASSES =========================
    @Test
    void getAll_success() throws Exception {
        // given
        ClassEntity class1 = new ClassEntity();
        ClassEntity class2 = new ClassEntity();

        when(classService.getAllClasses())
                .thenReturn(Arrays.asList(class1, class2));

        // when & then
        mockMvc.perform(get("/api/classes"))
                .andExpect(status().isOk());

        verify(classService).getAllClasses();
    }

    @Test
    void getAll_emptyList_success() throws Exception {
        // given
        when(classService.getAllClasses())
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(classService).getAllClasses();
    }

    @Test
    void getAll_serviceThrowsException_returnError() throws Exception {
        // given
        when(classService.getAllClasses())
                .thenThrow(new RuntimeException("Database error"));

        // when & then
        mockMvc.perform(get("/api/classes"))
                .andExpect(status().is5xxServerError());

        verify(classService).getAllClasses();
    }

    // ========================= MOCK CONFIG =========================

    @TestConfiguration
    static class MockConfig {
        @Bean
        ClassService classService() {
            return Mockito.mock(ClassService.class);
        }
    }

}
