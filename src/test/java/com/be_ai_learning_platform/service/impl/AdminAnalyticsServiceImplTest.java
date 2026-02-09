package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.AdminAnalyticsFilterRequest;
import com.be_ai_learning_platform.dto.response.AdminAnalyticsOverviewResponse;
import com.be_ai_learning_platform.dto.response.AtRiskStudentResponse;
import com.be_ai_learning_platform.repository.AdminAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceImplTest {

    @Mock
    private AdminAnalyticsRepository analyticsRepository;

    @InjectMocks
    private AdminAnalyticsServiceImpl adminAnalyticsService;

    private AdminAnalyticsFilterRequest filterRequest;

    @BeforeEach
    void setUp() {
        filterRequest = new AdminAnalyticsFilterRequest();
        filterRequest.setFrom("2024-01-01T00:00:00");
        filterRequest.setTo("2024-12-31T23:59:59");
        filterRequest.setClassId(1L);
    }

    @Test
    @DisplayName("getOverview - Trả về dữ liệu rỗng khi Repository trả về null")
    void getOverview_ReturnsEmptyWhenRepoIsNull() {
        // GIVEN
        when(analyticsRepository.getOverview(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
        when(analyticsRepository.getStudentAgg(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(analyticsRepository.getTimeSeries(any(), any(), any(), any()))
                .thenReturn(List.of());

        // WHEN
        AdminAnalyticsOverviewResponse res = adminAnalyticsService.getOverview(filterRequest);

        // THEN
        assertThat(res.getTotalAttempts()).isZero();
        assertThat(res.getTotalStudents()).isZero();
        assertThat(res.getAvgScore()).isZero();
        assertThat(res.getStudents()).isEmpty();
    }

    @Test
    @DisplayName("getOverview - Tính toán tỷ lệ Pass/Fail và phân loại học viên chính xác")
    void getOverview_CalculatesMetricsCorrectly() {
        // GIVEN
        AdminAnalyticsRepository.OverviewRow mockOv = mock(AdminAnalyticsRepository.OverviewRow.class);
        when(mockOv.getTotalAttempts()).thenReturn(10L);
        when(mockOv.getTotalStudents()).thenReturn(2L);
        when(mockOv.getAvgScore()).thenReturn(70.0);
        when(mockOv.getFailedAttempts()).thenReturn(3L);

        when(analyticsRepository.getOverview(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockOv);

        // Giả lập 2 học viên: 1 Tốt, 1 Yếu
        AdminAnalyticsRepository.StudentAggRow student1 = mock(AdminAnalyticsRepository.StudentAggRow.class);
        when(student1.getUserId()).thenReturn(101L);
        when(student1.getAttemptsCount()).thenReturn(5L);
        when(student1.getAvgScore()).thenReturn(85.0); // TOT
        when(student1.getFailedCount()).thenReturn(0L);

        AdminAnalyticsRepository.StudentAggRow student2 = mock(AdminAnalyticsRepository.StudentAggRow.class);
        when(student2.getUserId()).thenReturn(102L);
        when(student2.getAttemptsCount()).thenReturn(5L);
        when(student2.getAvgScore()).thenReturn(40.0); // YEU
        when(student2.getFailedCount()).thenReturn(4L);

        when(analyticsRepository.getStudentAgg(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(student1, student2));

        // WHEN
        AdminAnalyticsOverviewResponse res = adminAnalyticsService.getOverview(filterRequest);

        // THEN
        assertThat(res.getFailRate()).isEqualTo(0.3); // 3/10
        assertThat(res.getPassRate()).isEqualTo(0.7); // 1 - 0.3

        // Kiểm tra danh sách học viên rủi ro
        assertThat(res.getAtRiskStudents()).hasSize(1);
        assertThat(res.getAtRiskStudents().get(0).getRiskLevel()).isEqualTo("YEU"); // < 50
        assertThat(res.getAtRiskStudents().get(0).getReasons()).contains("Điểm dưới 50 (Yếu)");
    }

    @Test
    @DisplayName("Phân loại level - Kiểm tra biên điểm số")
    void classifyLevel_EdgeCases() {
        // Đây là phương thức private nhưng ta có thể test gián tiếp qua getOverview
        // hoặc dùng Reflection (nhưng ở đây logic buildStudents sử dụng nó trực tiếp)

        AdminAnalyticsRepository.StudentAggRow s1 = createMockStudent(1L, 80.0); // Biên TOT
        AdminAnalyticsRepository.StudentAggRow s2 = createMockStudent(2L, 50.0); // Biên TRUNG_BINH
        AdminAnalyticsRepository.StudentAggRow s3 = createMockStudent(3L, 49.9); // Biên YEU

        when(analyticsRepository.getStudentAgg(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(s1, s2, s3));

        AdminAnalyticsOverviewResponse res = adminAnalyticsService.getOverview(filterRequest);

        assertThat(res.getStudents()).extracting(AtRiskStudentResponse::getRiskLevel)
                .containsExactly("TOT", "TRUNG_BINH", "YEU");
    }

    @Test
    @DisplayName("buildTopPassed - Sắp xếp đúng thứ tự ưu tiên")
    void buildTopPassed_SortingOrder() {
        // GIVEN: 2 học viên tốt, nhưng học viên A có passRate cao hơn
        AdminAnalyticsRepository.StudentAggRow s1 = createMockStudent(1L, 90.0, 5, 0); // PassRate 1.0
        AdminAnalyticsRepository.StudentAggRow s2 = createMockStudent(2L, 95.0, 5, 1); // PassRate 0.8

        when(analyticsRepository.getStudentAgg(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(s1, s2));

        // WHEN
        AdminAnalyticsOverviewResponse res = adminAnalyticsService.getOverview(filterRequest);

        // THEN: s1 phải đứng đầu do PassRate cao hơn
        assertThat(res.getTopPassedStudents().get(0).getUserId()).isEqualTo(1L);
    }

    // Helper method để tạo mock nhanh
    private AdminAnalyticsRepository.StudentAggRow createMockStudent(Long id, Double avgScore) {
        return createMockStudent(id, avgScore, 5, 0);
    }

    private AdminAnalyticsRepository.StudentAggRow createMockStudent(Long id, Double avgScore, long attempts, long failed) {
        AdminAnalyticsRepository.StudentAggRow m = mock(AdminAnalyticsRepository.StudentAggRow.class);
        when(m.getUserId()).thenReturn(id);
        when(m.getAvgScore()).thenReturn(avgScore);
        when(m.getAttemptsCount()).thenReturn(attempts);
        when(m.getFailedCount()).thenReturn(failed);
        return m;
    }
}