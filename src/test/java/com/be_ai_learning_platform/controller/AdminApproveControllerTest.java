package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.entity.User;
import com.be_ai_learning_platform.entity.enums.UserStatus;
import com.be_ai_learning_platform.repository.UserRepository;
import com.be_ai_learning_platform.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminApproveControllerTest {

    @Mock
    UserRepository userRepository;

    @Mock
    UserService userService;

    @InjectMocks
    AdminApproveController controller;

    @BeforeEach
    void setup() {
        // set giá trị cho @Value("${app.frontend-url}")
        ReflectionTestUtils.setField(
                controller,
                "frontendUrl",
                "http://frontend.test"
        );
    }

    @Test
    void approveUser_success() throws Exception {
        // given
        User user = new User();
        user.setId(1L);
        user.setStatus(UserStatus.WAITING_APPROVAL);

        when(userRepository.findByApproveToken("token123"))
                .thenReturn(Optional.of(user));

        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        controller.approveUser("token123", response);

        // then
        verify(userService).approveUser(1L);
        assertEquals(
                "http://frontend.test/approval-result?status=success",
                response.getRedirectedUrl()
        );
    }

    @Test
    void approveUser_alreadyApproved_redirectAlreadyApproved() throws Exception {
        // given
        User user = new User();
        user.setId(2L);
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByApproveToken("token456"))
                .thenReturn(Optional.of(user));

        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        controller.approveUser("token456", response);

        // then
        verify(userService, never()).approveUser(any());
        assertEquals(
                "http://frontend.test/approval-result?status=already-approved",
                response.getRedirectedUrl()
        );
    }

    @Test
    void approveUser_invalidToken_throwException() {
        // given
        when(userRepository.findByApproveToken("invalid"))
                .thenReturn(Optional.empty());

        MockHttpServletResponse response = new MockHttpServletResponse();

        // when + then
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> controller.approveUser("invalid", response)
        );

        assertEquals("Invalid token", ex.getMessage());
        verify(userService, never()).approveUser(any());
    }
}
