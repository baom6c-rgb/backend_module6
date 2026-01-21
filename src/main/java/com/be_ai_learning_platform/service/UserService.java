package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.UserUpdateDTO;
import com.be_ai_learning_platform.entity.User;

public interface UserService {
    User updateProfile(Long id, UserUpdateDTO updateDTO);
}
