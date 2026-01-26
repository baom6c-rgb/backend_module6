package com.be_ai_learning_platform.dto.response;

import com.be_ai_learning_platform.entity.enums.UserStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUserDetailResponse {
    private Long id;

    private String email;
    private String fullName;

    private String avatarUrl;
    private String phoneNumber;
    private String address;

    private UserStatus status;

    private Long classId;
    private String className;

    private Long moduleId;
    private String moduleName;

    private Long roleId;
    private String roleName;
}
