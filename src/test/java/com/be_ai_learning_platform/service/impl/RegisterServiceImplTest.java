package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.request.RegisterRequest;
import com.be_ai_learning_platform.entity.Role;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.LoginProvider;
import com.be_ai_learning_platform.entity.enums.RegisterMethod;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.RoleRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.repository.UserRoleRepository;
import com.be_ai_learning_platform.service.mail.MailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MailService mailService;

    @InjectMocks
    private RegisterServiceImpl registerService;

    @Test
    void registerByEmail_success() {
        // given
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test@gmail.com");
        req.setFullName("Test User");
        req.setPassword("123456");

        Role role = new Role();
        role.setName("STUDENT");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("hashed_pw");
        when(roleRepository.findByName("STUDENT")).thenReturn(Optional.of(role));

        // when
        User user = registerService.registerByEmail(req);

        // then
        assertNotNull(user);
        assertEquals("test@gmail.com", user.getEmail());
        assertEquals(RegisterMethod.FORM, user.getRegisterMethod());
        assertEquals(LoginProvider.FORM, user.getLoginProvider());
        assertEquals(UserStatus.WAITING_APPROVAL, user.getStatus());
        assertNotNull(user.getApproveToken());
        assertEquals("hashed_pw", user.getPasswordHash());

        verify(userRepository).save(any(User.class));
        verify(userRoleRepository).save(any());
        verify(mailService).notifyWaitingApproval(any(User.class));
    }

    @Test
    void registerByEmail_emailExists_throwException() {
        // given
        RegisterRequest req = new RegisterRequest();
        req.setEmail("exist@gmail.com");

        when(userRepository.existsByEmail(req.getEmail())).thenReturn(true);

        // when + then
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> registerService.registerByEmail(req)
        );

        assertEquals("Email đã tồn tại", ex.getMessage());

        verify(userRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
        verify(mailService, never()).notifyWaitingApproval(any());
    }
}
