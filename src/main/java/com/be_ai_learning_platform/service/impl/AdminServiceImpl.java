package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.response.AdminResponse;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;

    @Override
    public List<AdminResponse> getPendingApprovals() {
        return userRepository.findByStatus(UserStatus.WAITING_APPROVAL)
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Override
    public void approve(Long userId) {
        User user = getUser(userId);

        if (user.getStatus() != UserStatus.WAITING_APPROVAL) {
            throw new RuntimeException("User is not waiting for approval");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Override
    public void reject(Long userId) {
        User user = getUser(userId);

        if (user.getStatus() != UserStatus.WAITING_APPROVAL) {
            throw new RuntimeException("User is not waiting for approval");
        }

        user.setStatus(UserStatus.REJECTED);
        userRepository.save(user);
    }

    @Override
    public List<AdminResponse> getActiveStudents() {
        return userRepository.findByStatus(UserStatus.ACTIVE)
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Override
    public void block(Long userId) {
        User user = getUser(userId);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("Only ACTIVE user can be blocked");
        }

        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);
    }

    @Override
    public void unblock(Long userId) {
        User user = getUser(userId);

        if (user.getStatus() != UserStatus.BLOCKED) {
            throw new RuntimeException("Only BLOCKED user can be unblocked");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private AdminResponse toAdminResponse(User u) {
        // ✅ ClassEntity field trong dự án mày là: className (String) -> getter getClassName()
        Long classId = (u.getClassName() != null) ? u.getClassName().getId() : null;
        String className = (u.getClassName() != null) ? u.getClassName().getClassName() : null;

        // ✅ LearningModule field trong dự án mày là: moduleName (String) -> getter getModuleName()
        Long moduleId = (u.getLearningModule() != null) ? u.getLearningModule().getId() : null;
        String moduleName = (u.getLearningModule() != null) ? u.getLearningModule().getModuleName() : null;

        return AdminResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .status(u.getStatus())
                .classId(classId)
                .className(className)
                .moduleId(moduleId)
                .moduleName(moduleName)
                .build();
    }
}
