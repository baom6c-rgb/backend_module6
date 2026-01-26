package com.be_ai_learning_platform.dto.response;

import com.be_ai_learning_platform.entity.enums.LoginProvider;
import com.be_ai_learning_platform.entity.enums.RegisterMethod;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminResponse {
    private Long id;
    private String email;
    private String fullName;
    private UserStatus status;
    private RegisterMethod registerMethod;
    private LoginProvider loginProvider;
    private String role; // ADMIN / STUDENT
    // optional hiển thị nhanh
    private Long classId;
    private String className;

    private Long moduleId;
    private String moduleName;
}
