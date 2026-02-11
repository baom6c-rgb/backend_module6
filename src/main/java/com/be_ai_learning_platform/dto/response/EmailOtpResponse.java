package com.be_ai_learning_platform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EmailOtpResponse {
    private String otpSessionId;
    private int cooldownSeconds;
    private int expiresInSeconds;
}
