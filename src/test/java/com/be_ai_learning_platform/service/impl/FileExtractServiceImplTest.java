package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.FileType;
import com.be_ai_learning_platform.entity.enums.MaterialStatus;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileExtractServiceImplTest {

    @Mock
    private LearningMaterialRepository materialRepository;

    @InjectMocks
    private FileExtractServiceImpl fileExtractService;

    @Test
    void extractAndSave_txt_success() throws IOException {
        // given
        String content = "Hello AI Learning Platform";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes()
        );

        User user = new User();
        user.setId(1L);

        // when
        Long result = fileExtractService.extractAndSave(file, user);

        // then
        ArgumentCaptor<LearningMaterial> captor =
                ArgumentCaptor.forClass(LearningMaterial.class);

        verify(materialRepository).save(captor.capture());

        LearningMaterial saved = captor.getValue();

        assertEquals("test.txt", saved.getFileName());
        assertEquals(FileType.TXT, saved.getFileType());
        assertEquals(MaterialStatus.EXTRACTED, saved.getStatus());
        assertEquals(content, saved.getExtractedText());
        assertEquals(user, saved.getUser());
    }

    @Test
    void extractAndSave_fileNull_throwException() {
        // when & then
        assertThrows(IllegalArgumentException.class,
                () -> fileExtractService.extractAndSave(null, new User()));
    }

    @Test
    void extractAndSave_fileEmpty_throwException() {
        // given
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "a.txt", "text/plain", new byte[0]
        );

        // when & then
        assertThrows(IllegalArgumentException.class,
                () -> fileExtractService.extractAndSave(emptyFile, new User()));
    }

    @Test
    void extractAndSave_unsupportedFile_throwException() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.exe",
                "application/octet-stream",
                "abc".getBytes()
        );

        // when & then
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> fileExtractService.extractAndSave(file, new User())
        );

        assertTrue(ex.getMessage().contains("Định dạng file"));
    }
}
