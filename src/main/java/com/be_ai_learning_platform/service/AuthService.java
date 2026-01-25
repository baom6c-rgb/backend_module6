package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.CompleteProfileRequest;
import com.be_ai_learning_platform.dto.request.LoginRequest;
import com.be_ai_learning_platform.dto.request.RegisterRequest;
import com.be_ai_learning_platform.entity.User;

public interface AuthService {
    void register(RegisterRequest request);
    User login(LoginRequest request);
    void logout(String token);

    // ✅ đổi void -> User
    User completeProfile(CompleteProfileRequest request);

    void processForgotPassword(String email);
    void updatePassword(String token, String newPassword);
}
