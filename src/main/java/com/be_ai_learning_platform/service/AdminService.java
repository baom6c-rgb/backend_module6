package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.AdminAddUserRequest;
import com.be_ai_learning_platform.dto.response.AdminResponse;
import java.util.List;

public interface AdminService {
    // Thêm mới User (US4)
    AdminResponse addUser(AdminAddUserRequest request);

    // Lấy danh sách hiển thị
    List<AdminResponse> getAllUsers();
    List<AdminResponse> getPendingApprovals();
    List<AdminResponse> getActiveStudents();

    // Các tác vụ khác
    void approve(Long userId);
    void reject(Long userId);
    void block(Long userId);
    void unblock(Long userId);
}