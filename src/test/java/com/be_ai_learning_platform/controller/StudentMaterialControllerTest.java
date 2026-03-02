package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.CreateTextMaterialRequest;
import com.be_ai_learning_platform.entity.LearningMaterial;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.repository.LearningMaterialRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.FileExtractService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentMaterialControllerTest {

    @Mock
    private FileExtractService fileExtractService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LearningMaterialRepository learningMaterialRepository;

    @InjectMocks
    private StudentMaterialController studentMaterialController;

    private final String TEST_EMAIL = "test@student.com";
    private User mockUser;

    @BeforeEach
    void setUp() {
        
        mockUser = new User();
        mockUser.setEmail(TEST_EMAIL);
    }

    // --- TEST UPLOAD FILE ---

    @Test
    @DisplayName("Upload file thành công")
    void uploadFile_Success() throws Exception {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(fileExtractService.extractAndSave(mockFile, mockUser)).thenReturn(1L);

        ResponseEntity<?> response = studentMaterialController.upload(mockFile, TEST_EMAIL);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(1L, body.get("materialId"));
        verify(fileExtractService).extractAndSave(mockFile, mockUser);
    }

    @Test
    @DisplayName("Upload file thất bại khi không tìm thấy email")
    void uploadFile_Unauthorized() {
        ResponseEntity<?> response = studentMaterialController.upload(mock(MultipartFile.class), null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // --- TEST CREATE FROM TEXT ---

    @Test
    @DisplayName("Tạo học liệu từ text thành công")
    void createFromText_Success() {
        // Given: Text có độ dài hợp lệ (200 - 20000 chars)
        String validText = "a".repeat(300);
        CreateTextMaterialRequest req = new CreateTextMaterialRequest();
        req.setRawText(validText);
        req.setTitle("Test Title");

        LearningMaterial savedMaterial = new LearningMaterial();
        savedMaterial.setId(99L);

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(learningMaterialRepository.save(any(LearningMaterial.class))).thenReturn(savedMaterial);

        // When
        ResponseEntity<?> response = studentMaterialController.createFromText(req, TEST_EMAIL);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(99L, body.get("materialId"));
    }

    @Test
    @DisplayName("Tạo học liệu thất bại do text quá ngắn")
    void createFromText_TooShort() {
        CreateTextMaterialRequest req = new CreateTextMaterialRequest();
        req.setRawText("Short text"); // < 200 chars

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));

        ResponseEntity<?> response = studentMaterialController.createFromText(req, TEST_EMAIL);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("Nội dung quá ngắn"));
    }

    // --- TEST GET EXTRACTED TEXT ---

    @Test
    @DisplayName("Lấy nội dung văn bản thành công")
    void getExtractedText_Success() {
        Long materialId = 1L;
        LearningMaterial material = new LearningMaterial();
        material.setExtractedText("Nội dung đã trích xuất");

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(learningMaterialRepository.findByIdAndUser(materialId, mockUser)).thenReturn(Optional.of(material));

        ResponseEntity<?> response = studentMaterialController.getExtractedText(materialId, TEST_EMAIL);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Nội dung đã trích xuất", response.getBody());
    }

    @Test
    @DisplayName("Lấy nội dung thất bại khi tài liệu không tồn tại")
    void getExtractedText_NotFound() {
        Long materialId = 1L;
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(mockUser));
        when(learningMaterialRepository.findByIdAndUser(materialId, mockUser)).thenReturn(Optional.empty());

        ResponseEntity<?> response = studentMaterialController.getExtractedText(materialId, TEST_EMAIL);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("Không tìm thấy tài liệu"));
    }
}