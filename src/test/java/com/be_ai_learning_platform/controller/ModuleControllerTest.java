package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.entity.LearningModule;
import com.be_ai_learning_platform.repository.ModuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleControllerTest {

    @Mock
    private ModuleRepository moduleRepository;

    @InjectMocks
    private ModuleController moduleController;

    @Test
    @DisplayName("Nên lấy được danh sách tất cả các module")
    void getAll_Success() {
        // Given
        LearningModule module1 = new LearningModule();
        module1.setId(1L);
        // module1.setName("Module 1"); // Giả sử có field name

        LearningModule module2 = new LearningModule();
        module2.setId(2L);

        List<LearningModule> mockModules = Arrays.asList(module1, module2);

        when(moduleRepository.findAll()).thenReturn(mockModules);

        // When
        List<LearningModule> result = moduleController.getAll();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());

        // Kiểm tra xem repository có thực sự được gọi 1 lần không
        verify(moduleRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Nên trả về danh sách trống khi không có module nào")
    void getAll_EmptyList() {
        // Given
        when(moduleRepository.findAll()).thenReturn(List.of());

        // When
        List<LearningModule> result = moduleController.getAll();

        // Then
        assertNotNull(result);
        assertEquals(0, result.size());
        verify(moduleRepository, times(1)).findAll();
    }
}