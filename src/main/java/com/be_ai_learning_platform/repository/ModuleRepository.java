package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.LearningModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModuleRepository extends JpaRepository<LearningModule, Long> {
}
