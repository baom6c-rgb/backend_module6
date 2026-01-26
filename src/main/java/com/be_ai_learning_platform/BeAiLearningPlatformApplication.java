package com.be_ai_learning_platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication(scanBasePackages = "com.be_ai_learning_platform")
public class BeAiLearningPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(BeAiLearningPlatformApplication.class, args);
    }

}
