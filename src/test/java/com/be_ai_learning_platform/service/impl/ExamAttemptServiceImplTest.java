package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.UserExamAttemptDTO;
import com.be_ai_learning_platform.entity.*;
import com.be_ai_learning_platform.entity.enums.ExamResult;
import com.be_ai_learning_platform.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamAttemptServiceImplTest {

    @Mock
    private ExamAttemptRepository examAttemptRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ExamRepository examRepository;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ExamAttemptServiceImpl examAttemptService;

    private User user;
    private Exam exam;
    private ExamAttempt attempt;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setFullName("Nguyen Van A");
        user.setEmail("a@gmail.com");

        exam = new Exam();
        exam.setId(10L);
        exam.setTitle("Java Basic Exam");
        exam.setPassScore(50);

        attempt = new ExamAttempt();
        attempt.setId(100L);
        attempt.setUser(user);
        attempt.setExam(exam);
        attempt.setStartTime(LocalDateTime.now().minusMinutes(30));
        attempt.setSubmitTime(LocalDateTime.now());
        attempt.setScore(80);
    }

    @Test
    @DisplayName("getMyAttempts - Trả về danh sách DTO khi user tồn tại và có bài làm")
    void getMyAttempts_ShouldReturnList_WhenUserExists() {
        // Given
        when(userRepository.existsById(1L)).thenReturn(true);
        when(examAttemptRepository.findByUserIdOrderBySubmitTimeDesc(1L)).thenReturn(List.of(attempt));

        // When
        List<UserExamAttemptDTO> result = examAttemptService.getMyAttempts(1L);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStudentName()).isEqualTo("Nguyen Van A");
        assertThat(result.get(0).getExamTitle()).isEqualTo("Java Basic Exam");
        verify(examAttemptRepository).findByUserIdOrderBySubmitTimeDesc(1L);
    }

    @Test
    @DisplayName("getMyAttempts - Trả về list rỗng khi user không tồn tại")
    void getMyAttempts_ShouldReturnEmpty_WhenUserNotFound() {
        when(userRepository.existsById(1L)).thenReturn(false);

        List<UserExamAttemptDTO> result = examAttemptService.getMyAttempts(1L);

        assertThat(result).isEmpty();
        verify(examAttemptRepository, never()).findByUserIdOrderBySubmitTimeDesc(any());
    }

    @Test
    @DisplayName("getExamStats - Tính toán thống kê chính xác")
    void getExamStats_ShouldReturnCorrectStats() {
        // Given
        when(examAttemptRepository.findByUserIdNative(1L)).thenReturn(List.of(attempt));

        // When
        Map<String, Object> stats = examAttemptService.getExamStats(1L);

        // Then
        assertThat(stats.get("totalTests")).isEqualTo(1);
        assertThat(stats.get("averageScore")).isEqualTo(80.0);
        assertThat(stats.get("passedTests")).isEqualTo(1L);
    }

    @Test
    @DisplayName("startExam - Lưu và trả về bản ghi attempt mới")
    void startExam_ShouldSaveAndReturnAttempt() {
        // Given
        when(examRepository.findById(10L)).thenReturn(Optional.of(exam));
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(examAttemptRepository.save(any(ExamAttempt.class))).thenReturn(attempt);

        // When
        ExamAttempt result = examAttemptService.startExam(1L, 10L);

        // Then
        assertThat(result).isNotNull();
        verify(examAttemptRepository).save(any(ExamAttempt.class));
    }

    @Test
    @DisplayName("submitExam - Cập nhật điểm và trạng thái PASSED khi điểm đủ")
    void submitExam_ShouldSetPassedStatus_WhenScoreIsHigh() throws Exception {
        // Given
        Object dummyAnswers = new Object();
        when(examAttemptRepository.findById(100L)).thenReturn(Optional.of(attempt));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(examAttemptRepository.save(any(ExamAttempt.class))).thenAnswer(i -> i.getArguments()[0]);

        // When
        ExamAttempt result = examAttemptService.submitExam(100L, dummyAnswers);

        // Then
        assertThat(result.getStatus()).isEqualTo(ExamResult.PASSED);
        assertThat(result.getScore()).isEqualTo(80);
        assertThat(result.getSubmitTime()).isNotNull();
    }

    @Test
    @DisplayName("submitExam - Ném ngoại lệ khi có lỗi Serialization")
    void submitExam_ShouldThrowException_WhenMappingFails() throws Exception {
        when(examAttemptRepository.findById(100L)).thenReturn(Optional.of(attempt));
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("JSON error"));

        assertThrows(RuntimeException.class, () -> {
            examAttemptService.submitExam(100L, new Object());
        });
    }

    @Test
    @DisplayName("getAllAttemptsForAdmin - Trả về danh sách cho Admin")
    void getAllAttemptsForAdmin_ShouldReturnList() {
        when(examAttemptRepository.findAllWithUserDetails()).thenReturn(List.of(attempt));

        List<UserExamAttemptDTO> result = examAttemptService.getAllAttemptsForAdmin();

        assertThat(result).hasSize(1);
        verify(examAttemptRepository).findAllWithUserDetails();
    }
}