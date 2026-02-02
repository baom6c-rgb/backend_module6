package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findTopByUser_IdAndMaterial_IdOrderByCreatedAtDesc(Long userId, Long materialId);
}
