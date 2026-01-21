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
    public User updateProfile(Long id, UserUpdateDTO updateDTO) {
        return null;
    }
}
