package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.response.AdminResponse;

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
}
