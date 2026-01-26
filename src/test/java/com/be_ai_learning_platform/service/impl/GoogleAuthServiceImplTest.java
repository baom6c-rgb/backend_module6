package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.entity.Role;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.UserRole;
import com.be_ai_learning_platform.entity.enums.LoginProvider;
import com.be_ai_learning_platform.entity.enums.RegisterMethod;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.RoleRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.repository.UserRoleRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceImplTest {

    @Mock
    UserRepository userRepository;

    @Mock
    RoleRepository roleRepository;

    @Mock
    UserRoleRepository userRoleRepository;

    @InjectMocks
    GoogleAuthServiceImpl googleAuthService;

    @Test
    void authenticate_tokenNull_throwException() {
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> googleAuthService.authenticate(null)
        );

        assertTrue(ex.getMessage().contains("idToken"));
    }

    @Test
    void authenticate_newUser_success() {
        // ===== Mock Google Token =====
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("test@gmail.com");
        payload.setSubject("google-id-123");
        payload.set("name", "Bao");
        payload.set("picture", "avatar.png");

        GoogleIdToken idToken = mock(GoogleIdToken.class);
        when(idToken.getPayload()).thenReturn(payload);

        try (MockedConstruction<GoogleIdTokenVerifier> mocked =
                     mockConstruction(GoogleIdTokenVerifier.class,
                             (mock, context) -> when(mock.verify(anyString())).thenReturn(idToken))
        ) {

            when(userRepository.findByEmail("test@gmail.com"))
                    .thenReturn(Optional.empty());

            Role studentRole = new Role();
            studentRole.setName("STUDENT");

            when(roleRepository.findByName("STUDENT"))
                    .thenReturn(Optional.of(studentRole));

            // ===== when =====
            User user = googleAuthService.authenticate("valid-token");

            // ===== then =====
            assertEquals("test@gmail.com", user.getEmail());
            assertEquals(RegisterMethod.GOOGLE, user.getRegisterMethod());
            assertEquals(LoginProvider.GOOGLE, user.getLoginProvider());
            assertEquals(UserStatus.CREATED, user.getStatus());
            assertEquals("Bao", user.getFullName());

            verify(userRepository).save(any(User.class));
            verify(userRoleRepository).save(any(UserRole.class));
        }
    }

    @Test
    void authenticate_existingUser_blocked_throwException() {
        // mock token
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("blocked@gmail.com");
        payload.setSubject("google-id");

        GoogleIdToken idToken = mock(GoogleIdToken.class);
        when(idToken.getPayload()).thenReturn(payload);

        try (MockedConstruction<GoogleIdTokenVerifier> mocked =
                     mockConstruction(GoogleIdTokenVerifier.class,
                             (mock, context) -> when(mock.verify(anyString())).thenReturn(idToken))
        ) {

            User user = new User();
            user.setEmail("blocked@gmail.com");
            user.setStatus(UserStatus.BLOCKED);
            user.setLoginProvider(LoginProvider.GOOGLE);
            user.setUserRoles(new ArrayList<>());

            when(userRepository.findByEmail("blocked@gmail.com"))
                    .thenReturn(Optional.of(user));

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> googleAuthService.authenticate("token")
            );

            assertTrue(ex.getMessage().contains("blocked"));
        }
    }
}
