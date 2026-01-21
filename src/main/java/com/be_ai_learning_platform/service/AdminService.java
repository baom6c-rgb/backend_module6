package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;

import java.util.List;

public interface AdminService {
    List<User> getActiveUsers();
    List<User> getAll();
    User getById(Long id);
    User add(User user);
    User update(Long id, User user);
    void delete(Long id);
    List<User> findByStatus(UserStatus status);
}
