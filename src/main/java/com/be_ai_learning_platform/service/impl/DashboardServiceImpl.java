package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.UserDashboardStatsDTO;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.repository.ExamAttemptRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final ExamAttemptRepository examAttemptRepository;
    private final UserRepository userRepository;

    @Override
    public UserDashboardStatsDTO getStats(Long userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            long totalStudents = userRepository.countByRoleName("STUDENT");
            long completed = examAttemptRepository.countCompletedByUser(userId);
            Double avgScore = examAttemptRepository.getAvgScoreByUser(userId);
            Long totalSeconds = examAttemptRepository.sumDurationByUser(userId);

            // Thêm try-catch riêng cho phần Rank vì nó dễ lỗi SQL nhất
            Integer rank = 0;
            try {
                rank = examAttemptRepository.getRankAmongStudentsByUser(userId);
            } catch (Exception e) {
                System.err.println("Lỗi tính Rank: " + e.getMessage());
                rank = 0; // Trả về 0 nếu lỗi để Dashboard vẫn hiện các số khác
            }

            return UserDashboardStatsDTO.builder()
                    .greeting(generateGreeting(user.getFullName()))
                    .suggestion(generateSuggestion(completed, avgScore))
                    .completedLessons(completed)
                    .onlineTime(totalSeconds != null ? Math.round((totalSeconds / 3600.0) * 10.0) / 10.0 : 0.0)
                    .averageScore(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0)
                    .rank(rank != null ? rank : 0)
                    .totalStudents((int) totalStudents)
                    .build();
        } catch (Exception e) {
            log.error("Lỗi tổng quát Dashboard: ", e);
            throw new RuntimeException("Lỗi xử lý dữ liệu Dashboard: " + e.getMessage());
        }
    }

    // PHẢI CÓ CÁC PHƯƠNG THỨC NÀY BÊN TRONG CLASS
    private String generateGreeting(String fullName) {
        int hour = LocalTime.now().getHour();
        String name = (fullName != null && !fullName.isEmpty()) ? fullName : "Học viên";
        if (hour >= 5 && hour < 12) return "Chào buổi sáng, " + name;
        if (hour >= 12 && hour < 18) return "Chào buổi chiều, " + name;
        return "Chào buổi tối, " + name;
    }

    private String generateSuggestion(long completed, Double avgScore) {
        if (avgScore == null || avgScore == 0) return "Hãy bắt đầu bài thi đầu tiên để xem gợi ý!";
        if (avgScore < 5.0) return "Kết quả hơi thấp, bạn nên ôn tập kỹ hơn phần lý thuyết.";
        return "Phong độ rất tốt! Hãy tiếp tục phát huy để nâng cao thứ hạng.";
    }
}