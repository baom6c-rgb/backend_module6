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

        boolean userExists = userRepository.existsById(userId);
        log.info("=== DEBUG: User exists: {}", userExists);

        if (!userExists) {
            log.warn("=== DEBUG: User not found with id: {}", userId);
            return List.of();
        }

        List<ExamAttempt> attempts = examAttemptRepository.findByUserIdOrderBySubmitTimeDesc(userId);
        log.info("=== DEBUG: Found {} exam attempts", attempts.size());

        if (attempts.isEmpty()) {
            log.warn("=== DEBUG: No exam attempts found for user: {}", userId);

            long totalAttempts = examAttemptRepository.count();
            log.info("=== DEBUG: Total attempts in database: {}", totalAttempts);

            if (totalAttempts > 0) {
                log.warn("=== DEBUG: There are {} attempts in DB but none belong to userId: {}",
                        totalAttempts, userId);
            } else {
                log.warn("=== DEBUG: No exam attempts in database at all!");
            }

            return List.of();
        }

        return attempts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getExamStats(Long userId) {
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
        stats.put("rank", attempts.size() > 0 ? 12 : 0);

        return stats;
    }

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

    @Override
    @Transactional(readOnly = true)
    public List<UserExamAttemptDTO> getAllAttemptsForAdmin() {
        List<ExamAttempt> allAttempts = examAttemptRepository.findAllWithUserDetails();
        return allAttempts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private UserExamAttemptDTO convertToDTO(ExamAttempt attempt) {
        Exam exam = attempt.getExam();
        User user = attempt.getUser();

        // ===== module/class từ ExamAttempt =====
        String moduleName = "N/A";
        String className = "N/A";

        try {
            if (attempt.getLearningModule() != null) {
                moduleName = attempt.getLearningModule().getModuleName();
            }
        } catch (Exception e) {
            log.warn("Cannot get module name for attempt: {}", attempt.getId(), e);
        }

        try {
            if (attempt.getClassroom() != null) {
                className = attempt.getClassroom().getClassName();
            }
        } catch (Exception e) {
            log.warn("Cannot get class name for attempt: {}", attempt.getId(), e);
        }

        // ===== duration (phút) =====
        Integer duration = 0;
        if (attempt.getStartTime() != null && attempt.getSubmitTime() != null) {
            duration = (int) ChronoUnit.MINUTES.between(attempt.getStartTime(), attempt.getSubmitTime());
        }

        Integer totalQuestions = 0;
        Integer correctAnswers = 0;

        // ✅ FIX: lấy AI title từ Exam.title
        String aiTitle = resolveExamTitle(exam);
        String fallbackName = resolveFallbackExamName(exam);

        // name + examTitle đều ưu tiên AI title
        String finalTitle = (aiTitle != null && !aiTitle.isBlank()) ? aiTitle : fallbackName;

        return UserExamAttemptDTO.builder()
                .id(attempt.getId())
                .studentName(user != null ? user.getFullName() : "N/A")
                .studentEmail(user != null ? user.getEmail() : "N/A")

                // ✅ backward compatible (FE đang dùng name)
                .name(finalTitle)

                // ✅ field mới rõ nghĩa
                .examTitle(finalTitle)

                .module(moduleName)
                .className(className)

                // giữ như bạn đang trả: submitTime
                .date(attempt.getSubmitTime())

                .score(attempt.getScore())
                .totalScore(100) // giữ logic cũ của bạn
                .duration(duration)
                .questions(totalQuestions)
                .correctAnswers(correctAnswers)
                .build();
    }

    private String resolveExamTitle(Exam exam) {
        if (exam == null) return null;
        try {
            String t = exam.getTitle();
            return (t == null) ? null : t.trim();
        } catch (Exception e) {
            log.warn("Cannot get exam title for exam", e);
            return null;
        }
    }

    private String resolveFallbackExamName(Exam exam) {
        if (exam == null) return "Bài thi";
        try {
            if (exam.getType() != null) {
                return "Bài thi " + exam.getType().name();
            }
        } catch (Exception e) {
            log.warn("Cannot get exam type for fallback name", e);
        }
        return "Bài thi";
    }
}
