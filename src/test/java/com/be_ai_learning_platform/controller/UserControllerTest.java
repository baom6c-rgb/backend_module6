package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.UserUpdateDTO;
import com.be_ai_learning_platform.dto.request.ChangePasswordRequest;
import com.be_ai_learning_platform.dto.request.StudentUpdateProfileRequest;
import com.be_ai_learning_platform.dto.response.StudentProfileResponse;
import com.be_ai_learning_platform.dto.response.UserStatusResponse;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(UserControllerTest.MockConfig.class)
class UserControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    UserControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    // ========================= UPDATE PROFILE (LEGACY/ADMIN) =========================
    @Test
    void updateProfile_success() throws Exception {
        // given
        Long userId = 1L;
        String requestJson = """
            {
                "fullName": "Updated Name",
                "email": "updated@test.com"
            }
            """;

        User mockUser = new User();
        when(MockConfig.userService.updateProfile(eq(userId), any(UserUpdateDTO.class)))
                .thenReturn(mockUser);

        // when & then
        mockMvc.perform(put("/api/users/profile/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(MockConfig.userService).updateProfile(eq(userId), any(UserUpdateDTO.class));
    }

    // ========================= GET STATUS =========================
    @Test
    void getStatus_userExists_returnStatus() throws Exception {
        // given
        String email = "test@test.com";

        when(MockConfig.userService.getStatusByEmail(email))
                .thenReturn(any(UserStatusResponse.class));

        // when & then
        mockMvc.perform(get("/api/users/status")
                        .param("email", email))
                .andExpect(status().isOk());

        verify(MockConfig.userService).getStatusByEmail(email);
    }

    @Test
    void getStatus_userNotFound_return404() throws Exception {
        // given
        String email = "notfound@test.com";

        when(MockConfig.userService.getStatusByEmail(email))
                .thenThrow(new RuntimeException("User not found"));

        // when & then
        mockMvc.perform(get("/api/users/status")
                        .param("email", email))
                .andExpect(status().isNotFound())
                .andExpect(content().string("User not found"));

        verify(MockConfig.userService).getStatusByEmail(email);
    }

    // ========================= GET MY PROFILE =========================
    @Test
    @WithMockUser(username = "student@test.com")
    void getMyProfile_success() throws Exception {
        // given
        String email = "student@test.com";

        when(MockConfig.userService.getMyProfileByEmail(email))
                .thenReturn(any(StudentProfileResponse.class));

        // when & then
        mockMvc.perform(get("/api/users/me/profile"))
                .andExpect(status().isOk());

        verify(MockConfig.userService).getMyProfileByEmail(email);
    }

    // ========================= UPDATE MY PROFILE =========================
    @Test
    @WithMockUser(username = "student@test.com")
    void updateMyProfile_success() throws Exception {
        // given
        String email = "student@test.com";
        String requestJson = """
            {
                "fullName": "Updated Student",
                "phone": "0123456789"
            }
            """;

        doNothing().when(MockConfig.userService)
                .updateStudentProfileByEmail(eq(email), any(StudentUpdateProfileRequest.class));

        // when & then
        mockMvc.perform(put("/api/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(MockConfig.userService).updateStudentProfileByEmail(eq(email), any(StudentUpdateProfileRequest.class));
    }

    // ========================= UPLOAD AVATAR =========================
    @Test
    @WithMockUser(username = "student@test.com")
    void uploadMyAvatar_success() throws Exception {
        // given
        String email = "student@test.com";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        when(MockConfig.userService.updateMyAvatarByEmail(eq(email), any()))
                .thenReturn(any(StudentProfileResponse.class));

        // when & then
        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(file))
                .andExpect(status().isOk());

        verify(MockConfig.userService).updateMyAvatarByEmail(eq(email), any());
    }

    // ========================= CHANGE PASSWORD =========================
    @Test
    @WithMockUser(username = "student@test.com")
    void changeMyPassword_success() throws Exception {
        // given
        String email = "student@test.com";
        String requestJson = """
            {
                "oldPassword": "oldPass123",
                "newPassword": "newPass456"
            }
            """;

        doNothing().when(MockConfig.userService)
                .changeMyPasswordByEmail(eq(email), any(ChangePasswordRequest.class));

        // when & then
        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().string("Đổi mật khẩu thành công"));

        verify(MockConfig.userService).changeMyPasswordByEmail(eq(email), any(ChangePasswordRequest.class));
    }

    @Test
    @WithMockUser(username = "student@test.com")
    void changeMyPassword_wrongOldPassword_returnBadRequest() throws Exception {
        // given
        String email = "student@test.com";
        String requestJson = """
            {
                "oldPassword": "wrongPass",
                "newPassword": "newPass456"
            }
            """;

        doThrow(new RuntimeException("Mật khẩu cũ không đúng"))
                .when(MockConfig.userService)
                .changeMyPasswordByEmail(eq(email), any(ChangePasswordRequest.class));

        // when & then
        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Mật khẩu cũ không đúng"));

        verify(MockConfig.userService).changeMyPasswordByEmail(eq(email), any(ChangePasswordRequest.class));
    }

    @Test
    @WithMockUser(username = "student@test.com")
    void changeMyPassword_genericError_returnBadRequest() throws Exception {
        // given
        String email = "student@test.com";
        String requestJson = """
            {
                "oldPassword": "oldPass123",
                "newPassword": "newPass456"
            }
            """;

        doThrow(new Exception())
                .when(MockConfig.userService)
                .changeMyPasswordByEmail(eq(email), any(ChangePasswordRequest.class));

        // when & then
        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Đổi mật khẩu thất bại"));

        verify(MockConfig.userService).changeMyPasswordByEmail(eq(email), any(ChangePasswordRequest.class));
    }

    // ========================= ME (TEST ENDPOINT) =========================
    @Test
    @WithMockUser(username = "test@test.com")
    void me_returnAuthenticatedEmail() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(content().string("test@test.com"));
    }

    @Test
    void me_unauthenticated_return401() throws Exception {
        // when & then
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ========================= MOCK CONFIG =========================
    static class MockConfig {

        static final UserService userService = Mockito.mock(UserService.class);

        @Bean
        UserService userService() {
            return userService;
        }
    }
}