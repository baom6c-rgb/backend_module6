package com.be_ai_learning_platform.service.Impl;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.AdminRepository;
import com.be_ai_learning_platform.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;

    @Override
    public List<User> getAll() {
        return adminRepository.findAll();
    }

    @Override
    public User getById(Long id) {
        return adminRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    public User add(User user) {
        if (user.getStatus() == null) {
            user.setStatus(UserStatus.WAITING_APPROVAL);
        }
        return adminRepository.save(user);
    }

    @Override
    public User update(Long id, User user) {
        User oldUser = getById(id);

        oldUser.setFullName(user.getFullName());
        oldUser.setEmail(user.getEmail());
        oldUser.setClassName(user.getClassName());
        oldUser.setLearningModule(user.getLearningModule());
        oldUser.setStatus(user.getStatus()); // ⭐ cho phép đổi status

        return adminRepository.save(oldUser);
    }

    @Override
    public void delete(Long id) {
        adminRepository.deleteById(id);
    }

    @Override
    public List<User> getActiveUsers() {
        return adminRepository.findByStatus(UserStatus.ACTIVE);
    }

    @Override
    public List<User> findByStatus(UserStatus status) {
        return adminRepository.findByStatus(status);
    }
}
