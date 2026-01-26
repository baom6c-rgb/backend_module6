package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<Exam, Long> {
}
