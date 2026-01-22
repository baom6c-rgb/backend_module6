package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.AdminAddUserRequest;
import com.be_ai_learning_platform.dto.response.AdminResponse;
import com.be_ai_learning_platform.entity.*;
import com.be_ai_learning_platform.entity.enums.LoginProvider;
import com.be_ai_learning_platform.entity.enums.RegisterMethod;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.*;
import com.be_ai_learning_platform.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final ModuleRepository moduleRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AdminResponse addUser(AdminAddUserRequest request) {
        // 1. Kiểm tra email trùng
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã tồn tại!");
        }

        // 2. Tìm các thực thể liên quan (Lớp, Học phần, Quyền)
        ClassEntity clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lớp"));

        LearningModule module = moduleRepository.findById(request.getModuleId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy học phần"));

        // ĐÂY LÀ CHỖ DỄ LỖI NHẤT: Tìm Role dựa trên roleName gửi từ Request
        Role foundRole = roleRepository.findByName(request.getRoleName())
                .orElseThrow(() -> new RuntimeException("Quyền " + request.getRoleName() + " không tồn tại"));

        // 3. Tạo User và set các trường bắt buộc để tránh lỗi SQL [login_provider cannot be null]
        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setClassName(clazz);
        user.setLearningModule(module);

        // Set các giá trị mặc định cho login và register
        user.setLoginProvider(LoginProvider.FORM);
        user.setRegisterMethod(RegisterMethod.FORM);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        // 4. Gán Role vào bảng trung gian UserRole
        UserRole userRole = new UserRole();
        userRole.setUser(savedUser);
        userRole.setRole(foundRole);
        userRoleRepository.save(userRole);

        return toAdminResponse(savedUser);
    }

    @Override
    public List<AdminResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Override
    public List<AdminResponse> getPendingApprovals() {
        return userRepository.findByStatus(UserStatus.WAITING_APPROVAL).stream()
                .map(this::toAdminResponse).toList();
    }

    @Override
    public List<AdminResponse> getActiveStudents() {
        return userRepository.findByStatus(UserStatus.ACTIVE).stream()
                .map(this::toAdminResponse).toList();
    }

    @Override
    public void approve(Long userId) {
        User user = getUser(userId);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Override
    public void block(Long userId) {
        User user = getUser(userId);
        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);
    }

    @Override
    public void unblock(Long userId) {
        User user = getUser(userId);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Override
    public void reject(Long userId) {
        userRepository.deleteById(userId);
    }

    // Helper methods
    private User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
    }

    private AdminResponse toAdminResponse(User u) {
        return AdminResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .status(u.getStatus())
                .classId(u.getClassName() != null ? u.getClassName().getId() : null)
                .className(u.getClassName() != null ? u.getClassName().getClassName() : null)
                .moduleId(u.getLearningModule() != null ? u.getLearningModule().getId() : null)
                .moduleName(u.getLearningModule() != null ? u.getLearningModule().getModuleName() : null)
                .build();
    }
}