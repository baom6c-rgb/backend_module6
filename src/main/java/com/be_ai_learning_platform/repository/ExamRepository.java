package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
