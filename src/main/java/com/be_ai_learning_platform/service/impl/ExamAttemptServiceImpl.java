package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.UserExamAttemptDTO;
import com.be_ai_learning_platform.entity.*;
import com.be_ai_learning_platform.entity.enums.ExamResult;
import com.be_ai_learning_platform.repository.*;
import com.be_ai_learning_platform.service.ExamAttemptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamAttemptServiceImpl implements ExamAttemptService {

    private final ExamAttemptRepository examAttemptRepository;
    private final AnswerRepository answerRepository;
    private final ExamRepository examRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<UserExamAttemptDTO> getMyAttempts(Long userId) {
        log.info("=== DEBUG: Getting exam attempts for userId: {}", userId);

        // Kiểm tra user có tồn tại không
        boolean userExists = userRepository.existsById(userId);
        log.info("=== DEBUG: User exists: {}", userExists);

        if (!userExists) {
            log.warn("=== DEBUG: User not found with id: {}", userId);
            return List.of();
        }

        // Lấy tất cả attempts của user
        List<ExamAttempt> attempts = examAttemptRepository.findByUserIdOrderBySubmitTimeDesc(userId);
        log.info("=== DEBUG: Found {} exam attempts", attempts.size());

        if (attempts.isEmpty()) {
            log.warn("=== DEBUG: No exam attempts found for user: {}", userId);

            // Kiểm tra có attempt nào trong database không
            long totalAttempts = examAttemptRepository.count();
            log.info("=== DEBUG: Total attempts in database: {}", totalAttempts);

            if (totalAttempts > 0) {
                // Có attempts nhưng không phải của user này
                log.warn("=== DEBUG: There are {} attempts in DB but none belong to userId: {}",
                        totalAttempts, userId);
            } else {
                log.warn("=== DEBUG: No exam attempts in database at all!");
            }

            return List.of();
        }

        // Convert sang DTO
        List<UserExamAttemptDTO> dtos = attempts.stream()
                .map(attempt -> {
                    log.debug("=== DEBUG: Processing attempt id: {}", attempt.getId());
                    return convertToDTO(attempt);
                })
                .collect(Collectors.toList());

        log.info("=== DEBUG: Successfully converted {} attempts to DTOs", dtos.size());
        return dtos;
    }

    @Override
    public Map<String, Object> getExamStats(Long userId) {
        // Sử dụng hàm Native để đồng bộ dữ liệu
        List<ExamAttempt> attempts = examAttemptRepository.findByUserIdNative(userId);

        double avgScore = attempts.stream()
                .filter(a -> a.getScore() != null)
                .mapToInt(ExamAttempt::getScore)
                .average()
                .orElse(0.0);

        long passedCount = attempts.stream()
                .filter(a -> a.getScore() != null && a.getExam() != null
                        && a.getScore() >= a.getExam().getPassScore())
                .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTests", attempts.size());
        stats.put("averageScore", Math.round(avgScore * 10.0) / 10.0);
        stats.put("passedTests", passedCount);
        stats.put("rank", attempts.size() > 0 ? 12 : 0); // Ví dụ hạng 12

        return stats;
    }

    // Các hàm getAttemptById, startExam, submitExam giữ nguyên như bản trước...
    @Override
    public ExamAttempt getAttemptById(Long attemptId) {
        return examAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bài làm ID: " + attemptId));
    }

    @Override
    @Transactional
    public ExamAttempt startExam(Long userId, Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Đề thi không tồn tại"));
        ExamAttempt attempt = new ExamAttempt();
        attempt.setUser(userRepository.getReferenceById(userId));
        attempt.setExam(exam);
        attempt.setStartTime(LocalDateTime.now());
        return examAttemptRepository.save(attempt);
    }

    @Override
    @Transactional
    public ExamAttempt submitExam(Long attemptId, Object answers) {
        ExamAttempt attempt = getAttemptById(attemptId);
        try {
            attempt.setAnswersJson(objectMapper.writeValueAsString(answers));
            // Logic tính điểm thực tế nên được thêm ở đây
            attempt.setScore(80);
            attempt.setSubmitTime(LocalDateTime.now());
            if (attempt.getScore() >= attempt.getExam().getPassScore()) {
                attempt.setStatus(ExamResult.PASSED);
            } else {
                attempt.setStatus(ExamResult.FAILED);
            }
            return examAttemptRepository.save(attempt);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi nộp bài: " + e.getMessage());
        }
    }

    private UserExamAttemptDTO convertToDTO(ExamAttempt attempt) {
        Exam exam = attempt.getExam();

        // Lấy thông tin module và class từ ExamAttempt (không phải từ Exam)
        String moduleName = "N/A";
        String className = "N/A";

        try {
            // Module được lưu trực tiếp trong ExamAttempt
            if (attempt.getLearningModule() != null) {
                moduleName = attempt.getLearningModule().getModuleName();
            }
        } catch (Exception e) {
            log.warn("Cannot get module name for attempt: {}", attempt.getId(), e);
        }

        try {
            // Class được lưu trực tiếp trong ExamAttempt
            if (attempt.getClassroom() != null) {
                className = attempt.getClassroom().getClassName();
            }
        } catch (Exception e) {
            log.warn("Cannot get class name for attempt: {}", attempt.getId(), e);
        }

        // Tính thời gian làm bài (phút)
        Integer duration = 0;
        if (attempt.getStartTime() != null && attempt.getSubmitTime() != null) {
            duration = (int) ChronoUnit.MINUTES.between(
                    attempt.getStartTime(),
                    attempt.getSubmitTime()
            );
        }

        // Lấy số câu hỏi - Exam không có relationship với Questions
        // Cần query riêng hoặc lưu trong exam
        Integer totalQuestions = 0;

        // Tính số câu đúng từ answersJson nếu có
        Integer correctAnswers = 0;
        // TODO: Parse answersJson và tính số câu đúng

        // Tên bài thi - lấy từ ExamType hoặc tạo tên mặc định
        String examName = "Bài thi";
        try {
            if (exam.getType() != null) {
                examName = "Bài thi " + exam.getType().name();
            }
        } catch (Exception e) {
            log.warn("Cannot get exam type for exam: {}", exam.getId(), e);
        }

        // Build DTO
        return UserExamAttemptDTO.builder()
                .id(attempt.getId())
                .name(examName)
                .module(moduleName)
                .className(className)
                .date(attempt.getSubmitTime()) // Ngày submit
                .score(attempt.getScore())
                .totalScore(100) // Hoặc tính từ số câu hỏi * điểm mỗi câu
                .duration(duration)
                .questions(totalQuestions)
                .correctAnswers(correctAnswers)
                .build();
    }
}