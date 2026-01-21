package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.UserUpdateDTO;
import com.be_ai_learning_platform.dto.request.CompleteProfileRequest;
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
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final ModuleRepository moduleRepository;

    /**
     * Hoàn tất hồ sơ sau khi Google login lần đầu
     * CREATED → WAITING_APPROVAL
     */
    @Override
    public void completeProfile(CompleteProfileRequest request) {

        // 1️⃣ Tìm user theo email (đã được tạo khi Google login)
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2️⃣ Chỉ cho phép khi user ở trạng thái CREATED
        if (user.getStatus() != UserStatus.CREATED) {
            throw new RuntimeException("User is not allowed to complete profile");
        }

        // 3️⃣ Lấy class
        ClassEntity clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new RuntimeException("Class not found"));

        // 4️⃣ Lấy module
        LearningModule module = moduleRepository.findById(request.getModuleId())
                .orElseThrow(() -> new RuntimeException("Module not found"));

        // 5️⃣ Update thông tin
        user.setFullName(request.getFullName());
        user.setClassName(clazz);
        user.setLearningModule(module);

        // 🔥 QUAN TRỌNG: chuyển sang WAITING_APPROVAL
        user.setStatus(UserStatus.WAITING_APPROVAL);

        userRepository.save(user);
    }

    /**
     * Admin duyệt user
     * WAITING_APPROVAL → ACTIVE
     */
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
            LearningModule moduleEntity = moduleRepository.findById(updateDTO.getLearningModuleId())
                    .orElseThrow(() -> new RuntimeException("Module không tồn tại"));

            user.setLearningModule(moduleEntity);
        }

        // 4. Ghi nhận thời gian cập nhật
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

}
