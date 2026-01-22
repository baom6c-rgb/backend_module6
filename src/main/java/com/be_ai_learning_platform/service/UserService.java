package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.UserUpdateDTO;
import com.be_ai_learning_platform.dto.request.CompleteProfileRequest;
import com.be_ai_learning_platform.dto.request.StudentUpdateProfileRequest;
import com.be_ai_learning_platform.dto.response.UserStatusResponse;
import com.be_ai_learning_platform.dto.response.StudentProfileResponse;
import com.be_ai_learning_platform.entity.User;
import org.springframework.transaction.annotation.Transactional;

public interface UserService {

    // US2
    void completeProfile(CompleteProfileRequest request);
    UserStatusResponse getStatusByEmail(String email);

    // US3 - STUDENT
    @Transactional
    void updateStudentProfileByEmail(String email, StudentUpdateProfileRequest request);

    @Transactional(readOnly = true)
    StudentProfileResponse getMyProfileByEmail(String email);

    // (để sau dùng admin/internal)
    @Transactional
    void updateStudentProfile(Long userId, StudentUpdateProfileRequest request);

    // legacy/admin (đang có)
    User updateProfile(Long id, UserUpdateDTO updateDTO);
    void approveUser(Long userId);
}
