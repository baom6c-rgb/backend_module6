package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.entity.ClassEntity;
import com.be_ai_learning_platform.repository.ClassRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassServiceImplTest {

    @Mock
    private ClassRepository classRepository;

    @InjectMocks
    private ClassServiceImpl classService;

    @Test
    void getAllClasses_returnList() {
        // given
        ClassEntity c1 = new ClassEntity();
        c1.setId(1L);
        c1.setClassName("Class A");

        ClassEntity c2 = new ClassEntity();
        c2.setId(2L);
        c2.setClassName("Class B");

        when(classRepository.findAll())
                .thenReturn(List.of(c1, c2));

        // when
        List<ClassEntity> result = classService.getAllClasses();

        // then
        assertEquals(2, result.size());
        assertEquals("Class A", result.get(0).getClassName());
        assertEquals("Class B", result.get(1).getClassName());

        verify(classRepository, times(1)).findAll();
    }

    @Test
    void getAllClasses_returnEmptyList() {
        // given
        when(classRepository.findAll())
                .thenReturn(List.of());

        // when
        List<ClassEntity> result = classService.getAllClasses();

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(classRepository).findAll();
    }
}
