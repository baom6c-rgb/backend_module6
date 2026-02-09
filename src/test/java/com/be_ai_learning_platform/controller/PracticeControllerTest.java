package com.be_ai_learning_platform.controller;

import com.be_ai_learning_platform.dto.request.*;
import com.be_ai_learning_platform.dto.response.*;
import com.be_ai_learning_platform.service.PracticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test thuần cho PracticeController
 * Không sử dụng Spring context, chỉ test logic của controller
 */
class PracticeControllerTest {

    private PracticeController controller;

    @Mock
    private PracticeService practiceService;

    @Mock
    private Authentication authentication;

    private static final String USERNAME = "test@example.com";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new PracticeController(practiceService);
        when(authentication.getName()).thenReturn(USERNAME);
    }

    // ==================== V1 API Tests ====================

    @Test
    @DisplayName("Generate Preview - Success")
    void testGeneratePreview_Success() {
        // Given
        PracticeGenerateRequest request = new PracticeGenerateRequest();
        GenerateQuestionsResponse expectedResponse = new GenerateQuestionsResponse();

        when(practiceService.generatePreview(USERNAME, request))
                .thenReturn(expectedResponse);

        // When
        GenerateQuestionsResponse result = controller.generatePreview(authentication, request);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(practiceService, times(1)).generatePreview(USERNAME, request);
    }

    @Test
    @DisplayName("Start Practice - Success")
    void testStartPractice_Success() {
        // Given
        PracticeGenerateRequest request = new PracticeGenerateRequest();
        StartPracticeResponse expectedResponse = new StartPracticeResponse();

        when(practiceService.start(USERNAME, request))
                .thenReturn(expectedResponse);

        // When
        StartPracticeResponse result = controller.start(authentication, request);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(practiceService, times(1)).start(USERNAME, request);
    }

    @Test
    @DisplayName("Get Attempt - Success")
    void testGetAttempt_Success() {
        // Given
        Long attemptId = 123L;
        AttemptDetailResponse expectedResponse = new AttemptDetailResponse();

        when(practiceService.getAttempt(USERNAME, attemptId))
                .thenReturn(expectedResponse);

        // When
        AttemptDetailResponse result = controller.getAttempt(authentication, attemptId);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(practiceService, times(1)).getAttempt(USERNAME, attemptId);
    }

    @Test
    @DisplayName("Get Attempt - Not Found")
    void testGetAttempt_NotFound() {
        // Given
        Long attemptId = 999L;

        when(practiceService.getAttempt(USERNAME, attemptId))
                .thenThrow(new RuntimeException("Attempt not found"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            controller.getAttempt(authentication, attemptId);
        });
        verify(practiceService, times(1)).getAttempt(USERNAME, attemptId);
    }

    @Test
    @DisplayName("Submit Practice - Success")
    void testSubmitPractice_Success() {
        // Given
        Long attemptId = 123L;
        SubmitPracticeRequest request = new SubmitPracticeRequest();
        SubmitPracticeResponse expectedResponse = new SubmitPracticeResponse();

        when(practiceService.submit(USERNAME, attemptId, request))
                .thenReturn(expectedResponse);

        // When
        SubmitPracticeResponse result = controller.submit(authentication, attemptId, request);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(practiceService, times(1)).submit(USERNAME, attemptId, request);
    }

    @Test
    @DisplayName("Review Attempt - Success")
    void testReviewAttempt_Success() {
        // Given
        Long attemptId = 123L;
        AttemptReviewResponse expectedResponse = new AttemptReviewResponse();

        when(practiceService.getReview(USERNAME, attemptId))
                .thenReturn(expectedResponse);

        // When
        AttemptReviewResponse result = controller.review(authentication, attemptId);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(practiceService, times(1)).getReview(USERNAME, attemptId);
    }

    // ==================== V2 API Tests ====================

    @Test
    @DisplayName("V2 Generate Session - Success")
    void testGenerateSessionV2_Success() {
        // Given
        GeneratePracticeSessionRequest request = new GeneratePracticeSessionRequest();
        GeneratePracticeSessionResponse expectedResponse = new GeneratePracticeSessionResponse();

        when(practiceService.generateSessionV2(USERNAME, request))
                .thenReturn(expectedResponse);

        // When
        GeneratePracticeSessionResponse result = controller.generateSessionV2(authentication, request);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(practiceService, times(1)).generateSessionV2(USERNAME, request);
    }

    @Test
    @DisplayName("V2 Start Session - Success")
    void testStartSessionV2_Success() {
        // Given
        StartPracticeSessionRequest request = new StartPracticeSessionRequest();
        StartPracticeSessionResponse expectedResponse = new StartPracticeSessionResponse();

        when(practiceService.startSessionV2(USERNAME, request))
                .thenReturn(expectedResponse);

        // When
        StartPracticeSessionResponse result = controller.startSessionV2(authentication, request);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(practiceService, times(1)).startSessionV2(USERNAME, request);
    }

    @Test
    @DisplayName("V2 Get Session - Success (Resume)")
    void testGetSessionV2_Success() {
        // Given
        String sessionToken = "session-token-123";
        StartPracticeSessionResponse expectedResponse = new StartPracticeSessionResponse();

        when(practiceService.getSessionV2(USERNAME, sessionToken))
                .thenReturn(expectedResponse);

        // When
        StartPracticeSessionResponse result = controller.getSessionV2(authentication, sessionToken);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(practiceService, times(1)).getSessionV2(USERNAME, sessionToken);
    }

    @Test
    @DisplayName("V2 Get Session - Expired Token")
    void testGetSessionV2_ExpiredToken() {
        // Given
        String sessionToken = "expired-token";

        when(practiceService.getSessionV2(USERNAME, sessionToken))
                .thenThrow(new RuntimeException("Session expired"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            controller.getSessionV2(authentication, sessionToken);
        });
        verify(practiceService, times(1)).getSessionV2(USERNAME, sessionToken);
    }

    @Test
    @DisplayName("V2 Submit Session - Success")
    void testSubmitSessionV2_Success() {
        // Given
        String sessionToken = "session-token-123";
        SubmitPracticeSessionRequest request = new SubmitPracticeSessionRequest();
        SubmitPracticeV2Response expectedResponse = new SubmitPracticeV2Response();

        when(practiceService.submitSessionV2(USERNAME, sessionToken, request))
                .thenReturn(expectedResponse);

        // When
        SubmitPracticeV2Response result = controller.submitSessionV2(authentication, sessionToken, request);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(practiceService, times(1)).submitSessionV2(USERNAME, sessionToken, request);
    }

    @Test
    @DisplayName("V2 Get Retest Status - Can Retest")
    void testGetRetestStatusV2_CanRetest() {
        // Given
        Long attemptId = 123L;
        RetestStatusResponse expectedResponse = new RetestStatusResponse();

        when(practiceService.getRetestStatusV2(USERNAME, attemptId))
                .thenReturn(expectedResponse);

        // When
        RetestStatusResponse result = controller.getRetestStatusV2(authentication, attemptId);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(practiceService, times(1)).getRetestStatusV2(USERNAME, attemptId);
    }

    @Test
    @DisplayName("V2 Start Retest - Success")
    void testStartRetestV2_Success() {
        // Given
        Long attemptId = 123L;
        StartPracticeSessionResponse expectedResponse = new StartPracticeSessionResponse();

        when(practiceService.startRetestV2(USERNAME, attemptId))
                .thenReturn(expectedResponse);

        // When
        StartPracticeSessionResponse result = controller.startRetestV2(authentication, attemptId);

        // Then
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(practiceService, times(1)).startRetestV2(USERNAME, attemptId);
    }

    @Test
    @DisplayName("V2 Start Retest - Not Available (Cooldown)")
    void testStartRetestV2_NotAvailable() {
        // Given
        Long attemptId = 123L;

        when(practiceService.startRetestV2(USERNAME, attemptId))
                .thenThrow(new RuntimeException("Retest not available yet"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            controller.startRetestV2(authentication, attemptId);
        });
        verify(practiceService, times(1)).startRetestV2(USERNAME, attemptId);
    }

    // ==================== Constructor Tests ====================

    @Test
    @DisplayName("Controller Constructor - Should Initialize Service")
    void testConstructor_ShouldInitializeService() {
        // Given
        PracticeService mockService = mock(PracticeService.class);

        // When
        PracticeController newController = new PracticeController(mockService);

        // Then
        assertNotNull(newController);
    }

    // ==================== Edge Cases Tests ====================

    @Test
    @DisplayName("Generate Preview - Service Throws Exception")
    void testGeneratePreview_ServiceThrowsException() {
        // Given
        PracticeGenerateRequest request = new PracticeGenerateRequest();

        when(practiceService.generatePreview(USERNAME, request))
                .thenThrow(new RuntimeException("Service error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            controller.generatePreview(authentication, request);
        });
    }

    @Test
    @DisplayName("Submit Practice - Service Throws Exception")
    void testSubmitPractice_ServiceThrowsException() {
        // Given
        Long attemptId = 123L;
        SubmitPracticeRequest request = new SubmitPracticeRequest();

        when(practiceService.submit(USERNAME, attemptId, request))
                .thenThrow(new RuntimeException("Submit failed"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            controller.submit(authentication, attemptId, request);
        });
    }

    // ==================== Authentication Tests ====================

    @Test
    @DisplayName("All Endpoints - Should Use Authentication Username")
    void testAllEndpoints_ShouldUseAuthenticationUsername() {
        // V1 endpoint test
        PracticeGenerateRequest generateReq = new PracticeGenerateRequest();
        controller.generatePreview(authentication, generateReq);
        verify(authentication, atLeastOnce()).getName();

        // V2 endpoint test
        GeneratePracticeSessionRequest sessionReq = new GeneratePracticeSessionRequest();
        controller.generateSessionV2(authentication, sessionReq);
        verify(authentication, atLeastOnce()).getName();
    }

    @Test
    @DisplayName("Service Method Calls - Should Pass Correct Username")
    void testServiceMethodCalls_ShouldPassCorrectUsername() {
        // Given
        Long attemptId = 456L;
        AttemptDetailResponse response = new AttemptDetailResponse();

        when(practiceService.getAttempt(USERNAME, attemptId))
                .thenReturn(response);

        // When
        controller.getAttempt(authentication, attemptId);

        // Then
        verify(practiceService).getAttempt(eq(USERNAME), eq(attemptId));
    }
}