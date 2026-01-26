package com.be_ai_learning_platform.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class GeminiConfig {

    @Bean
    public RestClient geminiRestClient(GeminiProperties props) {
        // props đã @Validated, nhưng check nhẹ cũng ok
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                // ✅ Gemini uses x-goog-api-key
                .defaultHeader("x-goog-api-key", props.apiKey())
                .build();
    }
}
