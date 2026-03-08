package com.be_ai_learning_platform.AI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenRouterConfig {

    @Bean
    public RestClient openRouterRestClient(OpenRouterProperties props) {
        String base = (props.baseUrl() == null || props.baseUrl().isBlank())
                ? "https://openrouter.ai/api/v1"
                : props.baseUrl().trim();

        return RestClient.builder()
                .baseUrl(base)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
