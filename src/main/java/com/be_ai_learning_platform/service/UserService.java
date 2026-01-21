package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.CompleteProfileRequest;
import com.be_ai_learning_platform.dto.response.AuthResponse;
  import com.be_ai_learning_platform.dto.UserUpdateDTO;
import com.be_ai_learning_platform.entity.User;

public interface UserService {

    void completeProfile(CompleteProfileRequest request);

    void approveUser(Long userId);
  User updateProfile(Long id, UserUpdateDTO updateDTO);
}
