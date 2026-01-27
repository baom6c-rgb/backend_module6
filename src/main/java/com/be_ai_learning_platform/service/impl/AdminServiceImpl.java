package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.AdminAddAdminRequest;
import com.be_ai_learning_platform.dto.request.AdminAddUserRequest;
import com.be_ai_learning_platform.dto.request.AdminUpdateUserRequest;
import com.be_ai_learning_platform.dto.response.AdminResponse;
import com.be_ai_learning_platform.dto.response.AdminUserDetailResponse;
import com.be_ai_learning_platform.dto.response.OptionResponse;
import com.be_ai_learning_platform.entity.ClassEntity;
import com.be_ai_learning_platform.entity.LearningModule;
import com.be_ai_learning_platform.entity.Role;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.UserRole;
import com.be_ai_learning_platform.entity.enums.LoginProvider;
import com.be_ai_learning_platform.entity.enums.RegisterMethod;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.ClassRepository;
import com.be_ai_learning_platform.repository.ModuleRepository;
import com.be_ai_learning_platform.repository.RoleRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.repository.UserRoleRepository;
import com.be_ai_learning_platform.service.AdminService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    // Fix case: PUT trả role cũ nhưng GET đúng (Hibernate 1st-level cache)
    private final EntityManager entityManager;

    // =========================================================
    // US4 - Admin add user + list all users
    // =========================================================
    @Override
    public AdminResponse addUser(AdminAddUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã tồn tại!");
        }

        String roleName = request.getRoleName() == null ? "" : request.getRoleName().trim().toUpperCase();
        if ("ADMIN".equals(roleName)) {
            throw new RuntimeException("Dùng API /api/admin/users/admin để tạo ADMIN");
        }

        if (request.getClassId() == null) {
            throw new RuntimeException("ID lớp không được trống");
        }
        if (request.getModuleId() == null) {
            throw new RuntimeException("ID học phần không được trống");
        }

        ClassEntity clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lớp"));

        LearningModule module = moduleRepository.findById(request.getModuleId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy học phần"));

        Role foundRole = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Quyền " + roleName + " không tồn tại"));

        User user = new User();
        user.setEmail(request.getEmail().trim());
        user.setFullName(request.getFullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        user.setClassName(clazz);
        user.setLearningModule(module);

        user.setLoginProvider(LoginProvider.FORM);
        user.setRegisterMethod(RegisterMethod.FORM);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setIsDeleted(false);

        User savedUser = userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setUser(savedUser);
        userRole.setRole(foundRole);
        userRoleRepository.save(userRole);

        // đảm bảo list trong entity có (tránh mapping DTO đọc null)
        if (savedUser.getUserRoles() == null) {
            savedUser.setUserRoles(new ArrayList<>());
        }
        savedUser.getUserRoles().add(userRole);

        return toAdminResponse(savedUser);
    }

    @Override
    public AdminResponse addAdmin(AdminAddAdminRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã tồn tại!");
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new RuntimeException("Quyền ADMIN không tồn tại"));

        User user = new User();
        user.setEmail(request.getEmail().trim());
        user.setFullName(request.getFullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        // ✅ ADMIN: allow NULL in DB for class/module
        user.setClassName(null);
        user.setLearningModule(null);

        user.setLoginProvider(LoginProvider.FORM);
        user.setRegisterMethod(RegisterMethod.FORM);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setIsDeleted(false);

        User savedUser = userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setUser(savedUser);
        userRole.setRole(adminRole);
        userRoleRepository.save(userRole);

        // đảm bảo list trong entity có (tránh mapping DTO đọc null)
        if (savedUser.getUserRoles() == null) {
            savedUser.setUserRoles(new ArrayList<>());
        }
        savedUser.getUserRoles().add(userRole);

        return toAdminResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    // =========================================================
    // Approvals (Pending → Approve/Reject)
    // =========================================================
    @Override
    @Transactional(readOnly = true)
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

        // reject = đổi trạng thái (KHÔNG delete DB)
        user.setStatus(UserStatus.REJECTED);
        userRepository.save(user);
    }

    // =========================================================
    // US6 - Students lists + block/unblock
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public List<AdminResponse> getActiveStudents() {
        // chỉ lấy STUDENT ACTIVE (không lẫn ADMIN)
        return userRepository.findByStatusAndRoleName(UserStatus.ACTIVE, "STUDENT")
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminResponse> getBlockedUsers() {
        return userRepository.findByStatusAndRoleName(UserStatus.BLOCKED, "STUDENT")
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    /**
     * Alias để tương thích code cũ nếu có nơi đang gọi block/unblock
     * (AdminController của mày hiện gọi blockUser/unblockUser)
     */
    @Override
    public void block(Long userId) {
        blockUser(userId);
    }

    @Override
    public void unblock(Long userId) {
        unblockUser(userId);
    }

    @Override
    public void blockUser(Long userId) {
        User user = getUser(userId);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("Only ACTIVE user can be blocked");
        }

        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);
    }

    @Override
    public void unblockUser(Long userId) {
        User user = getUser(userId);

        if (user.getStatus() != UserStatus.BLOCKED) {
            throw new RuntimeException("Only BLOCKED user can be unblocked");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    // =========================================================
    // US5 - Admin edit user
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(Long userId) {
        User user = getUser(userId);
        return toAdminUserDetailResponse(user);
    }

    /**
     * ✅ UPDATE USER (FIXED theo đoạn mày gửi)
     * - Email: KHÔNG cho chỉnh
     * - Role: bắt buộc
     * - Nếu ADMIN: class/module = null
     * - Nếu không phải ADMIN: bắt buộc classId/moduleId
     * - Fix PUT trả role cũ: flush + clear + re-fetch
     */
    @Override
    public AdminUserDetailResponse updateUser(Long userId, AdminUpdateUserRequest request) {
        User user = getUser(userId);

        // 1) update basic fields (email không cho chỉnh)
        if (request.getFullName() == null || request.getFullName().trim().isEmpty()) {
            throw new RuntimeException("Full name is required");
        }
        user.setFullName(request.getFullName().trim());

        // 2) update role (bắt buộc)
        String roleName = request.getRoleName() == null ? "" : request.getRoleName().trim().toUpperCase();
        if (roleName.isEmpty()) {
            throw new RuntimeException("RoleName is required");
        }

        Role foundRole = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Quyền " + roleName + " không tồn tại"));

        // 2.1) set/replace role (assume 1 role per user)
        UserRole userRole;
        if (user.getUserRoles() != null && !user.getUserRoles().isEmpty()) {
            userRole = user.getUserRoles().get(0);
            userRole.setRole(foundRole);
            userRoleRepository.save(userRole);
        } else {
            userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(foundRole);
            userRoleRepository.save(userRole);

            // đảm bảo list trong entity có (tránh toAdminResponse/toAdminUserDetailResponse đọc null)
            if (user.getUserRoles() == null) {
                user.setUserRoles(new ArrayList<>());
            }
            user.getUserRoles().add(userRole);
        }

        // 3) update class/module theo role
        if ("ADMIN".equals(roleName)) {
            // ✅ ADMIN: allow NULL in DB for class/module
            user.setClassName(null);
            user.setLearningModule(null);
        } else {
            // ✅ STUDENT/others: require classId/moduleId
            if (request.getClassId() == null) {
                throw new RuntimeException("ClassId is required");
            }
            if (request.getModuleId() == null) {
                throw new RuntimeException("ModuleId is required");
            }

            ClassEntity clazz = classRepository.findById(request.getClassId())
                    .orElseThrow(() -> new RuntimeException("Class not found"));
            user.setClassName(clazz);

            LearningModule module = moduleRepository.findById(request.getModuleId())
                    .orElseThrow(() -> new RuntimeException("Module not found"));
            user.setLearningModule(module);
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // fix “PUT trả role cũ”
        entityManager.flush();
        entityManager.clear();

        User fresh = getUser(userId);
        return toAdminUserDetailResponse(fresh);
    }

    // =========================================================
    // Options (dropdown)
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public List<OptionResponse> getRoleOptions() {
        return roleRepository.findAll()
                .stream()
                .map(r -> new OptionResponse(r.getId(), r.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OptionResponse> getClassOptions() {
        return classRepository.findAll()
                .stream()
                .map(c -> new OptionResponse(c.getId(), c.getClassName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OptionResponse> getModuleOptions() {
        return moduleRepository.findAll()
                .stream()
                .map(m -> new OptionResponse(m.getId(), m.getModuleName()))
                .toList();
    }

    // =========================================================
    // Helpers
    // =========================================================
    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private AdminResponse toAdminResponse(User u) {
        String roleName = null;
        if (u.getUserRoles() != null && !u.getUserRoles().isEmpty() && u.getUserRoles().get(0).getRole() != null) {
            roleName = u.getUserRoles().get(0).getRole().getName();
        }

        return AdminResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .status(u.getStatus())

                // 🔥 BỔ SUNG CHO ADMIN
                .registerMethod(u.getRegisterMethod())
                .loginProvider(u.getLoginProvider())
                .role(roleName)

                // optional hiển thị nhanh
                .classId(u.getClassName() != null ? u.getClassName().getId() : null)
                .className(u.getClassName() != null ? u.getClassName().getClassName() : null)

                .moduleId(u.getLearningModule() != null ? u.getLearningModule().getId() : null)
                .moduleName(u.getLearningModule() != null ? u.getLearningModule().getModuleName() : null)

                .build();
    }

    private AdminUserDetailResponse toAdminUserDetailResponse(User u) {
        Long roleId = null;
        String roleName = null;
        if (u.getUserRoles() != null && !u.getUserRoles().isEmpty() && u.getUserRoles().get(0).getRole() != null) {
            roleId = u.getUserRoles().get(0).getRole().getId();
            roleName = u.getUserRoles().get(0).getRole().getName();
        }

        return AdminUserDetailResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .avatarUrl(u.getAvatarUrl())
                .phoneNumber(u.getPhoneNumber())
                .address(u.getAddress())
                .status(u.getStatus())
                .classId(u.getClassName() != null ? u.getClassName().getId() : null)
                .className(u.getClassName() != null ? u.getClassName().getClassName() : null)
                .moduleId(u.getLearningModule() != null ? u.getLearningModule().getId() : null)
                .moduleName(u.getLearningModule() != null ? u.getLearningModule().getModuleName() : null)
                .roleId(roleId)
                .roleName(roleName)
                .build();
    }
}
