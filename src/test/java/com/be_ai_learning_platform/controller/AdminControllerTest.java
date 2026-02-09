package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.AdminAddAdminRequest;
import com.be_ai_learning_platform.dto.request.AdminAddUserRequest;
import com.be_ai_learning_platform.dto.request.AdminUpdateUserRequest;
import com.be_ai_learning_platform.dto.response.AdminResponse;
import com.be_ai_learning_platform.dto.response.AdminUserDetailResponse;
import com.be_ai_learning_platform.dto.response.OptionResponse;
import com.be_ai_learning_platform.service.AdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(AdminControllerTest.MockConfig.class)
class AdminControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    AdminControllerTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    // ========================= ADD ADMIN =========================
    @Test
    void addAdmin_success() throws Exception {
        // given
        String requestJson = """
            {
                "email": "admin@test.com",
                "fullName": "Admin User",
                "password": "password123"
            }
            """;

        when(MockConfig.adminService.addAdmin(any(AdminAddAdminRequest.class)))
                .thenReturn(any(AdminResponse.class));

        // when & then
        mockMvc.perform(post("/api/admin/users/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());

        verify(MockConfig.adminService).addAdmin(any(AdminAddAdminRequest.class));
    }

    // ========================= ADD USER (STUDENT) =========================
    @Test
    void addUser_success() throws Exception {
        // given
        String requestJson = """
            {
                "email": "student@test.com",
                "fullName": "Student User",
                "password": "password123",
                "classId": 1,
                "moduleId": 2
            }
            """;

        when(MockConfig.adminService.addUser(any(AdminAddUserRequest.class)))
                .thenReturn(any(AdminResponse.class));

        // when & then
        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());

        verify(MockConfig.adminService).addUser(any(AdminAddUserRequest.class));
    }

    // ========================= GET ALL USERS =========================
    @Test
    void getAllUsers_success() throws Exception {
        // given
        when(MockConfig.adminService.getAllUsers())
                .thenReturn(Arrays.asList(
                        any(AdminResponse.class),
                        any(AdminResponse.class)
                ));

        // when & then
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).getAllUsers();
    }

    @Test
    void getAllUsers_emptyList_success() throws Exception {
        // given
        when(MockConfig.adminService.getAllUsers())
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(MockConfig.adminService).getAllUsers();
    }

    // ========================= PENDING APPROVALS =========================
    @Test
    void pendingApprovals_success() throws Exception {
        // given
        when(MockConfig.adminService.getPendingApprovals())
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/admin/approvals/pending"))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).getPendingApprovals();
    }

    // ========================= APPROVE =========================
    @Test
    void approve_success() throws Exception {
        // given
        Long userId = 5L;
        doNothing().when(MockConfig.adminService).approve(userId);

        // when & then
        mockMvc.perform(post("/api/admin/approvals/{id}/approve", userId))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).approve(userId);
    }

    // ========================= REJECT =========================
    @Test
    void reject_success() throws Exception {
        // given
        Long userId = 6L;
        doNothing().when(MockConfig.adminService).reject(userId);

        // when & then
        mockMvc.perform(post("/api/admin/approvals/{id}/reject", userId))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).reject(userId);
    }

    // ========================= ACTIVE STUDENTS =========================
    @Test
    void activeStudents_success() throws Exception {
        // given
        when(MockConfig.adminService.getActiveStudents())
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/admin/students/active"))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).getActiveStudents();
    }

    // ========================= BLOCK USER =========================
    @Test
    void block_success() throws Exception {
        // given
        Long userId = 9L;
        doNothing().when(MockConfig.adminService).blockUser(userId);

        // when & then
        mockMvc.perform(post("/api/admin/students/{id}/block", userId))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).blockUser(userId);
    }

    // ========================= UNBLOCK USER =========================
    @Test
    void unblock_success() throws Exception {
        // given
        Long userId = 10L;
        doNothing().when(MockConfig.adminService).unblockUser(userId);

        // when & then
        mockMvc.perform(post("/api/admin/students/{id}/unblock", userId))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).unblockUser(userId);
    }

    // ========================= BLOCKED STUDENTS =========================
    @Test
    void blockedStudents_success() throws Exception {
        // given
        when(MockConfig.adminService.getBlockedUsers())
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/admin/students/blocked"))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).getBlockedUsers();
    }

    // ========================= GET USER DETAIL =========================
    @Test
    void getUserDetail_success() throws Exception {
        // given
        Long userId = 12L;
        when(MockConfig.adminService.getUserDetail(userId))
                .thenReturn(any(AdminUserDetailResponse.class));

        // when & then
        mockMvc.perform(get("/api/admin/users/{id}", userId))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).getUserDetail(userId);
    }

    // ========================= UPDATE USER =========================
    @Test
    void updateUser_success() throws Exception {
        // given
        Long userId = 13L;
        String requestJson = """
            {
                "email": "updated@test.com",
                "fullName": "Updated User",
                "roleIds": [1, 2],
                "classIds": [1],
                "moduleIds": [2]
            }
            """;

        when(MockConfig.adminService.updateUser(eq(userId), any(AdminUpdateUserRequest.class)))
                .thenReturn(any(AdminUserDetailResponse.class));

        // when & then
        mockMvc.perform(put("/api/admin/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).updateUser(eq(userId), any(AdminUpdateUserRequest.class));
    }

    @Test
    void updateUser_withEmptyLists_success() throws Exception {
        // given
        Long userId = 14L;
        String requestJson = """
            {
                "email": "minimal@test.com",
                "fullName": "Minimal User",
                "roleIds": [],
                "classIds": [],
                "moduleIds": []
            }
            """;

        when(MockConfig.adminService.updateUser(eq(userId), any(AdminUpdateUserRequest.class)))
                .thenReturn(any(AdminUserDetailResponse.class));

        // when & then
        mockMvc.perform(put("/api/admin/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).updateUser(eq(userId), any(AdminUpdateUserRequest.class));
    }

    // ========================= ROLE OPTIONS =========================
    @Test
    void roleOptions_success() throws Exception {
        // given
        when(MockConfig.adminService.getRoleOptions())
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/admin/options/roles"))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).getRoleOptions();
    }

    // ========================= CLASS OPTIONS =========================
    @Test
    void classOptions_success() throws Exception {
        // given
        when(MockConfig.adminService.getClassOptions())
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/admin/options/classes"))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).getClassOptions();
    }

    // ========================= MODULE OPTIONS =========================
    @Test
    void moduleOptions_success() throws Exception {
        // given
        when(MockConfig.adminService.getModuleOptions())
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/admin/options/modules"))
                .andExpect(status().isOk());

        verify(MockConfig.adminService).getModuleOptions();
    }

    @Test
    void moduleOptions_emptyList_success() throws Exception {
        // given
        when(MockConfig.adminService.getModuleOptions())
                .thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/admin/options/modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(MockConfig.adminService).getModuleOptions();
    }

    // ========================= MOCK CONFIG =========================
    static class MockConfig {

        static final AdminService adminService = Mockito.mock(AdminService.class);

        @Bean
        AdminService adminService() {
            return adminService;
        }
    }
}