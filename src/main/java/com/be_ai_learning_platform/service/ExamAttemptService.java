package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.UserExamAttemptDTO;
import com.be_ai_learning_platform.entity.ExamAttempt;
import java.util.List;
import java.util.Map;

public interface ExamAttemptService {
    List<UserExamAttemptDTO> getMyAttempts(Long userId);

    Map<String, Object> getExamStats(Long userId);

    ExamAttempt getAttemptById(Long attemptId);

    ExamAttempt startExam(Long userId, Long examId);

    ExamAttempt submitExam(Long attemptId, Object answers);

    List<UserExamAttemptDTO> getAllAttemptsForAdmin();
}