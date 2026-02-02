package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Long> {
}
