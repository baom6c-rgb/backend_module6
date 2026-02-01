package com.be_ai_learning_platform.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache cho practice session (V2):
 * - Generate câu hỏi xong sẽ lưu trong cache
 * - Chỉ khi SUBMIT mới lưu DB (tránh rác)
 * - TTL nên >= duration làm bài + buffer
 */
@Configuration
public class PracticeSessionCacheConfig {

    @Bean
    public Cache<String, Object> practiceSessionCache(
            @Value("${ai.practice.session-ttl-minutes:180}") long ttlMinutes,
            @Value("${ai.practice.session-cache.max-size:5000}") long maxSize
    ) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(Math.max(5, ttlMinutes)))
                .maximumSize(Math.max(100, maxSize))
                .build();
    }
}
