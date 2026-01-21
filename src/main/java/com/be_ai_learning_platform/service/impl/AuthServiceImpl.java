package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.CompleteProfileRequest;
import com.be_ai_learning_platform.dto.request.LoginRequest;
import com.be_ai_learning_platform.dto.request.RegisterRequest;
import com.be_ai_learning_platform.entity.*;
import com.be_ai_learning_platform.entity.enums.LoginProvider;
import com.be_ai_learning_platform.entity.enums.RegisterMethod;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.*;
import com.be_ai_learning_platform.service.AuthService;
import com.be_ai_learning_platform.service.mail.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final ModuleRepository moduleRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    // ======================= REGISTER (FORM) =======================
    @Override
    public void register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        ClassEntity clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new RuntimeException("Class not found"));

        LearningModule module = moduleRepository.findById(request.getModuleId())
                .orElseThrow(() -> new RuntimeException("Module not found"));

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());

        user.setClassName(clazz);
        user.setLearningModule(module);

        user.setRegisterMethod(RegisterMethod.FORM);
        user.setLoginProvider(LoginProvider.FORM);
        user.setStatus(UserStatus.WAITING_APPROVAL);

        user.setApproveToken(UUID.randomUUID().toString());
        user.setCreatedAt(LocalDateTime.now());
        user.setIsDeleted(false);

        userRepository.save(user);

        assignStudentRole(user);

        mailService.notifyWaitingApproval(user);
    }

    // ======================= LOGIN (FORM) =======================
    @Override
    public User login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (user.getLoginProvider() != LoginProvider.FORM) {
            throw new RuntimeException("Please login using Google");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new RuntimeException("Account is blocked");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return user;
    }

    // ======================= COMPLETE PROFILE (GOOGLE) =======================
    @Override
    public void completeProfile(CompleteProfileRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() != UserStatus.CREATED) {
            throw new RuntimeException("Invalid user state");
        }

        ClassEntity clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new RuntimeException("Class not found"));

        LearningModule module = moduleRepository.findById(request.getModuleId())
                .orElseThrow(() -> new RuntimeException("Module not found"));

        user.setFullName(request.getFullName());
        user.setClassName(clazz);
        user.setLearningModule(module);

        user.setStatus(UserStatus.WAITING_APPROVAL);
        user.setApproveToken(UUID.randomUUID().toString());

        userRepository.save(user);

        mailService.notifyWaitingApproval(user);
    }

    // ======================= PRIVATE =======================
    private void assignStudentRole(User user) {

        Role role = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new RuntimeException("Role STUDENT not found"));

        UserRole ur = new UserRole();
        ur.setUser(user);
        ur.setRole(role);

        userRoleRepository.save(ur);
    }
}
