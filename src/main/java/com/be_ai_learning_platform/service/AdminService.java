package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.AdminUpdateUserRequest;
import com.be_ai_learning_platform.dto.response.AdminResponse;
import com.be_ai_learning_platform.dto.response.AdminUserDetailResponse;
import com.be_ai_learning_platform.dto.response.OptionResponse;

import java.util.List;

public interface AdminService {

    // approvals
    List<AdminResponse> getPendingApprovals();
    void approve(Long userId);
    void reject(Long userId);

    // active students
    List<AdminResponse> getActiveStudents();
    void block(Long userId);
    void unblock(Long userId);

    // ===== US5 =====
    AdminUserDetailResponse getUserDetail(Long userId);
    AdminUserDetailResponse updateUser(Long userId, AdminUpdateUserRequest request);

    // ===== US6 =====
    List<AdminResponse> getBlockedUsers();
    void blockUser(Long userId);
    void unblockUser(Long userId);


    // dropdown options for admin form
    List<OptionResponse> getRoleOptions();
    List<OptionResponse> getClassOptions();
    List<OptionResponse> getModuleOptions();
}
