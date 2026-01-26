package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.dto.request.RegisterRequest;

public interface RegisterService {
    User registerByEmail(RegisterRequest request);
}
