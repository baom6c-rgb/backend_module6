package com.be_ai_learning_platform.service.impl;

import com.be_ai_learning_platform.dto.UserUpdateDTO;
import com.be_ai_learning_platform.dto.request.StudentUpdateProfileRequest;
import com.be_ai_learning_platform.dto.response.StudentProfileResponse;
import com.be_ai_learning_platform.dto.response.UserStatusResponse;
import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.ClassRepository;
import com.be_ai_learning_platform.repository.ModuleRepository;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.AvatarStorageService;
import com.be_ai_learning_platform.service.mail.MailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private ModuleRepository moduleRepository;
    @Mock
    private MailService mailService;
    @Mock
    private AvatarStorageService avatarStorageService;

    @InjectMocks
    private UserServiceImpl userService;

    // ================= getStatusByEmail =================

    @Test
    void getStatusByEmail_userExists() {
        User user = new User();
        user.setEmail("test@gmail.com");
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        UserStatusResponse res = userService.getStatusByEmail("test@gmail.com");

        assertEquals("test@gmail.com", res.getEmail());
        assertEquals(UserStatus.ACTIVE, res.getStatus());
    }

    @Test
    void getStatusByEmail_userNotFound() {
        when(userRepository.findByEmail("x@gmail.com"))
                .thenReturn(Optional.empty());

        UserStatusResponse res = userService.getStatusByEmail("x@gmail.com");

        assertEquals("x@gmail.com", res.getEmail());
        assertNull(res.getStatus());
    }

    // ================= getMyProfileByEmail =================

    @Test
    void getMyProfileByEmail_success() {
        User user = new User();
        user.setEmail("test@gmail.com");
        user.setFullName("Test User");
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        StudentProfileResponse res =
                userService.getMyProfileByEmail("test@gmail.com");

        assertEquals("test@gmail.com", res.getEmail());
        assertEquals("Test User", res.getFullName());
    }

    @Test
    void getMyProfileByEmail_notFound() {
        when(userRepository.findByEmail("x@gmail.com"))
                .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> userService.getMyProfileByEmail("x@gmail.com"));
    }

    // ================= updateStudentProfileByEmail =================

    @Test
    void updateStudentProfileByEmail_active_success() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);

        StudentUpdateProfileRequest req = new StudentUpdateProfileRequest();
        req.setFullName("New Name");

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        userService.updateStudentProfileByEmail("test@gmail.com", req);

        assertEquals("New Name", user.getFullName());
        verify(userRepository).save(user);
    }

    @Test
    void updateStudentProfileByEmail_notActive_throw() {
        User user = new User();
        user.setStatus(UserStatus.BLOCKED);

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class,
                () -> userService.updateStudentProfileByEmail(
                        "test@gmail.com",
                        new StudentUpdateProfileRequest()
                ));

        verify(userRepository, never()).save(any());
    }

    // ================= updateMyAvatarByEmail =================

    @Test
    void updateMyAvatarByEmail_success() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@gmail.com");
        user.setStatus(UserStatus.ACTIVE);

        MultipartFile file = mock(MultipartFile.class);

        when(userRepository.findByEmail("test@gmail.com"))
                .thenReturn(Optional.of(user));
        when(avatarStorageService.storeAvatar(file, 1L))
                .thenReturn("http://avatar.png");

        StudentProfileResponse res =
                userService.updateMyAvatarByEmail("test@gmail.com", file);

        assertEquals("http://avatar.png", res.getAvatarUrl());
        verify(userRepository).save(user);
    }

    // ================= approveUser =================

    @Test
    void approveUser_success() {
        User user = new User();
        user.setStatus(UserStatus.WAITING_APPROVAL);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        userService.approveUser(1L);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        verify(mailService).notifyApprovedSuccess(user);
    }

    @Test
    void approveUser_wrongStatus_throw() {
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class,
                () -> userService.approveUser(1L));

        verify(mailService, never()).notifyApprovedSuccess(any());
    }
}
