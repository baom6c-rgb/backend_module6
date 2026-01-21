package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.LoginRequest;
import com.be_ai_learning_platform.dto.request.RegisterRequest;
import com.be_ai_learning_platform.entity.ClassEntity;
import com.be_ai_learning_platform.entity.Role;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.UserRole;
import com.be_ai_learning_platform.entity.enums.LoginProvider;
import com.be_ai_learning_platform.entity.enums.RegisterMethod;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.ClassRepository;
import com.be_ai_learning_platform.repository.RoleRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.repository.UserRoleRepository;
import com.be_ai_learning_platform.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    // ======================= REGISTER (FORM) =======================
    @Override
    public void register(RegisterRequest request) {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        ClassEntity clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new RuntimeException("Class not found"));

        // --------- TẠO USER ---------
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setClazz(clazz);

        user.setRegisterMethod(RegisterMethod.FORM);
        user.setLoginProvider(LoginProvider.FORM);
        user.setStatus(UserStatus.WAITING_APPROVAL);

        user.setCreatedAt(LocalDateTime.now());
        user.setIsDeleted(false);

        userRepository.save(user);

        // --------- GÁN ROLE STUDENT ---------
        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new RuntimeException("Role STUDENT not found"));

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(studentRole);

        userRoleRepository.save(userRole);
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

}
