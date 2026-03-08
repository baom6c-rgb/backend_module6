package com.be_ai_learning_platform.AI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.openrouter")
public record OpenRouterProperties(
        String baseUrl,
        String defaultModel,
        String httpReferer,
        String appTitle
) { }
