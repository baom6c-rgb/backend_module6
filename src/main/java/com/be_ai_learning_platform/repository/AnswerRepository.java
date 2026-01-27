package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    long countByExamAttemptId(Long attemptId);
    long countByExamAttemptIdAndIsCorrectTrue(Long attemptId);
}