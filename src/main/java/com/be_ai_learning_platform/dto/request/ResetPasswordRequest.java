package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank
    private String token; // Token lấy từ email
    @NotBlank @Size(min = 6)
    private String newPassword;
}