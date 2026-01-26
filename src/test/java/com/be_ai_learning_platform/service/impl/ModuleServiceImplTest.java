package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.entity.LearningModule;
import com.be_ai_learning_platform.repository.ModuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModuleServiceImplTest {

    @Mock
    private ModuleRepository moduleRepository;

    @InjectMocks
    private ModuleServiceImpl moduleService;

    @Test
    void getAllModules_returnList() {
        // given
        LearningModule m1 = new LearningModule();
        m1.setId(1L);
        m1.setModuleName("Module A");

        LearningModule m2 = new LearningModule();
        m2.setId(2L);
        m2.setModuleName("Module B");

        when(moduleRepository.findAll())
                .thenReturn(List.of(m1, m2));

        // when
        List<LearningModule> result = moduleService.getAllModules();

        // then
        assertEquals(2, result.size());
        assertEquals("Module A", result.get(0).getModuleName());
        assertEquals("Module B", result.get(1).getModuleName());

        verify(moduleRepository, times(1)).findAll();
    }

    @Test
    void getAllModules_returnEmptyList() {
        // given
        when(moduleRepository.findAll())
                .thenReturn(List.of());

        // when
        List<LearningModule> result = moduleService.getAllModules();

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(moduleRepository).findAll();
    }
}
