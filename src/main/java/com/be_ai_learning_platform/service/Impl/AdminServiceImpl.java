package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.AdminUpdateUserRequest;
import com.be_ai_learning_platform.dto.response.AdminResponse;
import com.be_ai_learning_platform.dto.response.AdminUserDetailResponse;
import com.be_ai_learning_platform.dto.response.OptionResponse;
import com.be_ai_learning_platform.entity.ClassEntity;
import com.be_ai_learning_platform.entity.LearningModule;
import com.be_ai_learning_platform.entity.Role;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.UserRole;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.ClassRepository;
import com.be_ai_learning_platform.repository.ModuleRepository;
import com.be_ai_learning_platform.repository.RoleRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.repository.UserRoleRepository;
import com.be_ai_learning_platform.service.AdminService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ClassRepository classRepository;
    private final ModuleRepository moduleRepository;

    // để fix case: PUT trả role cũ nhưng GET đúng (Hibernate 1st-level cache)
    private final EntityManager entityManager;

    // ================= Existing (approve/block) =================

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
        return userRepository.findByStatusAndRoleName(UserStatus.ACTIVE, "STUDENT")
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

    // ================= US5 =================

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(Long userId) {
        User user = getUser(userId);
        return toAdminUserDetailResponse(user);
    }

    @Override
    public AdminUserDetailResponse updateUser(Long userId, AdminUpdateUserRequest request) {
        User user = getUser(userId);

        // 1) email unique
        String newEmail = request.getEmail().trim();
        if (!user.getEmail().equalsIgnoreCase(newEmail) && userRepository.existsByEmail(newEmail)) {
            throw new RuntimeException("Email already exists");
        }

        // 2) update basic fields
        user.setFullName(request.getFullName().trim());
        user.setEmail(newEmail);

        // 3) update class
        ClassEntity clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new RuntimeException("Class not found"));
        user.setClassName(clazz);

        // 4) update module/model
        LearningModule module = moduleRepository.findById(request.getModuleId())
                .orElseThrow(() -> new RuntimeException("Module not found"));
        user.setLearningModule(module);

        // 5) update role (avoid duplicate + avoid reinsert when not changed)
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));

        boolean alreadyHasThatRole = userRoleRepository.existsByUser_IdAndRole_Id(userId, request.getRoleId());
        if (!alreadyHasThatRole) {
            userRoleRepository.deleteAllByUser_Id(userId);

            UserRole ur = new UserRole();
            ur.setUser(user);
            ur.setRole(role);
            userRoleRepository.save(ur);
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // ===== Fix “PUT vẫn trả role cũ nhưng GET đúng” =====
        // ép Hibernate sync & clear cache session, rồi fetch lại từ DB thật
        entityManager.flush();
        entityManager.clear();

        User fresh = getUser(userId);
        return toAdminUserDetailResponse(fresh);
    }

    // ================= Options (dropdown) =================

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

    // ================= helpers =================

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private AdminResponse toAdminResponse(User u) {
        Long classId = (u.getClassName() != null) ? u.getClassName().getId() : null;
        String className = (u.getClassName() != null) ? u.getClassName().getClassName() : null;

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

    private AdminUserDetailResponse toAdminUserDetailResponse(User u) {
        Long classId = (u.getClassName() != null) ? u.getClassName().getId() : null;
        String className = (u.getClassName() != null) ? u.getClassName().getClassName() : null;

        Long moduleId = (u.getLearningModule() != null) ? u.getLearningModule().getId() : null;
        String moduleName = (u.getLearningModule() != null) ? u.getLearningModule().getModuleName() : null;

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
                .classId(classId)
                .className(className)
                .moduleId(moduleId)
                .moduleName(moduleName)
                .roleId(roleId)
                .roleName(roleName)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminResponse> getBlockedUsers() {
        return userRepository.findByStatusAndRoleName(UserStatus.BLOCKED, "STUDENT")
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Override
    public void blockUser(Long userId) {
        User user = getUser(userId);

        // chỉ block user đang ACTIVE
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("Only ACTIVE user can be blocked");
        }

        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);
    }

    @Override
    public void unblockUser(Long userId) {
        User user = getUser(userId);

        // chỉ unblock user đang BLOCKED
        if (user.getStatus() != UserStatus.BLOCKED) {
            throw new RuntimeException("Only BLOCKED user can be unblocked");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

}