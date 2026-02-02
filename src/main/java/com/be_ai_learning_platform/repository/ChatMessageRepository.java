package com.be_ai_learning_platform.repository;

import com.be_ai_learning_platform.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChatSession_IdOrderByCreatedAtAsc(Long sessionId);
}
