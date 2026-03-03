package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.GenerateAssignedExamSessionRequest;
import com.be_ai_learning_platform.dto.request.StartPracticeSessionRequest;
import com.be_ai_learning_platform.dto.request.SubmitPracticeSessionRequest;
import com.be_ai_learning_platform.dto.response.GeneratePracticeSessionResponse;
import com.be_ai_learning_platform.dto.response.StartPracticeSessionResponse;
import com.be_ai_learning_platform.dto.response.SubmitPracticeV2Response;

public interface AssignedExamV2Service {

    GeneratePracticeSessionResponse generateAssignedSessionV2(String email, GenerateAssignedExamSessionRequest req);

    StartPracticeSessionResponse startAssignedSessionV2(String email, StartPracticeSessionRequest req);

    StartPracticeSessionResponse getAssignedSessionV2(String email, String sessionToken);

    SubmitPracticeV2Response submitAssignedSessionV2(String email, String sessionToken, SubmitPracticeSessionRequest req);
}
