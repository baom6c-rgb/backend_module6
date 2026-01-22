package com.be_ai_learning_platform.dto.response;

import com.be_ai_learning_platform.entity.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserStatusResponse {
    private String email;
    private UserStatus status;
}
