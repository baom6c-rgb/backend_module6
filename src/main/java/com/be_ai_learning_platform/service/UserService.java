package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.UserUpdateDTO;
import com.be_ai_learning_platform.dto.request.CompleteProfileRequest;
import com.be_ai_learning_platform.dto.request.StudentUpdateProfileRequest;
import com.be_ai_learning_platform.dto.response.UserStatusResponse;
import com.be_ai_learning_platform.dto.response.StudentProfileResponse;
import com.be_ai_learning_platform.entity.User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    // US2
    UserStatusResponse getStatusByEmail(String email);

    // US3 - STUDENT
    @Transactional
    void updateStudentProfileByEmail(String email, StudentUpdateProfileRequest request);

    @Transactional(readOnly = true)
    StudentProfileResponse getMyProfileByEmail(String email);

    // (để sau dùng admin/internal)
    @Transactional
    void updateStudentProfile(Long userId, StudentUpdateProfileRequest request);

    // Admin duyệt user
    void approveUser(Long userId);

    // User ACTIVE cập nhật profile
    User updateProfile(Long id, UserUpdateDTO updateDTO);

    @Transactional
    StudentProfileResponse updateMyAvatarByEmail(String email, MultipartFile file);
}
