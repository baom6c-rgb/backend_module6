package com.be_ai_learning_platform.service;

import com.be_ai_learning_platform.dto.request.AdminCreateAssignedExamRequest;
import com.be_ai_learning_platform.dto.request.AdminExamPreviewRequest;
import com.be_ai_learning_platform.dto.request.AdminUpdateAssignedExamRequest;
import com.be_ai_learning_platform.dto.response.AdminAssignmentReviewResponse; // ✅ NEW
import com.be_ai_learning_platform.dto.response.AdminExamDetailResponse;
import com.be_ai_learning_platform.dto.response.AdminExamPreviewResponse;

import java.util.List;
import java.util.Map;

public interface AdminAssignedExamService {

    AdminExamPreviewResponse preview(String adminEmail, AdminExamPreviewRequest req);

    Map<String, Object> createAndAssign(String adminEmail, AdminCreateAssignedExamRequest req);

    List<Map<String, Object>> listMyExams(String adminEmail);

    AdminExamDetailResponse getDetail(String adminEmail, Long examId);

    List<Map<String, Object>> getCheatingEvents(String adminEmail, Long examId);

    AdminAssignmentReviewResponse reviewAssignment(String adminEmail, Long examId, Long assignmentId);

    Map<String, Object> update(String adminEmail, Long examId, AdminUpdateAssignedExamRequest req);

    Map<String, Object> hardDelete(String adminEmail, Long examId);
}