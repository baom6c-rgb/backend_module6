package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.entity.User;

public interface GoogleAuthService {
    User authenticate(String idToken); // ✅ bỏ "throws Exception"
}
