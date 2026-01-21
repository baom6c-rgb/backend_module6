package com.be_ai_learning_platform.dto;

import lombok.Data;

@Data
public class UserUpdateDTO {
    private String fullName;
    private String avatarUrl;
    private String email;
    private Long classId;
    private Long learningModuleId;
}
