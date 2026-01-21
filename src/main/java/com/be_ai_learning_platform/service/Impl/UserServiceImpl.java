package com.be_ai_learning_platform.service.Impl;

import com.be_ai_learning_platform.dto.UserUpdateDTO;
import com.be_ai_learning_platform.entity.ClassEntity;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.repository.ClassRepository;
import com.be_ai_learning_platform.repository.ModuleRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final ModuleRepository moduleRepository;

    @Override
    @Transactional
    public User updateProfile(Long id, UserUpdateDTO updateDTO) {
        // Giải quyết lỗi orElseThrow bằng cách dùng Supplier tường minh
        User user = userRepository.findById(id)
                .orElseThrow(new Supplier<RuntimeException>() {
                    @Override
                    public RuntimeException get() {
                        return new RuntimeException("Không tìm thấy học viên với ID: " + id);
                    }
                });

        // 1. Cập nhật các trường thông tin cơ bản
        user.setFullName(updateDTO.getFullName());
        user.setAvatarUrl(updateDTO.getAvatarUrl());
        user.setEmail(updateDTO.getEmail());

        // 2. Cập nhật Lớp học (Sửa lỗi classId gạch đỏ: dùng .getClassId())
        if (updateDTO.getClassId() != null) {
            ClassEntity classEntity = classRepository.findById(updateDTO.getClassId())
                    .orElseThrow(() -> new RuntimeException("Lớp học không tồn tại"));
            user.setClassName(classEntity);
        }

        // 3. Cập nhật Module (Dùng full path để tránh xung đột với java.lang.Module)
        if (updateDTO.getLearningModuleId() != null) {
            com.be_ai_learning_platform.entity.Module moduleEntity = moduleRepository.findById(updateDTO.getLearningModuleId())
                    .orElseThrow(() -> new RuntimeException("Module không tồn tại"));

            user.setLearningModule(moduleEntity);
        }

        // 4. Ghi nhận thời gian cập nhật
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }
}