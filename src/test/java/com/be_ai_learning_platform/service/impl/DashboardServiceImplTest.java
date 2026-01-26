package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.UserDashboardStatsDTO;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.repository.ExamAttemptRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private ExamAttemptRepository examAttemptRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Test
    void getStats_success() {
        // given
        User user = new User();
        user.setId(1L);
        user.setFullName("Bao");

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(userRepository.countByRoleName("STUDENT"))
                .thenReturn(100L);

        when(examAttemptRepository.countCompletedByUser(1L))
                .thenReturn(5L);

        when(examAttemptRepository.getAvgScoreByUser(1L))
                .thenReturn(7.5);

        when(examAttemptRepository.sumDurationByUser(1L))
                .thenReturn(7200L); // 2 giờ

        when(examAttemptRepository.getRankAmongStudentsByUser(1L))
                .thenReturn(10);

        // when
        UserDashboardStatsDTO result = dashboardService.getStats(1L);

        // then
        assertNotNull(result);
        assertEquals(5, result.getCompletedLessons());
        assertEquals(2.0, result.getOnlineTime());
        assertEquals(7.5, result.getAverageScore());
        assertEquals(10, result.getRank());
        assertEquals(100, result.getTotalStudents());
        assertTrue(result.getGreeting().contains("Bao"));

        verify(userRepository).findById(1L);
        verify(examAttemptRepository).countCompletedByUser(1L);
    }

    @Test
    void getStats_avgScoreNull_shouldFallback() {
        // given
        User user = new User();
        user.setId(1L);
        user.setFullName("Bao");

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(userRepository.countByRoleName("STUDENT"))
                .thenReturn(50L);

        when(examAttemptRepository.countCompletedByUser(1L))
                .thenReturn(0L);

        when(examAttemptRepository.getAvgScoreByUser(1L))
                .thenReturn(null);

        when(examAttemptRepository.sumDurationByUser(1L))
                .thenReturn(null);

        when(examAttemptRepository.getRankAmongStudentsByUser(1L))
                .thenReturn(1);

        // when
        UserDashboardStatsDTO result = dashboardService.getStats(1L);

        // then
        assertEquals(0.0, result.getAverageScore());
        assertEquals(0.0, result.getOnlineTime());
        assertEquals("Hãy bắt đầu bài thi đầu tiên để xem gợi ý!", result.getSuggestion());
    }

    @Test
    void getStats_rankQueryThrows_shouldReturnZero() {
        // given
        User user = new User();
        user.setId(1L);
        user.setFullName("Bao");

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(userRepository.countByRoleName("STUDENT"))
                .thenReturn(30L);

        when(examAttemptRepository.countCompletedByUser(1L))
                .thenReturn(3L);

        when(examAttemptRepository.getAvgScoreByUser(1L))
                .thenReturn(6.0);

        when(examAttemptRepository.sumDurationByUser(1L))
                .thenReturn(3600L);

        when(examAttemptRepository.getRankAmongStudentsByUser(1L))
                .thenThrow(new RuntimeException("SQL error"));

        // when
        UserDashboardStatsDTO result = dashboardService.getStats(1L);

        // then
        assertEquals(0, result.getRank());
    }

    @Test
    void getStats_userNotFound_throwException() {
        // given
        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        // when & then
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> dashboardService.getStats(99L));

        assertTrue(ex.getMessage().contains("Dashboard"));
    }
}
