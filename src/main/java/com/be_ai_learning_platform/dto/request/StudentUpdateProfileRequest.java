package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudentUpdateProfileRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    // ✅ Phone: validate nhẹ
    @Pattern(
            regexp = "^[0-9+()\\- ]{8,20}$",
            message = "Invalid phone number"
    )
    private String phoneNumber;

    // ✅ Address: chỉ giới hạn độ dài
    @Size(max = 255, message = "Address is too long")
    private String address;
}
