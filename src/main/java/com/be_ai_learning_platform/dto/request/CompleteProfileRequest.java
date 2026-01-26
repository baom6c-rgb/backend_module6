package com.be_ai_learning_platform.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompleteProfileRequest {

    @NotBlank
    private String email;

    @NotBlank
    private String fullName;

    @NotNull
    private Long classId;

    @NotNull
    private Long moduleId;
}
