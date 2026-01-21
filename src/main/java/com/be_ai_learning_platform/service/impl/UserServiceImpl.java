package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.UserUpdateDTO;
import com.be_ai_learning_platform.entity.ClassEntity;
import com.be_ai_learning_platform.entity.LearningModule;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.ClassRepository;
import com.be_ai_learning_platform.repository.ModuleRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final ModuleRepository moduleRepository;

    // ======================= UPDATE PROFILE (ACTIVE USER) =======================
    @Override
    public User updateProfile(Long id, UserUpdateDTO updateDTO) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("Only ACTIVE user can update profile");
        }

        user.setFullName(updateDTO.getFullName());
        user.setAvatarUrl(updateDTO.getAvatarUrl());
        user.setEmail(updateDTO.getEmail());

        if (updateDTO.getClassId() != null) {
            ClassEntity clazz = classRepository.findById(updateDTO.getClassId())
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            user.setClassName(clazz);
        }

        if (updateDTO.getLearningModuleId() != null) {
            LearningModule module = moduleRepository.findById(updateDTO.getLearningModuleId())
                    .orElseThrow(() -> new RuntimeException("Module not found"));
            user.setLearningModule(module);
        }

        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    // ======================= ADMIN APPROVE =======================
    @Override
    public void approveUser(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() != UserStatus.WAITING_APPROVAL) {
            throw new RuntimeException("User is not waiting for approval");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }
}
