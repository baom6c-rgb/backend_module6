package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.CompleteProfileRequest;
import com.be_ai_learning_platform.dto.request.LoginRequest;
import com.be_ai_learning_platform.dto.request.RegisterRequest;
import com.be_ai_learning_platform.entity.*;
import com.be_ai_learning_platform.entity.enums.LoginProvider;
import com.be_ai_learning_platform.entity.enums.RegisterMethod;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.*;
import com.be_ai_learning_platform.service.mail.MailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private ModuleRepository moduleRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MailService mailService;

    @InjectMocks
    private AuthServiceImpl authService;

    // ================= REGISTER =================

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@gmail.com");
        request.setPassword("123456");
        request.setFullName("Test User");
        request.setClassId(1L);
        request.setModuleId(1L);

        ClassEntity clazz = new ClassEntity();
        LearningModule module = new LearningModule();
        Role role = new Role();
        role.setName("STUDENT");

        when(userRepository.existsByEmail("test@gmail.com")).thenReturn(false);
        when(classRepository.findById(1L)).thenReturn(Optional.of(clazz));
        when(moduleRepository.findById(1L)).thenReturn(Optional.of(module));
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        when(roleRepository.findByName("STUDENT")).thenReturn(Optional.of(role));

        authService.register(request);

        verify(userRepository).save(any(User.class));
        verify(userRoleRepository).save(any(UserRole.class));
        verify(mailService).notifyWaitingApproval(any(User.class));
    }

    @Test
    void register_emailExists_throwException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@gmail.com");

        when(userRepository.existsByEmail("test@gmail.com")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.register(request));

        assertEquals("Email already exists", ex.getMessage());
    }

    // ================= LOGIN =================

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@gmail.com");
        request.setPassword("123456");

        User user = new User();
        user.setEmail("test@gmail.com");
        user.setPasswordHash("encoded");
        user.setLoginProvider(LoginProvider.FORM);
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "encoded"))
                .thenReturn(true);

        User result = authService.login(request);

        assertNotNull(result);
        verify(userRepository).save(user);
    }

    @Test
    void login_wrongPassword_throwException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@gmail.com");
        request.setPassword("wrong");

        User user = new User();
        user.setLoginProvider(LoginProvider.FORM);
        user.setPasswordHash("encoded");

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded"))
                .thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> authService.login(request));
    }

    // ================= COMPLETE PROFILE =================

    @Test
    void completeProfile_success() {
        CompleteProfileRequest request = new CompleteProfileRequest();
        request.setEmail("test@gmail.com");
        request.setClassId(1L);
        request.setModuleId(1L);

        User user = new User();
        user.setEmail("test@gmail.com");
        user.setStatus(UserStatus.CREATED);

        ClassEntity clazz = new ClassEntity();
        LearningModule module = new LearningModule();

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));
        when(classRepository.findById(1L))
                .thenReturn(Optional.of(clazz));
        when(moduleRepository.findById(1L))
                .thenReturn(Optional.of(module));

        User result = authService.completeProfile(request);

        assertEquals(UserStatus.WAITING_APPROVAL, result.getStatus());
        verify(mailService).notifyWaitingApproval(user);
    }

    // ================= FORGOT PASSWORD =================

    @Test
    void processForgotPassword_success() {
        User user = new User();
        user.setEmail("test@gmail.com");

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        authService.processForgotPassword("test@gmail.com");

        verify(userRepository).save(user);
        verify(mailService)
                .sendForgotPasswordMail(eq("test@gmail.com"), contains("reset-password"));
    }

    // ================= UPDATE PASSWORD =================

    @Test
    void updatePassword_success() {
        User user = new User();
        user.setResetPasswordToken("token");
        user.setTokenExpiryDate(LocalDateTime.now().plusMinutes(5));

        when(userRepository.findByResetPasswordToken("token"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass"))
                .thenReturn("encoded");

        authService.updatePassword("token", "newpass");

        assertNull(user.getResetPasswordToken());
        verify(userRepository).save(user);
    }
}
