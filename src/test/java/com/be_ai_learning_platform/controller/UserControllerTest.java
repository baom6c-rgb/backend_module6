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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(UserControllerTest.MockConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

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
        when(userService.updateProfile(eq(userId), any(UserUpdateDTO.class)))
                .thenReturn(mockUser);

        // when & then
        mockMvc.perform(put("/api/users/profile/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(userService).updateProfile(eq(userId), any(UserUpdateDTO.class));
    }

    // ========================= GET STATUS =========================
    @Test
    void getStatus_userExists_returnStatus() throws Exception {
        // given
        String email = "test@test.com";

        when(userService.getStatusByEmail(email))
                .thenReturn(any(UserStatusResponse.class));

        // when & then
        mockMvc.perform(get("/api/users/status")
                        .param("email", email))
                .andExpect(status().isOk());

        verify(userService).getStatusByEmail(email);
    }

    @Test
    void getStatus_userNotFound_return404() throws Exception {
        // given
        String email = "notfound@test.com";

        when(userService.getStatusByEmail(email))
                .thenThrow(new RuntimeException("User not found"));

        // when & then
        mockMvc.perform(get("/api/users/status")
                        .param("email", email))
                .andExpect(status().isNotFound())
                .andExpect(content().string("User not found"));

        verify(userService).getStatusByEmail(email);
    }

    // ========================= GET MY PROFILE =========================
    @Test
    @WithMockUser(username = "student@test.com")
    void getMyProfile_success() throws Exception {
        // given
        String email = "student@test.com";

        when(userService.getMyProfileByEmail(email))
                .thenReturn(any(StudentProfileResponse.class));

        // when & then
        mockMvc.perform(get("/api/users/me/profile"))
                .andExpect(status().isOk());

        verify(userService).getMyProfileByEmail(email);
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

        doNothing().when(userService)
                .updateStudentProfileByEmail(eq(email), any(StudentUpdateProfileRequest.class));

        // when & then
        mockMvc.perform(put("/api/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(userService).updateStudentProfileByEmail(eq(email), any(StudentUpdateProfileRequest.class));
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

        when(userService.updateMyAvatarByEmail(eq(email), any()))
                .thenReturn(any(StudentProfileResponse.class));

        // when & then
        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(file))
                .andExpect(status().isOk());

        verify(userService).updateMyAvatarByEmail(eq(email), any());
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

        doNothing().when(userService)
                .changeMyPasswordByEmail(eq(email), any(ChangePasswordRequest.class));

        // when & then
        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().string("Đổi mật khẩu thành công"));

        verify(userService).changeMyPasswordByEmail(eq(email), any(ChangePasswordRequest.class));
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
                .when(userService)
                .changeMyPasswordByEmail(eq(email), any(ChangePasswordRequest.class));

        // when & then
        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Mật khẩu cũ không đúng"));

        verify(userService).changeMyPasswordByEmail(eq(email), any(ChangePasswordRequest.class));
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
                .when(userService)
                .changeMyPasswordByEmail(eq(email), any(ChangePasswordRequest.class));

        // when & then
        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Đổi mật khẩu thất bại"));

        verify(userService).changeMyPasswordByEmail(eq(email), any(ChangePasswordRequest.class));
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

    @TestConfiguration
    static class MockConfig {
        @Bean
        UserService userService() {
            return Mockito.mock(UserService.class);
        }
    }

}
