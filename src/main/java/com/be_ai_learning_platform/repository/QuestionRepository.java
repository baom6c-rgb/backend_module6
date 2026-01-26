package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
}
