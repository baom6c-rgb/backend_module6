package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.EmailOtpSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailOtpSessionRepository extends JpaRepository<EmailOtpSession, String> {

    Optional<EmailOtpSession> findFirstByEmailOrderByCreatedAtDesc(String email);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
