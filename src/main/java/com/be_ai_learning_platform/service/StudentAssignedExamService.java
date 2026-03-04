package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.SubmitPracticeRequest;
import com.be_ai_learning_platform.dto.response.AttemptReviewResponse;
import com.be_ai_learning_platform.dto.response.StudentAssignedExamItemResponse;
import com.be_ai_learning_platform.dto.response.StudentStartAssignedExamResponse;
import com.be_ai_learning_platform.dto.response.SubmitPracticeResponse;

import java.util.List;

public interface StudentAssignedExamService {
    List<StudentAssignedExamItemResponse> list(String studentEmail);
    StudentStartAssignedExamResponse start(String studentEmail, Long assignmentId);
    SubmitPracticeResponse submit(String studentEmail, Long assignmentId, SubmitPracticeRequest req);
    AttemptReviewResponse getReview(String studentEmail, Long assignmentId);
    String getStudyGuide(String studentEmail, Long assignmentId);
}