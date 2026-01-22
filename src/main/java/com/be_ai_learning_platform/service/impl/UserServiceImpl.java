package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.UserUpdateDTO;
import com.be_ai_learning_platform.dto.request.CompleteProfileRequest;
import com.be_ai_learning_platform.dto.request.StudentUpdateProfileRequest;
import com.be_ai_learning_platform.dto.response.StudentProfileResponse;
import com.be_ai_learning_platform.dto.response.UserStatusResponse;
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

    // ====================== US2: polling status ======================
    @Override
    @Transactional(readOnly = true)
    public UserStatusResponse getStatusByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(u -> new UserStatusResponse(u.getEmail(), u.getStatus()))
                .orElseGet(() -> new UserStatusResponse(email, null));
    }

    // ====================== US3: GET my profile ======================
    @Override
    @Transactional(readOnly = true)
    public StudentProfileResponse getMyProfileByEmail(String email) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long classId = (u.getClassName() != null) ? u.getClassName().getId() : null;
        String className = (u.getClassName() != null) ? u.getClassName().getClassName() : null;

        Long moduleId = (u.getLearningModule() != null) ? u.getLearningModule().getId() : null;
        String moduleName = (u.getLearningModule() != null) ? u.getLearningModule().getModuleName() : null;

        return StudentProfileResponse.builder()
                .email(u.getEmail())
                .fullName(u.getFullName())
                .avatarUrl(u.getAvatarUrl())
                .phoneNumber(u.getPhoneNumber())
                .address(u.getAddress())
                .status(u.getStatus())
                .classId(classId)
                .className(className)
                .moduleId(moduleId)
                .moduleName(moduleName)
                .build();
    }

    // ====================== US3: UPDATE by email (token) ======================
    @Override
    public void updateStudentProfileByEmail(String email, StudentUpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("User is not active");
        }

        // normalize nhẹ
        String fullName = request.getFullName() != null ? request.getFullName().trim() : null;
        String avatarUrl = request.getAvatarUrl() != null ? request.getAvatarUrl().trim() : null;
        String phone = request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null;
        String address = request.getAddress() != null ? request.getAddress().trim() : null;

        user.setFullName(fullName);
        user.setAvatarUrl(avatarUrl);
        user.setPhoneNumber(phone);
        user.setAddress(address);
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
    }

    // ====================== (optional) UPDATE by userId ======================
    @Override
    public void updateStudentProfile(Long userId, StudentUpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("User is not active");
        }

        user.setFullName(request.getFullName());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setAddress(request.getAddress());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
    }

    // ====================== US2: complete profile (Google) ======================
    @Override
    public void completeProfile(CompleteProfileRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() != UserStatus.CREATED) {
            throw new RuntimeException("User is not allowed to complete profile");
        }

        ClassEntity clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new RuntimeException("Class not found"));

        LearningModule module = moduleRepository.findById(request.getModuleId())
                .orElseThrow(() -> new RuntimeException("Module not found"));

        user.setFullName(request.getFullName());
        user.setClassName(clazz);
        user.setLearningModule(module);
        user.setStatus(UserStatus.WAITING_APPROVAL);

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

    @Override
    public User updateProfile(Long id, UserUpdateDTO updateDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(new Supplier<RuntimeException>() {
                    @Override
                    public RuntimeException get() {
                        return new RuntimeException("Không tìm thấy học viên với ID: " + id);
                    }
                });

        user.setFullName(updateDTO.getFullName());
        user.setAvatarUrl(updateDTO.getAvatarUrl());
        user.setEmail(updateDTO.getEmail());

        if (updateDTO.getClassId() != null) {
            ClassEntity classEntity = classRepository.findById(updateDTO.getClassId())
                    .orElseThrow(() -> new RuntimeException("Lớp học không tồn tại"));
            user.setClassName(classEntity);
        }

        if (updateDTO.getLearningModuleId() != null) {
            LearningModule moduleEntity = moduleRepository.findById(updateDTO.getLearningModuleId())
                    .orElseThrow(() -> new RuntimeException("Module không tồn tại"));
            user.setLearningModule(moduleEntity);
        }

        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }
}
