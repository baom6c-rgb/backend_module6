package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.LoginRequest;
import com.be_ai_learning_platform.dto.request.RegisterRequest;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.UserRoleRepository;
import com.be_ai_learning_platform.security.JwtUtil;
import com.be_ai_learning_platform.service.AuthService;
import com.be_ai_learning_platform.service.GoogleAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(AuthControllerTest.MockConfig.class)
class AuthControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    AuthControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    // ========================= REGISTER =========================
    @Test
    void register_success() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test@gmail.com");
        req.setFullName("Test User");
        req.setPassword("123456");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().string("Register success. Waiting for approval"));
    }

    // ========================= LOGIN ACTIVE =========================
    @Test
    void login_activeUser_returnToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@gmail.com");
        req.setPassword("123456");

        User user = new User();
        user.setId(1L);
        user.setEmail("test@gmail.com");
        user.setStatus(UserStatus.ACTIVE);

        Mockito.when(MockConfig.authService.login(any()))
                .thenReturn(user);
        Mockito.when(MockConfig.userRoleRepository.findRoleNamesByUserId(1L))
                .thenReturn(List.of("STUDENT"));
        Mockito.when(MockConfig.jwtUtil.generateToken(anyString(), any()))
                .thenReturn("jwt-token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ========================= LOGIN WAITING_APPROVAL =========================
    @Test
    void login_waitingApproval_returnToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("wait@gmail.com");
        req.setPassword("123456");

        User user = new User();
        user.setId(2L);
        user.setEmail("wait@gmail.com");
        user.setStatus(UserStatus.WAITING_APPROVAL);

        Mockito.when(MockConfig.authService.login(any()))
                .thenReturn(user);
        Mockito.when(MockConfig.userRoleRepository.findRoleNamesByUserId(2L))
                .thenReturn(List.of("STUDENT"));
        Mockito.when(MockConfig.jwtUtil.generateToken(anyString(), any()))
                .thenReturn("waiting-token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("waiting-token"))
                .andExpect(jsonPath("$.status").value("WAITING_APPROVAL"));
    }

    // ========================= LOGOUT =========================
    @Test
    void logout_success() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("Đã đăng xuất thành công"));
    }

    // ========================= GOOGLE LOGIN =========================
    @Test
    void loginWithGoogle_success() throws Exception {
        User user = new User();
        user.setId(3L);
        user.setEmail("google@gmail.com");
        user.setStatus(UserStatus.ACTIVE);

        Mockito.when(MockConfig.googleAuthService.authenticate(any()))
                .thenReturn(user);
        Mockito.when(MockConfig.userRoleRepository.findRoleNamesByUserId(3L))
                .thenReturn(List.of("STUDENT"));
        Mockito.when(MockConfig.jwtUtil.generateToken(anyString(), any()))
                .thenReturn("google-token");

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "idToken": "fake-google-token" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("google-token"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ========================= MOCK CONFIG =========================
    static class MockConfig {

        static final AuthService authService = Mockito.mock(AuthService.class);
        static final GoogleAuthService googleAuthService = Mockito.mock(GoogleAuthService.class);
        static final JwtUtil jwtUtil = Mockito.mock(JwtUtil.class);
        static final UserRoleRepository userRoleRepository = Mockito.mock(UserRoleRepository.class);

        @Bean
        AuthService authService() {
            return authService;
        }

        @Bean
        GoogleAuthService googleAuthService() {
            return googleAuthService;
        }

        @Bean
        JwtUtil jwtUtil() {
            return jwtUtil;
        }

        @Bean
        UserRoleRepository userRoleRepository() {
            return userRoleRepository;
        }
    }
}
