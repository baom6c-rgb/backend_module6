package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.AdminAddAdminRequest;
import com.be_ai_learning_platform.dto.request.AdminAddUserRequest;
import com.be_ai_learning_platform.dto.request.AdminUpdateUserRequest;
import com.be_ai_learning_platform.dto.response.AdminResponse;
import com.be_ai_learning_platform.dto.response.AdminUserDetailResponse;
import com.be_ai_learning_platform.entity.*;
import com.be_ai_learning_platform.entity.enums.LoginProvider;
import com.be_ai_learning_platform.entity.enums.RegisterMethod;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private ClassRepository classRepository;
    @Mock private ModuleRepository moduleRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private AdminServiceImpl adminService;

    // ================= ADD USER =================

    @Test
    void addUser_success() {
        AdminAddUserRequest req = new AdminAddUserRequest();
        req.setEmail("student@gmail.com");
        req.setFullName("Student A");
        req.setPassword("123456");
        req.setRoleName("STUDENT");
        req.setClassId(1L);
        req.setModuleId(1L);

        ClassEntity clazz = new ClassEntity();
        clazz.setId(1L);

        LearningModule module = new LearningModule();
        module.setId(1L);

        Role role = new Role();
        role.setName("STUDENT");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(classRepository.findById(1L)).thenReturn(Optional.of(clazz));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(module));
        when(roleRepository.findByName("STUDENT")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        AdminResponse res = adminService.addUser(req);

        assertEquals("student@gmail.com", res.getEmail());
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    void addUser_emailExists_throwException() {
        AdminAddUserRequest req = new AdminAddUserRequest();
        req.setEmail("student@gmail.com");

        when(userRepository.existsByEmail("student@gmail.com")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> adminService.addUser(req));

        assertEquals("Email đã tồn tại!", ex.getMessage());
    }

    // ================= ADD ADMIN =================

    @Test
    void addAdmin_success() {
        AdminAddAdminRequest req = new AdminAddAdminRequest();
        req.setEmail("admin@gmail.com");
        req.setFullName("Admin");
        req.setPassword("123456");

        Role adminRole = new Role();
        adminRole.setName("ADMIN");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        AdminResponse res = adminService.addAdmin(req);

        assertEquals("admin@gmail.com", res.getEmail());
        assertEquals(RegisterMethod.FORM, res.getRegisterMethod());
    }

    // ================= APPROVE / REJECT =================

    @Test
    void approve_success() {
        User user = new User();
        user.setStatus(UserStatus.WAITING_APPROVAL);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminService.approve(1L);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void reject_success() {
        User user = new User();
        user.setStatus(UserStatus.WAITING_APPROVAL);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminService.reject(1L);

        assertEquals(UserStatus.REJECTED, user.getStatus());
    }

    // ================= BLOCK / UNBLOCK =================

    @Test
    void blockUser_success() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminService.blockUser(1L);

        assertEquals(UserStatus.BLOCKED, user.getStatus());
    }

    @Test
    void unblockUser_success() {
        User user = new User();
        user.setStatus(UserStatus.BLOCKED);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminService.unblockUser(1L);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    // ================= UPDATE USER =================

    @Test
    void updateUser_success() {
        AdminUpdateUserRequest req = new AdminUpdateUserRequest();
        req.setEmail("new@gmail.com");
        req.setFullName("New Name");
        req.setClassId(1L);
        req.setModuleId(1L);

        User user = new User();
        user.setId(1L);
        user.setEmail("old@gmail.com");

        ClassEntity clazz = new ClassEntity();
        LearningModule module = new LearningModule();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@gmail.com")).thenReturn(false);
        when(classRepository.findById(1L)).thenReturn(Optional.of(clazz));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(module));

        AdminUserDetailResponse res = adminService.updateUser(1L, req);

        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    // ================= GET LIST =================

    @Test
    void getAllUsers_success() {
        User user = new User();
        user.setEmail("a@gmail.com");

        when(userRepository.findAll()).thenReturn(List.of(user));

        List<AdminResponse> list = adminService.getAllUsers();

        assertEquals(1, list.size());
    }
}
