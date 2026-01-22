package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.CompleteProfileRequest;
import com.be_ai_learning_platform.dto.request.LoginRequest;
import com.be_ai_learning_platform.dto.request.RegisterRequest;
import com.be_ai_learning_platform.entity.User;

public interface AuthService {

    // Đăng ký bằng form (email + password)
    void register(RegisterRequest request);

    // Đăng nhập bằng form
    User login(LoginRequest request);

    void logout(String token);

    // Hoàn tất hồ sơ sau Google login (CREATED → WAITING_APPROVAL)
    void completeProfile(CompleteProfileRequest request);

    void processForgotPassword(String email);
    void updatePassword(String token, String newPassword);
}
