package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUpdateUserRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @Email(message = "Invalid email")
    @NotBlank(message = "Email is required")
    private String email;

    @NotNull(message = "RoleId is required")
    private Long roleId;

    @NotNull(message = "ClassId is required")
    private Long classId;

    @NotNull(message = "ModuleId is required")
    private Long moduleId;
}
